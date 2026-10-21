package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.HashMap;
import java.util.Map;

/** Optional glass styling using Android 9 Canvas APIs. No WebView, RuntimeShader, or page capture. */
final class LiquidStyle {
    private static final int CACHE_WIDTH = 480;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix sampleMatrix = new Matrix(), shapeMatrix = new Matrix();
    private final RectF screen = new RectF(), inset = new RectF();
    private final LinearGradient sheen = new LinearGradient(0, 0, 0, 1,
            new int[] {0x52FFFFFF, 0x07FFFFFF, 0x08000000, 0x20FFFFFF},
            new float[] {0, 0.34f, 0.76f, 1}, Shader.TileMode.CLAMP);
    private final LinearGradient rim = new LinearGradient(0, 0, 1, 1,
            new int[] {0xD9FFFFFF, 0x26FFFFFF, 0x85D9EEFF},
            new float[] {0, 0.5f, 1}, Shader.TileMode.CLAMP);
    private final LinearGradient lightText = new LinearGradient(0, 0, 0, 1,
            new int[] {0xFF263957, 0xFF52698A, 0xFF24334D}, null, Shader.TileMode.CLAMP);
    private final LinearGradient darkText = new LinearGradient(0, 0, 0, 1,
            new int[] {0xFFFFFFFF, 0xFFC9E2F4, 0xFFF1E9FF}, null, Shader.TileMode.CLAMP);
    private Bitmap day, night;
    private Bitmap wallpaperImage;
    private final Wallpaper wallpaper = new Wallpaper();
    private BitmapShader daySample, nightSample;
    private int width, height;
    private final Map<String, Plate> plates = new HashMap<>();

    private static final class Plate {
        final RectF bounds;
        final int tint, screenWidth, screenHeight, focusColor;
        final float radius, tintAlpha, lift;
        final Bitmap resting, focused;

        Plate(RectF bounds, int tint, int screenWidth, int screenHeight, float radius, float tintAlpha,
              float lift, int focusColor, Bitmap resting, Bitmap focused) {
            this.bounds = new RectF(bounds);
            this.tint = tint;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.radius = radius;
            this.tintAlpha = tintAlpha;
            this.lift = lift;
            this.focusColor = focusColor;
            this.resting = resting;
            this.focused = focused;
        }
    }

    LiquidStyle() {
        edge.setStyle(Paint.Style.STROKE);
    }

    private void ensure(int w, int h) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        Bitmap selected = Wallpaper.selected();
        if (day != null && width == w && height == h && wallpaperImage == selected) return;
        wallpaperImage = selected;
        plates.clear();
        width = w;
        height = h;
        int cacheHeight = Math.max(1, Math.round(CACHE_WIDTH * (float) h / w));
        day = backdrop(CACHE_WIDTH, cacheHeight, false);
        night = backdrop(CACHE_WIDTH, cacheHeight, true);
        daySample = new BitmapShader(day, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        nightSample = new BitmapShader(night, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        screen.set(0, 0, width, height);
    }

    /** Soft color fields are rasterized once at quarter resolution, rather than recalculated each frame. */
    private Bitmap backdrop(int w, int h, boolean dark) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (wallpaperImage != null) {
            wallpaper.draw(canvas, w, h, dark ? 1 : 0);
            return bitmap;
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h,
                dark ? new int[] {0xFF101A30, 0xFF233C59, 0xFF292D47, 0xFF0C1425}
                     : new int[] {0xFFE6EDF9, 0xFFBCD7EB, 0xFFD8C9E9, 0xFFEAEDF5},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.78f, h * 0.25f, w * 0.66f,
                dark ? 0x7046B9DD : 0x99B9E8FF, 0x0046B9DD, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.14f, h * 0.7f, w * 0.53f,
                dark ? 0x8A7353AE : 0x99BDA7EC, 0x007353AE, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.8f, h * 1.1f, w * 0.52f,
                dark ? 0x557F537B : 0x88EBC6D5, 0x007F537B, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, p);
        p.setShader(null);
        return bitmap;
    }

    void background(Canvas c, int w, int h, float darkness) {
        ensure(w, h);
        if (wallpaperImage != null) {
            wallpaper.draw(c, w, h, darkness);
            return;
        }
        float d = Math.max(0, Math.min(1, darkness));
        paint.setShader(null);
        paint.setAlpha(255);
        if (d >= 0.999f) {
            c.drawBitmap(night, null, screen, paint);
        } else {
            c.drawBitmap(day, null, screen, paint);
        }
        if (d > 0.001f && d < 0.999f) {
            paint.setAlpha(Math.round(255 * d));
            c.drawBitmap(night, null, screen, paint);
        }
        paint.setAlpha(255);
    }

    /** Bake resting/focused plates once; movement only scales and crossfades two small textures. */
    void cachedSurface(Canvas c, String key, RectF base, RectF actual, float radius, int tint,
                       float tintAlpha, float lift, float focus, float alpha, int w, int h,
                       float offsetX, float offsetY) {
        cachedSurface(c, key, base, actual, radius, tint, tintAlpha, lift, focus, alpha,
                w, h, offsetX, offsetY, 0);
    }

