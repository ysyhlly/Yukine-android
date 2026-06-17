package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkDialogEventControllerTest {
    @Test
    fun forwardsDialogActionsThroughNetworkRequestController() {
        val sink = FakeSink()
        val listener = FakeListener()
        val requestController = NetworkRequestController(
            sink,
            object : NetworkRequestController.Labels {
                override fun text(key: String): String = "label:$key"
            },
            listener
        )
        val controller = NetworkDialogEventController(requestController)
        val track = Track(9L, "Old", "Artist", "Album", 1000L, Uri.EMPTY, "file:old")

        controller.addStream("Title", "https://example.test/audio.mp3")
        controller.importM3u("https://example.test/list.m3u8")
        controller.updateStream(track, "Updated", "https://example.test/updated.mp3")
        controller.saveWebDavSource(3L, "Home", "https://dav.example.test", "u", "p", "Music")

        assertEquals(
            listOf(
                "status:label:adding.stream",
                "addStream:Title|https://example.test/audio.mp3",
                "status:label:importing.m3u.playlist",
                "importM3u:https://example.test/list.m3u8",
                "status:label:updating.stream",
                "updateStream:9|Updated|https://example.test/updated.mp3",
                "status:label:saving.webdav.source",
                "saveWebDav:3|Home|https://dav.example.test|u|p|Music"
            ),
            events(listener, sink)
        )
    }

    private fun events(listener: FakeListener, sink: FakeSink): List<String> {
        val result = ArrayList<String>()
        val count = maxOf(listener.events.size, sink.events.size)
        for (index in 0 until count) {
            if (index < listener.events.size) {
                result.add(listener.events[index])
            }
            if (index < sink.events.size) {
                result.add(sink.events[index])
            }
        }
        return result
    }

    private class FakeListener : NetworkRequestController.Listener {
        val events = ArrayList<String>()

        override fun setStatus(status: String) {
            events.add("status:$status")
        }
    }

    private class FakeSink : NetworkOperationSink {
        val events = ArrayList<String>()

        override fun addStreamUrl(title: String, url: String) {
            events.add("addStream:$title|$url")
        }

        override fun updateStreamUrl(oldTrack: Track?, title: String, url: String) {
            events.add("updateStream:${oldTrack?.id}|$title|$url")
        }

        override fun importM3uPlaylist(url: String) {
            events.add("importM3u:$url")
        }

        override fun deleteAllStreams() = Unit

        override fun deleteTrack(trackId: Long, status: String) = Unit

        override fun deleteTracks(trackIds: List<Long>, status: String) = Unit

        override fun deleteRemoteSource(sourceId: Long) = Unit

        override fun saveWebDavSource(
            sourceId: Long,
            name: String,
            baseUrl: String,
            username: String,
            password: String,
            rootPath: String
        ) {
            events.add("saveWebDav:$sourceId|$name|$baseUrl|$username|$password|$rootPath")
        }

        override fun testRemoteSource(sourceId: Long) = Unit

        override fun syncRemoteSource(sourceId: Long, sourceName: String) = Unit

        override fun syncAllWebDavSources(sourceIds: List<Long>) = Unit
    }
}
