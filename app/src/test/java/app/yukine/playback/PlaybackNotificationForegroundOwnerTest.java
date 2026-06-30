package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PlaybackNotificationForegroundOwnerTest {
    @Test
    public void delegatesForegroundWorkToServiceBoundary() {
        List<String> events = new ArrayList<>();
        PlaybackNotificationForegroundOwner owner = new PlaybackNotificationForegroundOwner(
                () -> {
                    events.add("activity");
                    return null;
                },
                (action, requestCode) -> {
                    events.add("service:" + action + ":" + requestCode);
                    return null;
                },
                notification -> events.add("foreground:" + (notification == null))
        );

        assertNull(owner.activityPendingIntent());
        assertNull(owner.serviceActionPendingIntent("play", 7));
        owner.startPlaybackForeground(null);

        assertEquals(
                java.util.Arrays.asList(
                        "activity",
                        "service:play:7",
                        "foreground:true"
                ),
                events
        );
    }
}
