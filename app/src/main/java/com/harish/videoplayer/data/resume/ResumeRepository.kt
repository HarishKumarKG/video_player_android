package com.harish.videoplayer.data.resume

import android.content.Context
import com.harish.videoplayer.domain.resume.ResumeEntry
import com.harish.videoplayer.domain.resume.ResumePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Remembers where you stopped each video (video id -> position), saved in "resume.json".
 *
 * Everything is kept in memory as a StateFlow so the list can show the red "watched so far"
 * bars instantly; the file is rewritten in the background after each change.
 */
@Singleton
class ResumeRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val file = File(context.filesDir, "resume.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _entries = MutableStateFlow<Map<Long, ResumeEntry>>(emptyMap())
    val entries: StateFlow<Map<Long, ResumeEntry>> = _entries.asStateFlow()

    init {
        // Anything saved before the file finished loading wins over the file
        scope.launch { _entries.update { current -> read() + current } }
    }

    /** Saved position for this video, or null to start from the beginning. */
    fun positionFor(videoId: Long): Long? = _entries.value[videoId]?.positionMs

    /** Saves the position if it's worth remembering (see [ResumePolicy]); otherwise forgets it. */
    fun save(videoId: Long, positionMs: Long, durationMs: Long) {
        if (!ResumePolicy.shouldRemember(positionMs, durationMs)) {
            clear(videoId)
            return
        }
        _entries.update { entries ->
            val updated = entries + (videoId to ResumeEntry(positionMs, durationMs, System.currentTimeMillis()))
            // Keep the file small: only the most recent MAX_ENTRIES videos
            if (updated.size <= MAX_ENTRIES) updated
            else updated.entries.sortedByDescending { it.value.updatedAtMs }.take(MAX_ENTRIES).associate { it.toPair() }
        }
        persist()
    }

    fun clear(videoId: Long) {
        if (videoId !in _entries.value) return
        _entries.update { it - videoId }
        persist()
    }

    /** "Clear recently played": every video starts from the beginning again. */
    fun clearAll() {
        _entries.value = emptyMap()
        persist()
    }

    private fun persist() {
        scope.launch {
            writeLock.withLock {
                val json = JSONObject()
                _entries.value.forEach { (id, e) ->
                    json.put(
                        id.toString(),
                        JSONObject()
                            .put("position", e.positionMs)
                            .put("duration", e.durationMs)
                            .put("updated", e.updatedAtMs)
                    )
                }
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json.toString())
                tmp.renameTo(file)
            }
        }
    }

    private fun read(): Map<Long, ResumeEntry> = try {
        if (!file.exists()) emptyMap()
        else {
            val json = JSONObject(file.readText())
            json.keys().asSequence().associate { key ->
                val e = json.getJSONObject(key)
                key.toLong() to ResumeEntry(e.getLong("position"), e.getLong("duration"), e.getLong("updated"))
            }
        }
    } catch (e: Exception) {
        emptyMap() // corrupt file: start fresh
    }

    private companion object {
        const val MAX_ENTRIES = 2_000
    }
}
