package com.harish.videoplayer.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A tiny web server on the phone that hands the ORIGINAL video file to the TV.
 *
 * How casting works in practice: we don't push the video to the TV — we give the TV a link like
 *   http://192.168.1.23:41234/v/3f9c…/Trip.mkv
 * and the TV downloads it from the phone while it plays, exactly like a video on a website.
 * Nothing is re-encoded, so a 4K/8K HDR file reaches the TV bit-for-bit; the TV's own chip
 * decodes it.
 *
 * - "Range" requests are supported, so the TV can seek and can read the end of the file first
 *   (MP4/MKV keep their index there) — without this, seeking and 4K playback don't work.
 * - Only videos the app has shared are served, each under a random secret path, so other
 *   devices on the Wi-Fi can't browse the phone.
 */
@Singleton
class CastServer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private class Shared(val uri: Uri, val mimeType: String, val fileName: String)

    private val shared = ConcurrentHashMap<String, Shared>()
    @Volatile private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null

    val isRunning: Boolean get() = serverSocket?.isClosed == false

    /** Makes [uri] downloadable by the TV and returns its link (starts the server if needed). */
    @Synchronized
    fun share(uri: Uri, mimeType: String, fileName: String): String {
        val ip = wifiAddress() ?: throw CastException("Connect the phone to the same Wi-Fi as the TV")
        val port = ensureStarted()
        val key = UUID.randomUUID().toString().replace("-", "")
        shared[key] = Shared(uri, mimeType, fileName)
        // Ending the link with the real file name helps some TVs recognise the format
        return "http://${ip.hostAddress}:$port/v/$key/${Uri.encode(fileName)}"
    }

    /** Stops serving everything (casting ended). */
    @Synchronized
    fun stop() {
        shared.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        executor?.shutdownNow()
        executor = null
    }

    private fun ensureStarted(): Int {
        serverSocket?.takeIf { !it.isClosed }?.let { return it.localPort }
        val socket = ServerSocket(0) // any free port
        val pool = Executors.newCachedThreadPool()
        serverSocket = socket
        executor = pool
        pool.execute {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (e: IOException) { break }
                pool.execute { serve(client) }
            }
        }
        Log.i(TAG, "Video server on port ${socket.localPort}")
        return socket.localPort
    }

    private fun serve(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = 30_000
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream(), BUFFER)
                val requestLine = readLine(input) ?: return
                val headers = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                }
                val parts = requestLine.split(' ')
                val method = parts.getOrNull(0).orEmpty()
                val path = parts.getOrNull(1).orEmpty()
                val key = path.removePrefix("/v/").substringBefore('/')
                val item = shared[key]
                if (item == null || (method != "GET" && method != "HEAD")) {
                    writeHead(output, "404 Not Found", listOf("Content-Length" to "0"))
                    output.flush()
                    return
                }
                sendFile(item, method == "HEAD", headers["range"], output)
            } catch (e: SocketException) {
                // The TV closed the connection (it often does when seeking) — perfectly normal
            } catch (e: Exception) {
                Log.w(TAG, "Request failed", e)
            }
        }
    }

    private fun sendFile(item: Shared, headOnly: Boolean, range: String?, out: OutputStream) {
        val pfd = context.contentResolver.openFileDescriptor(item.uri, "r")
            ?: run { writeHead(out, "404 Not Found", listOf("Content-Length" to "0")); out.flush(); return }
        pfd.use {
            val size = it.statSize
            // "bytes=1000-" or "bytes=1000-1999"
            val (start, end) = parseRange(range, size)
            val partial = range != null && start >= 0
            if (partial && start >= size) {
                writeHead(out, "416 Range Not Satisfiable", listOf("Content-Range" to "bytes */$size", "Content-Length" to "0"))
                out.flush()
                return
            }
            val from = if (partial) start else 0L
            val to = if (partial) end else size - 1
            val length = to - from + 1
            val headers = mutableListOf(
                "Content-Type" to item.mimeType,
                "Content-Length" to length.toString(),
                "Accept-Ranges" to "bytes",
                // DLNA TVs (Samsung, LG, Sony) look for these before they allow seeking
                "transferMode.dlna.org" to "Streaming",
                "contentFeatures.dlna.org" to DLNA_FEATURES,
                // Chromecast's player fetches with CORS
                "Access-Control-Allow-Origin" to "*",
                "Connection" to "close"
            )
            if (partial) headers += "Content-Range" to "bytes $from-$to/$size"
            writeHead(out, if (partial) "206 Partial Content" else "200 OK", headers)
            if (!headOnly) {
                FileInputStream(it.fileDescriptor).use { file ->
                    file.channel.position(from)
                    copy(file, out, length)
                }
            }
            out.flush()
        }
    }

    private fun copy(input: InputStream, out: OutputStream, length: Long) {
        val buffer = ByteArray(BUFFER)
        var left = length
        while (left > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read < 0) break
            out.write(buffer, 0, read)
            left -= read
        }
    }

    private fun writeHead(out: OutputStream, status: String, headers: List<Pair<String, String>>) {
        val text = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            headers.forEach { (k, v) -> append(k).append(": ").append(v).append("\r\n") }
            append("\r\n")
        }
        out.write(text.toByteArray(Charsets.ISO_8859_1))
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 8192) return sb.toString()
        }
    }

    /** The phone's address on the Wi-Fi (or Ethernet), e.g. 192.168.1.23. */
    private fun wifiAddress(): InetAddress? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val networks = cm.allNetworks.filter { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        }
        return networks.asSequence()
            .mapNotNull { cm.getLinkProperties(it) }
            .flatMap { it.linkAddresses.asSequence() }
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
    }

    companion object {
        private const val TAG = "CastServer"
        private const val BUFFER = 256 * 1024 // big chunks: 4K/8K files need 50-100+ Mbit/s
        const val DLNA_FEATURES = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

        /** "bytes=100-199" -> (100, 199); "bytes=100-" -> (100, size-1); no/odd header -> (-1, -1). */
        internal fun parseRange(header: String?, size: Long): Pair<Long, Long> {
            if (header == null || !header.startsWith("bytes=")) return -1L to -1L
            val spec = header.removePrefix("bytes=").substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash < 0) return -1L to -1L
            val first = spec.substring(0, dash).trim()
            val last = spec.substring(dash + 1).trim()
            return when {
                first.isEmpty() && last.isNotEmpty() -> { // "bytes=-500": the last 500 bytes
                    val n = last.toLongOrNull() ?: return -1L to -1L
                    maxOf(0, size - n) to size - 1
                }
                else -> {
                    val start = first.toLongOrNull() ?: return -1L to -1L
                    val end = last.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
                    start to end
                }
            }
        }
    }
}

/** The type the TV expects for a file name, e.g. "Trip.mkv" -> "video/x-matroska". */
fun videoMimeType(fileName: String, fallback: String): String = when (fileName.substringAfterLast('.').lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "mov" -> "video/quicktime"
    "avi" -> "video/x-msvideo"
    "ts", "m2ts", "mts" -> "video/mp2t"
    "3gp" -> "video/3gpp"
    "wmv" -> "video/x-ms-wmv"
    else -> fallback.ifBlank { "video/mp4" }
}
