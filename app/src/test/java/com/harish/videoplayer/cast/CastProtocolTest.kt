package com.harish.videoplayer.cast

import com.harish.videoplayer.cast.dlna.DlnaRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastProtocolTest {

    @Test fun `range requests the TV sends`() {
        val size = 1_000L
        assertEquals(100L to 199L, CastServer.parseRange("bytes=100-199", size))
        assertEquals(100L to 999L, CastServer.parseRange("bytes=100-", size))       // "from 100 to the end"
        assertEquals(900L to 999L, CastServer.parseRange("bytes=-100", size))       // "the last 100 bytes"
        assertEquals(0L to 999L, CastServer.parseRange("bytes=0-5000", size))       // end past the file
        assertEquals(-1L to -1L, CastServer.parseRange(null, size))                 // no range: whole file
        assertEquals(-1L to -1L, CastServer.parseRange("items=1-2", size))
    }

    @Test fun `DLNA times`() {
        assertEquals("1:02:03", DlnaRenderer.formatTime(3_723_000))
        assertEquals("0:00:00", DlnaRenderer.formatTime(-5))
        assertEquals(3_723_000L, DlnaRenderer.parseTime("01:02:03"))
        assertEquals(3_723_000L, DlnaRenderer.parseTime("1:02:03.500"))
        assertEquals(0L, DlnaRenderer.parseTime("NOT_IMPLEMENTED"))
        assertEquals(0L, DlnaRenderer.parseTime(null))
    }

    @Test fun `reads values out of the TV's answers`() {
        val reply = """<s:Envelope><s:Body><u:GetPositionInfoResponse xmlns:u="urn:x">
            <Track>1</Track><TrackDuration>0:42:10</TrackDuration><RelTime>0:01:05</RelTime>
            </u:GetPositionInfoResponse></s:Body></s:Envelope>"""
        assertEquals("0:42:10", DlnaRenderer.tag(reply, "TrackDuration"))
        assertEquals("0:01:05", DlnaRenderer.tag(reply, "RelTime"))
        assertNull(DlnaRenderer.tag(reply, "AbsTime"))
        assertEquals("Tom & Jerry", DlnaRenderer.tag("<a:Title>Tom &amp; Jerry</a:Title>", "Title"))
    }

    @Test fun `metadata for the TV is valid escaped XML`() {
        val didl = DlnaRenderer.didl(
            CastMedia("http://192.168.1.5:4000/v/abc/Tom%20%26%20Jerry.mkv", "Tom & Jerry <4K>", "video/x-matroska", 123, 61_000)
        )
        assertTrue(didl.contains("<dc:title>Tom &amp; Jerry &lt;4K&gt;</dc:title>"))
        assertTrue(didl.contains("http-get:*:video/x-matroska:DLNA.ORG_OP=01"))
        assertTrue(didl.contains("duration=\"0:01:01.000\""))
    }

    @Test fun `file types the TV understands`() {
        assertEquals("video/x-matroska", videoMimeType("Movie.MKV", "application/octet-stream"))
        assertEquals("video/mp4", videoMimeType("clip.mp4", ""))
        assertEquals("video/mp2t", videoMimeType("tv.m2ts", ""))
        assertEquals("video/x-flv", videoMimeType("old.flv", "video/x-flv"))
    }
}
