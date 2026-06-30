package app.yukine.playback;

import android.graphics.Bitmap;

import app.yukine.model.Track;

interface PlaybackNotificationArtworkSource {
    Bitmap notificationArtworkFor(Track track);

    byte[] notificationArtworkDataFor(Track track);
}
