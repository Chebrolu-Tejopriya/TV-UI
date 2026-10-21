package io.freemytv;

import android.service.notification.NotificationListenerService;

/** Android's user-approved notification-listener permission unlocks MediaSessionManager access. */
public final class SpotifyListener extends NotificationListenerService {
    @Override public void onListenerConnected() {
        SpotifyPlayback.get(this).refresh();
        YouTubeHistory.get(this).refresh();
    }
    @Override public void onListenerDisconnected() {
        SpotifyPlayback.get(this).refresh();
        YouTubeHistory.get(this).refresh();
    }
    // Notification callbacks are intentionally not implemented: only media sessions are used.
}
