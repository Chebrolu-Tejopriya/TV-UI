package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;

/** Bundled artwork, decoded once when selected, never while drawing a frame. */
final class Wallpaper {
    private static Context app;
    private static Bitmap image;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect source = new Rect();
    private final RectF target = new RectF();

    static void init(Context context) { app = context.getApplicationContext(); }

    static boolean load() {
        if (image != null) return true;
        try {
            // Share the drawable resource with the starting window rather than decode a second bitmap from assets.
            image = ((BitmapDrawable) app.getDrawable(R.drawable.home_wallpaper)).getBitmap();
        } catch (RuntimeException e) {
            AppLog.w("wallpaper unavailable: " + e.getMessage());
        }
        return image != null;
    }

    static Bitmap selected() {
        return Prefs.skyWallpaper() ? image : null;
    }

    void draw(Canvas canvas, int w, int h, float darkness) {
        Bitmap bitmap = selected();
        if (bitmap == null) return;
        float scale = Math.max((float) w / bitmap.getWidth(), (float) h / bitmap.getHeight());
        int sw = Math.min(bitmap.getWidth(), Math.round(w / scale));
        int sh = Math.min(bitmap.getHeight(), Math.round(h / scale));
        int x = (bitmap.getWidth() - sw) / 2, y = (bitmap.getHeight() - sh) / 2;
        source.set(x, y, x + sw, y + sh);
        target.set(0, 0, w, h);
        paint.setColor(0xFFFFFFFF);
        canvas.drawBitmap(bitmap, source, target, paint);
        // Preserve the artwork's color, with a modest night scrim for text contrast.
        paint.setColor(Ui.alpha(0xFF071626, 0.04f + 0.12f * Math.max(0, Math.min(1, darkness))));
        canvas.drawRect(target, paint);
    }
}
