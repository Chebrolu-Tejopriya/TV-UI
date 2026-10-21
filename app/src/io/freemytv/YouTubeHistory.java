package io.freemytv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** At most three videos observed playing on this TV. Never reads account history, cookies, or notifications. */
final class YouTubeHistory {
    static final String PACKAGE = "com.google.android.youtube.tv";
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern VIDEO_URL = Pattern.compile("(?:[?&]v=|youtu\\.be/|/vi(?:_webp)?/|/shorts/)([A-Za-z0-9_-]{11})(?:[^A-Za-z0-9_-]|$)");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static YouTubeHistory instance;
    private final Context app;
    private final MediaSessionManager manager;
    private final ComponentName listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> observers = new ArrayList<>();
    private final List<Video> recent = new ArrayList<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::choose;
    private MediaController controller;
    private boolean watching, reportedCapabilities, metadataDirty = true;
    private final Runnable captureSoon = this::capture;

    static final class Video {
        final String key, title, author, videoId;
        final long duration;
        final Bitmap artwork;
        Video(String key, String title, String author, String id, long duration, Bitmap artwork) {
            this.key = key; this.title = title; this.author = author; this.videoId = id;
            this.duration = duration; this.artwork = artwork;
        }
    }

    static YouTubeHistory get(Context c) {
        if (instance == null) instance = new YouTubeHistory(c);
        return instance;
    }

    private YouTubeHistory(Context c) {
        app = c.getApplicationContext();
        manager = (MediaSessionManager) app.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(app, SpotifyListener.class);
        boolean removeSetupSamples = !app.getSharedPreferences("youtube-recents", Context.MODE_PRIVATE)
                .getBoolean("setupSamplesRemoved", false);
        boolean saveNeeded = removeSetupSamples;
        Set<String> seen = new HashSet<>();
        try {
            JSONArray saved = new JSONArray(app.getSharedPreferences("youtube-recents", Context.MODE_PRIVATE).getString("videos", "[]"));
            for (int i = 0; i < Math.min(3, saved.length()); i++) {
                JSONObject v = saved.getJSONObject(i);
                if (removeSetupSamples && isSetupSample(v.getString("title"), v.optString("author"))) continue;
                String oldKey = v.getString("key");
                if (!oldKey.matches("[a-f0-9]{64}")) continue;
                String title = v.getString("title"), author = v.optString("author"), id = v.optString("videoId");
                String key = identity(id, title, author);
                if (!seen.add(key)) { saveNeeded = true; continue; }
                saveNeeded |= !oldKey.equals(key);
                Bitmap art = BitmapFactory.decodeFile(artFile(oldKey).getAbsolutePath());
                recent.add(new Video(key, title, author, id, v.optLong("duration"), art));
            }
        } catch (Exception ignored) { recent.clear(); }
        // One-time migration: keep the owner's entries, remove only the four clips used during installation tests.
        // Later playback of these films is legitimate history and must not be filtered.
        if (saveNeeded) save();
    }

    private static boolean isSetupSample(String title, String author) {
        if (!"Blender".equals(author)) return false;
        return "Big Buck Bunny 60fps 4K - Official Blender Foundation Short Film".equals(title)
                || "Sintel - Open Movie by Blender Foundation".equals(title)
                || "Elephants Dream".equals(title)
                || "Tears of Steel - Blender VFX Open Movie".equals(title);
    }

    void add(Runnable observer) { if (!observers.contains(observer)) observers.add(observer); refresh(); }
    void remove(Runnable observer) { observers.remove(observer); }
    List<Video> videos() { return new ArrayList<>(recent); }

