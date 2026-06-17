package app.yukine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.yukine.model.Track;

import static org.junit.Assert.assertEquals;

public final class NetworkRequestControllerTest {
    @Test
    public void streamRequestsPublishStatusBeforeDelegatingOperation() {
        FakeSink sink = new FakeSink();
        FakeListener listener = new FakeListener();
        NetworkRequestController controller = controller(sink, listener);

        controller.addStreamUrl("Title", "https://example.test/audio.mp3");
        controller.updateStreamUrl(null, "Title 2", "https://example.test/audio2.mp3");
        controller.importM3uPlaylist("https://example.test/list.m3u8");

        assertEquals(
                Arrays.asList(
                        "status:label:adding.stream",
                        "addStream:Title|https://example.test/audio.mp3",
                        "status:label:updating.stream",
                        "updateStream:Title 2|https://example.test/audio2.mp3",
                        "status:label:importing.m3u.playlist",
                        "importM3u:https://example.test/list.m3u8"
                ),
                events(listener, sink)
        );
    }

    @Test
    public void destructiveRequestsPublishStatusBeforeDelegatingOperation() {
        FakeSink sink = new FakeSink();
        FakeListener listener = new FakeListener();
        NetworkRequestController controller = controller(sink, listener);

        controller.deleteAllStreams();
        controller.deleteTrack(42L, "deleted");
        controller.deleteTracks(Arrays.asList(42L, 43L), "deleted 2");
        controller.deleteRemoteSource(7L);

        assertEquals(
                Arrays.asList(
                        "status:label:deleting.streams",
                        "deleteAllStreams",
                        "status:label:deleting.stream",
                        "deleteTrack:42|deleted",
                        "status:label:deleting.stream",
                        "deleteTracks:42,43|deleted 2",
                        "status:label:deleting.source",
                        "deleteRemoteSource:7"
                ),
                events(listener, sink)
        );
    }

    @Test
    public void webDavRequestsPublishStatusBeforeDelegatingOperation() {
        FakeSink sink = new FakeSink();
        FakeListener listener = new FakeListener();
        NetworkRequestController controller = controller(sink, listener);

        controller.saveWebDavSource(3L, "Home", "https://dav.example.test", "u", "p", "Music");
        controller.testRemoteSource(3L);
        controller.syncRemoteSource(3L, "Home");
        controller.syncAllWebDavSources(Arrays.asList(1L, 2L));

        assertEquals(
                Arrays.asList(
                        "status:label:saving.webdav.source",
                        "saveWebDav:3|Home|https://dav.example.test|u|p|Music",
                        "status:label:test...",
                        "testRemoteSource:3",
                        "status:label:syncingHome",
                        "syncRemoteSource:3|Home",
                        "status:label:syncing.webdav.sources",
                        "syncAllWebDavSources:1,2"
                ),
                events(listener, sink)
        );
    }

    private NetworkRequestController controller(FakeSink sink, FakeListener listener) {
        return new NetworkRequestController(
                sink,
                new NetworkRequestController.Labels() {
                    @Override
                    public String text(String key) {
                        return "label:" + key;
                    }
                },
                listener
        );
    }

    private List<String> events(FakeListener listener, FakeSink sink) {
        ArrayList<String> result = new ArrayList<>();
        int count = Math.max(listener.events.size(), sink.events.size());
        for (int index = 0; index < count; index++) {
            if (index < listener.events.size()) {
                result.add(listener.events.get(index));
            }
            if (index < sink.events.size()) {
                result.add(sink.events.get(index));
            }
        }
        return result;
    }

    private static final class FakeListener implements NetworkRequestController.Listener {
        private final ArrayList<String> events = new ArrayList<>();

        @Override
        public void setStatus(String status) {
            events.add("status:" + status);
        }
    }

    private static final class FakeSink implements NetworkOperationSink {
        private final ArrayList<String> events = new ArrayList<>();

        @Override
        public void addStreamUrl(String title, String url) {
            events.add("addStream:" + title + "|" + url);
        }

        @Override
        public void updateStreamUrl(Track oldTrack, String title, String url) {
            events.add("updateStream:" + title + "|" + url);
        }

        @Override
        public void importM3uPlaylist(String url) {
            events.add("importM3u:" + url);
        }

        @Override
        public void deleteAllStreams() {
            events.add("deleteAllStreams");
        }

        @Override
        public void deleteTrack(long trackId, String status) {
            events.add("deleteTrack:" + trackId + "|" + status);
        }

        @Override
        public void deleteTracks(List<Long> trackIds, String status) {
            ArrayList<String> values = new ArrayList<>();
            for (Long trackId : trackIds) {
                values.add(String.valueOf(trackId));
            }
            events.add("deleteTracks:" + String.join(",", values) + "|" + status);
        }

        @Override
        public void deleteRemoteSource(long sourceId) {
            events.add("deleteRemoteSource:" + sourceId);
        }

        @Override
        public void saveWebDavSource(long sourceId, String name, String baseUrl, String username, String password, String rootPath) {
            events.add("saveWebDav:" + sourceId + "|" + name + "|" + baseUrl + "|" + username + "|" + password + "|" + rootPath);
        }

        @Override
        public void testRemoteSource(long sourceId) {
            events.add("testRemoteSource:" + sourceId);
        }

        @Override
        public void syncRemoteSource(long sourceId, String sourceName) {
            events.add("syncRemoteSource:" + sourceId + "|" + sourceName);
        }

        @Override
        public void syncAllWebDavSources(List<Long> sourceIds) {
            ArrayList<String> values = new ArrayList<>();
            for (Long sourceId : sourceIds) {
                values.add(String.valueOf(sourceId));
            }
            events.add("syncAllWebDavSources:" + String.join(",", values));
        }
    }
}
