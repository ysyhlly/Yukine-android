package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.yukine.playback.manager.PlaybackQueueManager;

import static org.junit.Assert.assertEquals;

public class PlaybackQueueRestoreOwnerTest {
    @Test
    public void restoresAndPreparesPlaybackWhenQueueIsRestorable() {
        List<String> events = new ArrayList<>();
        FakeQueueRestoreActions actions = new FakeQueueRestoreActions(
                events,
                new PlaybackQueueManager.RestorePlaybackResult(true, true, true)
        );
        PlaybackQueueRestoreOwner owner = new PlaybackQueueRestoreOwner(
                actions::restorePlaybackQueue,
                actions::restoreLastPlayback,
                actions::setPlaybackRestoreEnabled,
                new FakeRestorePlaybackBoundary(events)
        );

        owner.restoreLastPlayback(false);

        assertEquals(
                Arrays.asList(
                        "restore:false",
                        "create",
                        "prepare:true"
                ),
                events
        );
    }

    @Test
    public void publishesStateWhenRestoreDoesNotPreparePlayback() {
        List<String> events = new ArrayList<>();
        FakeQueueRestoreActions actions = new FakeQueueRestoreActions(
                events,
                new PlaybackQueueManager.RestorePlaybackResult(true, false, false)
        );
        PlaybackQueueRestoreOwner owner = new PlaybackQueueRestoreOwner(
                actions::restorePlaybackQueue,
                actions::restoreLastPlayback,
                actions::setPlaybackRestoreEnabled,
                new FakeRestorePlaybackBoundary(events)
        );

        owner.restoreLastPlayback(true);

        assertEquals(
                Arrays.asList(
                        "restore:true",
                        "create",
                        "publish"
                ),
                events
        );
    }

    @Test
    public void missingQueueActionsPublishesEmptyRestoreState() {
        List<String> events = new ArrayList<>();
        PlaybackQueueRestoreOwner missingActions = new PlaybackQueueRestoreOwner(
                null,
                null,
                null,
                new FakeRestorePlaybackBoundary(events)
        );

        missingActions.restoreLastPlayback(false);

        assertEquals(Collections.singletonList("publish"), events);
    }

    @Test
    public void missingPlaybackQueueManagerSupplierPublishesEmptyRestoreState() {
        List<String> events = new ArrayList<>();
        PlaybackQueueRestoreOwner owner = PlaybackQueueRestoreOwner.fromPlaybackQueueManager(
                null,
                new FakeRestorePlaybackBoundary(events)
        );

        owner.restoreLastPlayback(false);

        assertEquals(Collections.singletonList("publish"), events);
    }

    @Test
    public void ignoresMissingRestoreBoundary() {
        List<String> events = new ArrayList<>();
        FakeQueueRestoreActions actions = new FakeQueueRestoreActions(
                events,
                new PlaybackQueueManager.RestorePlaybackResult(true, true, true)
        );
        PlaybackQueueRestoreOwner owner = new PlaybackQueueRestoreOwner(
                actions::restorePlaybackQueue,
                actions::restoreLastPlayback,
                actions::setPlaybackRestoreEnabled,
                null
        );

        owner.restoreLastPlayback(false);

        assertEquals(Collections.singletonList("restore:false"), events);
    }

    @Test
    public void delegatesRestoreEnabledSetting() {
        List<String> events = new ArrayList<>();
        FakeQueueRestoreActions actions = new FakeQueueRestoreActions(events, null);
        PlaybackQueueRestoreOwner owner = new PlaybackQueueRestoreOwner(
                actions::restorePlaybackQueue,
                actions::restoreLastPlayback,
                actions::setPlaybackRestoreEnabled,
                null
        );
        PlaybackQueueRestoreOwner missingActions = new PlaybackQueueRestoreOwner(null, null, null, null);

        owner.setPlaybackRestoreEnabled(true);
        missingActions.setPlaybackRestoreEnabled(false);

        assertEquals(Collections.singletonList("enabled:true"), events);
    }

    @Test
    public void delegatesQueueRestoreSnapshot() {
        List<String> events = new ArrayList<>();
        FakeQueueRestoreActions actions = new FakeQueueRestoreActions(events, null);
        PlaybackQueueRestoreOwner owner = new PlaybackQueueRestoreOwner(
                actions::restorePlaybackQueue,
                actions::restoreLastPlayback,
                actions::setPlaybackRestoreEnabled,
                null
        );
        PlaybackQueueRestoreOwner missingActions = new PlaybackQueueRestoreOwner(null, null, null, null);

        owner.restorePlaybackQueue();
        missingActions.restorePlaybackQueue();

        assertEquals(Collections.singletonList("restoreQueue"), events);
    }

    private static final class FakeQueueRestoreActions {
        private final List<String> events;
        private final PlaybackQueueManager.RestorePlaybackResult restoreResult;

        private FakeQueueRestoreActions(
                List<String> events,
                PlaybackQueueManager.RestorePlaybackResult restoreResult
        ) {
            this.events = events;
            this.restoreResult = restoreResult;
        }

        public PlaybackQueueManager.QueueStateSnapshot restorePlaybackQueue() {
            events.add("restoreQueue");
            return PlaybackQueueManager.QueueStateSnapshot.empty();
        }

        public PlaybackQueueManager.RestorePlaybackResult restoreLastPlayback(boolean playWhenRestored) {
            events.add("restore:" + playWhenRestored);
            return restoreResult;
        }

        public void setPlaybackRestoreEnabled(boolean enabled) {
            events.add("enabled:" + enabled);
        }
    }

    private static final class FakeRestorePlaybackBoundary
            implements PlaybackQueueRestoreOwner.RestorePlaybackBoundary {
        private final List<String> events;

        private FakeRestorePlaybackBoundary(List<String> events) {
            this.events = events;
        }

        @Override
        public void createPlayerIfNeeded() {
            events.add("create");
        }

        @Override
        public void prepareCurrent(boolean playWhenReady) {
            events.add("prepare:" + playWhenReady);
        }

        @Override
        public void publishState() {
            events.add("publish");
        }
    }
}