    void refresh() {
        if (manager == null || !SpotifyPlayback.get(app).hasAccess()) {
            if (watching && manager != null) manager.removeOnActiveSessionsChangedListener(sessions);
            watching = false; choose(null); return;
        }
        try {
            if (!watching) {
                manager.addOnActiveSessionsChangedListener(sessions, listener, handler);
                watching = true;
            }
            choose(manager.getActiveSessions(listener));
        } catch (SecurityException e) {
            if (watching) manager.removeOnActiveSessionsChangedListener(sessions);
            watching = false; choose(null);
        }
    }

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata value) { metadataDirty = true; scheduleCapture(); }
        @Override public void onPlaybackStateChanged(PlaybackState value) { scheduleCapture(); }
        @Override public void onSessionDestroyed() { choose(null); handler.post(YouTubeHistory.this::refresh); }
    };

    private void choose(List<MediaController> active) {
        MediaController next = null;
        if (active != null) for (MediaController candidate : active) {
            if (PACKAGE.equals(candidate.getPackageName())) { next = candidate; break; }
        }
        if (controller != null && next != null && controller.getSessionToken().equals(next.getSessionToken())) {
            scheduleCapture(); return;
        }
        if (controller != null) controller.unregisterCallback(callback);
        handler.removeCallbacks(captureSoon);
        controller = next;
        metadataDirty = true;
        if (controller != null) { controller.registerCallback(callback, handler); scheduleCapture(); }
    }

    private void scheduleCapture() {
        handler.removeCallbacks(captureSoon);
        handler.postDelayed(captureSoon, 1200);
    }

    private void capture() {
        if (controller == null || !metadataDirty) return;
        PlaybackState state = controller.getPlaybackState();
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null || state == null || state.getState() != PlaybackState.STATE_PLAYING) return;
        MediaDescription description = metadata.getDescription();
        String title = text(description.getTitle()), author = text(description.getSubtitle());
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (title.isEmpty() || title.equalsIgnoreCase("YouTube") || title.equalsIgnoreCase("Advertisement") || duration <= 0) return;
        String id = videoId(description.getMediaId());
        if (id.isEmpty()) id = videoId(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI));
        if (id.isEmpty()) id = videoId(metadata.getString(MediaMetadata.METADATA_KEY_ART_URI));
        if (id.isEmpty()) id = videoId(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI));
        if (id.isEmpty()) id = videoId(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI));
        Bitmap art = description.getIconBitmap();
        String key = identity(id, title, author);
        metadataDirty = false;
        if (art == null) for (Video old : recent) if (old.key.equals(key)) { art = old.artwork; break; }
        // Keep only a small local thumbnail, not YouTube's full-size bitmap.
        if (art != null && (art.getWidth() > 480 || art.getHeight() > 270)) {
            float ratio = Math.min(480f / art.getWidth(), 270f / art.getHeight());
            art = Bitmap.createScaledBitmap(art, Math.max(1, Math.round(art.getWidth()*ratio)), Math.max(1, Math.round(art.getHeight()*ratio)), true);
        }
        for (int i = recent.size() - 1; i >= 0; i--) if (recent.get(i).key.equals(key)) recent.remove(i);
        recent.add(0, new Video(key, title, author, id, duration, art));
        while (recent.size() > 3) recent.remove(recent.size() - 1);
        save();
        if (!reportedCapabilities) {
            reportedCapabilities = true;
            AppLog.w("YouTube recents: local playback metadata available; direct link=" + !id.isEmpty() + "; artwork=" + (art != null));
        }
        for (Runnable observer : new ArrayList<>(observers)) observer.run();
    }

    private File artFile(String key) { return new File(app.getFilesDir(), "youtube-recent-" + key + ".jpg"); }

    private void save() {
        List<Video> snapshot = videos();
        IO.execute(() -> {
            JSONArray values = new JSONArray();
            try {
                for (Video v : snapshot) {
                    values.put(new JSONObject().put("key", v.key).put("title", v.title).put("author", v.author)
                            .put("videoId", v.videoId).put("duration", v.duration));
                    if (v.artwork != null && (v == snapshot.get(0) || !artFile(v.key).exists())) {
                        AtomicFile file = new AtomicFile(artFile(v.key));
                        FileOutputStream out = null;
                        try { out = file.startWrite(); v.artwork.compress(Bitmap.CompressFormat.JPEG, 85, out); file.finishWrite(out); }
                        catch (Exception e) { if (out != null) file.failWrite(out); }
                    }
                }
                app.getSharedPreferences("youtube-recents", Context.MODE_PRIVATE).edit()
                        .putString("videos", values.toString()).putBoolean("setupSamplesRemoved", true).commit();
                File[] files = app.getFilesDir().listFiles();
                if (files != null) for (File f : files) {
                    if (!f.getName().matches("youtube-recent-[a-f0-9]{64}\\.jpg")) continue;
                    boolean keep = false;
                    for (Video v : snapshot) if (f.equals(artFile(v.key))) { keep = true; break; }
                    if (!keep) f.delete();
                }
            } catch (Exception e) { AppLog.w("Couldn't save YouTube recents."); }
        });
    }

    /** Direct video when a link is provided; otherwise an honest title search inside the YouTube TV app. */
    boolean open(Activity activity, Video video) {
        Uri uri = video.videoId.isEmpty()
                ? Uri.parse("https://www.youtube.com/results").buildUpon().appendQueryParameter("search_query", video.title).build()
                : Uri.parse("https://www.youtube.com/watch").buildUpon().appendQueryParameter("v", video.videoId).build();
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) { return false; }
    }

    static String videoId(String value) {
        if (value == null) return "";
        if (VIDEO_ID.matcher(value).matches()) return value;
        Matcher matcher = VIDEO_URL.matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String text(CharSequence value) { return value == null ? "" : value.toString().trim(); }
    private static String identity(String id, String title, String author) {
        // Duration can change between metadata callbacks for the same video, particularly around adverts/live playback.
        // On this TV YouTube omits video IDs, so the stable fallback is the normalized title and channel.
        return digest(id.isEmpty() ? title.trim().replaceAll("\\s+", " ") + "\n" + author.trim().replaceAll("\\s+", " ") : id);
    }
    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