    void cachedSurface(Canvas c, String key, RectF base, RectF actual, float radius, int tint,
                       float tintAlpha, float lift, float focus, float alpha, int w, int h,
                       float offsetX, float offsetY, int focusColor) {
        ensure(w, h);
        Plate plate = plates.get(key);
        if (plate == null || !plate.bounds.equals(base) || plate.tint != tint || plate.screenWidth != w
                || plate.screenHeight != h || plate.radius != radius || plate.tintAlpha != tintAlpha
                || plate.lift != lift || plate.focusColor != focusColor) {
            Bitmap resting = bake(base, radius, tint, tintAlpha, lift, w, h, offsetX, offsetY, 0);
            Bitmap focused = bake(base, radius, tint, tintAlpha, lift + 0.14f, w, h, offsetX, offsetY, focusColor);
            plate = new Plate(base, tint, w, h, radius, tintAlpha, lift, focusColor, resting, focused);
            plates.put(key, plate);
        }
        paint.setShader(null);
        paint.setAlpha(Math.round(255 * Math.max(0, Math.min(1, alpha))));
        c.drawBitmap(plate.resting, null, actual, paint);
        if (focus > 0.001f) {
            paint.setAlpha(Math.round(255 * Math.max(0, Math.min(1, focus)) * Math.max(0, Math.min(1, alpha))));
            c.drawBitmap(plate.focused, null, actual, paint);
        }
        paint.setAlpha(255);
    }

    private Bitmap bake(RectF bounds, float radius, int tint, float tintAlpha, float lift,
                        int w, int h, float offsetX, float offsetY, int focusColor) {
        int bw = Math.max(1, Math.round(bounds.width())), bh = Math.max(1, Math.round(bounds.height()));
        Bitmap bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(bw / bounds.width(), bh / bounds.height());
        canvas.translate(-bounds.left, -bounds.top);
        if (focusColor != 0) {
            paint.setShader(null);
            paint.setColor(focusColor);
            canvas.drawRoundRect(bounds, radius, radius, paint);
        }
        // Brand fill replaces the sampled body at full focus, while preserving the glass rim.
        surface(canvas, bounds, radius, tint, tintAlpha, lift, focusColor == 0 ? 1 : 0, 1, w, h, offsetX, offsetY);
        return bitmap;
    }

    void surface(Canvas c, RectF r, float radius, int tint, float tintAlpha, float lift,
                 float body, float alpha, int w, int h, float offsetX, float offsetY) {
        if (r.isEmpty() || alpha <= 0.004f) return;
        ensure(w, h);
        float a = Math.max(0, Math.min(1, alpha));
        float d = 1 - Math.max(Color.red(tint), Math.max(Color.green(tint), Color.blue(tint))) / 255f;
        float scale = width / 1920f;
        float rad = Math.min(radius, Math.min(r.width(), r.height()) / 2);
        sampleMatrix.setScale((float) width / day.getWidth(), (float) height / day.getHeight());
        sampleMatrix.postTranslate(-offsetX, -offsetY);
        daySample.setLocalMatrix(sampleMatrix);
        nightSample.setLocalMatrix(sampleMatrix);
        if (body > 0.001f) {
            paint.setShader(daySample);
            paint.setAlpha(Math.round(255 * a * body));
            c.drawRoundRect(r, rad, rad, paint);
            if (d > 0.001f) {
                paint.setShader(nightSample);
                paint.setAlpha(Math.round(255 * a * body * d));
                c.drawRoundRect(r, rad, rad, paint);
            }
            paint.setShader(null);
            paint.setColor(Ui.alpha(0xFFFFFFFF, a * body * (0.07f + Math.min(0.18f, tintAlpha * 0.16f) + lift * 0.55f)));
            c.drawRoundRect(r, rad, rad, paint);
            shapeMatrix.setScale(r.width(), r.height());
            shapeMatrix.postTranslate(r.left, r.top);
            sheen.setLocalMatrix(shapeMatrix);
            paint.setShader(sheen);
            paint.setAlpha(Math.round(255 * a * body));
            c.drawRoundRect(r, rad, rad, paint);
            paint.setShader(null);
        }
        shapeMatrix.setScale(r.width(), r.height());
        shapeMatrix.postTranslate(r.left, r.top);
        rim.setLocalMatrix(shapeMatrix);
        edge.setShader(rim);
        edge.setStrokeWidth(Math.max(1, 1.7f * scale));
        edge.setAlpha(Math.round(255 * a));
        inset.set(r);
        inset.inset(edge.getStrokeWidth() / 2, edge.getStrokeWidth() / 2);
        c.drawRoundRect(inset, rad, rad, edge);
        edge.setShader(null);
        edge.setColor(Ui.alpha(0xFFDCEEFF, a * (0.11f + Math.min(0.12f, lift))));
        edge.setStrokeWidth(Math.max(1, scale));
        inset.inset(2 * scale, 2 * scale);
        if (!inset.isEmpty()) c.drawRoundRect(inset, Math.max(0, rad - 2 * scale), Math.max(0, rad - 2 * scale), edge);
        paint.setShader(null);
        paint.setAlpha(255);
    }

    void clock(Canvas c, String value, float x, float baseline, Paint original, float darkness) {
        text.set(original);
        shapeMatrix.setScale(1, original.getTextSize());
        shapeMatrix.postTranslate(0, baseline - original.getTextSize() * 0.83f);
        LinearGradient gradient = darkness > 0.5f ? darkText : lightText;
        gradient.setLocalMatrix(shapeMatrix);
        text.setShader(gradient);
        c.drawText(value, x, baseline, text);
        text.setShader(null);
    }
}
