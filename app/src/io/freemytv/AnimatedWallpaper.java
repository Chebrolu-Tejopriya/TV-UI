package io.freemytv;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;

/** Silent hardware-decoded background. The Canvas still stays visible until a video frame is ready. */
final class AnimatedWallpaper extends TextureView implements TextureView.SurfaceTextureListener {
    private final Runnable changed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Surface surface;
    private boolean active, ready, failed;
    private int videoWidth, videoHeight;
    private final Runnable timeout = () -> fail("preparation timed out");

    AnimatedWallpaper(Context context, Runnable changed) {
        super(context);
        this.changed = changed;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
    }

    boolean ready() { return ready; }

    void resume() {
        active = Prefs.liquidGlass() && Prefs.skyWallpaper() && Prefs.wallpaperMotion();
        if (active && isAvailable()) open();
    }

    void pause() {
        active = false;
        release();
    }

    private void open() {
        if (!active || failed || player != null || getSurfaceTexture() == null) return;
        MediaPlayer next = new MediaPlayer();
        player = next;
        try {
            surface = new Surface(getSurfaceTexture());
            next.setSurface(surface);
            next.setVolume(0, 0);
            next.setLooping(true);
            next.setOnVideoSizeChangedListener((mp, w, h) -> {
                if (mp != player) return;
                videoWidth = w;
                videoHeight = h;
                transform();
            });
            next.setOnPreparedListener(mp -> {
                if (mp != player || !active) return;
                try { mp.start(); } catch (RuntimeException e) { fail("could not start decoder"); }
            });
            next.setOnInfoListener((mp, what, extra) -> {
                if (mp == player && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && active) {
                    handler.removeCallbacks(timeout);
                    ready = true;
                    changed.run();
                    AppLog.w("animated wallpaper rendering");
                }
                return false;
            });
            next.setOnErrorListener((mp, what, extra) -> {
                if (mp == player) fail("decoder error " + what + "/" + extra);
                return true;
            });
            try (AssetFileDescriptor fd = getContext().getAssets().openFd("wallpapers/flowers-sky-loop.mp4")) {
                next.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            }
            handler.postDelayed(timeout, 15000);
            next.prepareAsync();
        } catch (Exception e) {
            fail(e.getClass().getSimpleName());
        }
    }

    private void fail(String reason) {
        failed = true;
        AppLog.w("animated wallpaper: " + reason + "; using still");
        release();
    }

    private void release() {
        handler.removeCallbacks(timeout);
        ready = false;
        MediaPlayer previous = player;
        player = null;
        if (previous != null) {
            previous.setOnPreparedListener(null);
            previous.setOnInfoListener(null);
            previous.setOnErrorListener(null);
            previous.setOnVideoSizeChangedListener(null);
            previous.release();
        }
        if (surface != null) { surface.release(); surface = null; }
        changed.run();
    }

    private void transform() {
        if (videoWidth <= 0 || videoHeight <= 0 || getWidth() == 0 || getHeight() == 0) return;
        float scale = Math.max((float) getWidth() / videoWidth, (float) getHeight() / videoHeight);
        Matrix matrix = new Matrix();
        matrix.setScale(videoWidth * scale / getWidth(), videoHeight * scale / getHeight(),
                getWidth() / 2f, getHeight() / 2f);
        setTransform(matrix);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) { open(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { transform(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { release(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
    @Override protected void onDetachedFromWindow() { pause(); super.onDetachedFromWindow(); }
}
