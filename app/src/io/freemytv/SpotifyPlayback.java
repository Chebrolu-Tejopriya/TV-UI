package io.freemytv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

/** Only Spotify's media session. No notification contents, audio capture, disk storage, or network calls. */
final class SpotifyPlayback {
    static final String PACKAGE = "com.spotify.tv.android";
    private static SpotifyPlayback instance;
    private final Context app;
    private final MediaSessionManager manager;
    private final ComponentName listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> observers = new ArrayList<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::choose;
    private boolean watching;
    private MediaController controller;
    private MediaMetadata metadata;
    private PlaybackState state;

    static SpotifyPlayback get(Context context) {
        if (instance == null) instance = new SpotifyPlayback(context);
        return instance;
    }

    private SpotifyPlayback(Context context) {
        app = context.getApplicationContext();
        manager = (MediaSessionManager) app.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(app, SpotifyListener.class);
    }

    boolean hasAccess() {
        String enabled = Settings.Secure.getString(app.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        for (String value : enabled.split(":")) if (listener.equals(ComponentName.unflattenFromString(value))) return true;
        return false;
    }

    void add(Runnable observer) {
        if (!observers.contains(observer)) observers.add(observer);
        refresh();
    }

    void remove(Runnable observer) { observers.remove(observer); }

    void refresh() {
        if (manager == null || !hasAccess()) {
            if (watching && manager != null) manager.removeOnActiveSessionsChangedListener(sessions);
            watching = false;
            choose(null);
            return;
        }
        try {
            if (!watching) {
                manager.addOnActiveSessionsChangedListener(sessions, listener, handler);
                watching = true;
            }
            choose(manager.getActiveSessions(listener));
        } catch (SecurityException e) {
            if (watching) manager.removeOnActiveSessionsChangedListener(sessions);
            watching = false;
            choose(null);
        }
    }

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata value) { metadata = value; changed(); }
        @Override public void onPlaybackStateChanged(PlaybackState value) { state = value; changed(); }
        @Override public void onSessionDestroyed() { choose(null); handler.post(SpotifyPlayback.this::refresh); }
    };

    private void choose(List<MediaController> active) {
        MediaController next = null;
        if (active != null) for (MediaController candidate : active) {
            if (!PACKAGE.equals(candidate.getPackageName())) continue;
            next = candidate;
            PlaybackState playback = candidate.getPlaybackState();
            if (playback != null && playback.getState() == PlaybackState.STATE_PLAYING) break;
        }
        if (controller != null && next != null && controller.getSessionToken().equals(next.getSessionToken())) {
            metadata = controller.getMetadata(); state = controller.getPlaybackState(); changed(); return;
        }
        if (controller != null) controller.unregisterCallback(callback);
        controller = next;
        if (controller != null) {
            controller.registerCallback(callback, handler);
            metadata = controller.getMetadata(); state = controller.getPlaybackState();
        } else {
            metadata = null; state = null;
        }
        changed();
    }

    private void changed() {
        for (Runnable observer : new ArrayList<>(observers)) observer.run();
    }

    boolean present() {
        return controller != null && state != null && state.getState() != PlaybackState.STATE_NONE
                && state.getState() != PlaybackState.STATE_STOPPED && state.getState() != PlaybackState.STATE_ERROR;
    }

    boolean playing() { return present() && state.getState() == PlaybackState.STATE_PLAYING; }
    boolean buffering() { return present() && (state.getState() == PlaybackState.STATE_BUFFERING || state.getState() == PlaybackState.STATE_CONNECTING); }

    String title() {
        if (metadata == null) return "Spotify";
        MediaDescription description = metadata.getDescription();
        CharSequence value = description.getTitle();
        return value == null || value.length() == 0 ? "Spotify" : value.toString();
    }

    String artist() {
        if (metadata == null) return "";
        String value = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (value != null && !value.isEmpty()) return value;
        CharSequence subtitle = metadata.getDescription().getSubtitle();
        return subtitle == null ? "" : subtitle.toString();
    }

    Bitmap artwork() {
        if (metadata == null) return null;
        Bitmap image = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (image == null) image = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        return image != null ? image : metadata.getDescription().getIconBitmap();
    }

    long duration() { return metadata == null ? 0 : Math.max(0, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)); }
    long position() {
        if (state == null) return 0;
        long position = Math.max(0, state.getPosition());
        if (playing() && state.getLastPositionUpdateTime() > 0)
            position += (long) ((SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime()) * state.getPlaybackSpeed());
        return duration() > 0 ? Math.min(duration(), position) : position;
    }

    boolean enabled(int control) {
        if (!present()) return false;
        long actions = state.getActions();
        if (control == 0) return (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0;
        if (control == 2) return (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0;
        return (actions & (PlaybackState.ACTION_PLAY_PAUSE | (playing() || buffering() ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY))) != 0;
    }

    boolean activate(int control) {
        if (!enabled(control)) return false;
        try {
            MediaController.TransportControls transport = controller.getTransportControls();
            if (control == 0) transport.skipToPrevious();
            else if (control == 2) transport.skipToNext();
            else if (playing() || buffering()) transport.pause();
            else transport.play();
            return true;
        } catch (RuntimeException e) { refresh(); return false; }
    }

    boolean openAccessSettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            return true;
        } catch (RuntimeException e) { return false; }
    }
}
