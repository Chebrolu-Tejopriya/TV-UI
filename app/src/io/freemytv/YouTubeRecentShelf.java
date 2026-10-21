package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Three native glass cards shown when the YouTube dock tile is focused. */
final class YouTubeRecentShelf {
    private final YouTubeHistory history;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF[] cards = {new RectF(), new RectF(), new RectF()};
    private final RectF image = new RectF(), inset = new RectF();
    private final Path triangle = new Path();
    private final Matrix matrix = new Matrix();
    private final Map<Bitmap, BitmapShader> shaders = new WeakHashMap<>();
    private TextPaint title, detail;
    private Paint heading, hint;
    private float scale;

    YouTubeRecentShelf(YouTubeHistory history) { this.history = history; }

    void layout(float k) {
        scale = k;
        float width = 360, gap = 32, left = (1920-3*width-2*gap)/2;
        for (int i = 0; i < 3; i++) cards[i].set((left+(width+gap)*i)*k, 350*k, (left+(width+gap)*i+width)*k, 630*k);
        heading = Ui.text(Ui.semibold, 32*k, 0xFFF5F5F7);
        title = new TextPaint(Ui.text(Ui.semibold, 23*k, 0xFFF5F5F7));
        detail = new TextPaint(Ui.text(Ui.medium, 19*k, 0xB8FFFFFF));
        hint = Ui.text(Ui.medium, 20*k, 0xE8FFFFFF);
        hint.setTextAlign(Paint.Align.CENTER);
    }

    int size() { return history.videos().size(); }
    RectF bounds(int i) { return cards[Math.max(0, Math.min(2, i))]; }

    String description(int i) {
        List<YouTubeHistory.Video> videos = history.videos();
        if (i < 0 || i >= videos.size()) return "";
        YouTubeHistory.Video v = videos.get(i);
        return v.title + (v.author.isEmpty() ? "" : ", " + v.author) + ". "
                + (v.videoId.isEmpty() ? Ui.tr("Buscar no YouTube", "Search in YouTube") : Ui.tr("Reproduzir no YouTube", "Play in YouTube"));
    }

    void draw(Context context, Canvas c, Scene scene, int focused) {
        if (heading == null) return;
        float k = scale;
        List<YouTubeHistory.Video> videos = history.videos();
        // BitmapShader retains its bitmap: remove stale values as well as stale keys.
        shaders.keySet().removeIf(bitmap -> {
            for (YouTubeHistory.Video v : videos) if (v.artwork == bitmap) return false;
            return true;
        });
        c.drawText(Ui.tr("Reproduzidos recentemente nesta TV", "Recently played on this TV"), cards[0].left, 306*k, heading);
        for (int i = 0; i < 3; i++) {
            RectF card = cards[i];
            if (Prefs.liquidGlass()) scene.cachedLiquid(c, "youtube-recent-"+i, card, card, 20*k, 0xFF101A2A, 0.6f, 0.02f, 0, 1);
            paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(0xE6101A2A);
            inset.set(card); inset.inset(2*k,2*k); c.drawRoundRect(inset,18*k,18*k,paint);
            image.set(card.left+3*k,card.top+3*k,card.right-3*k,card.top+202*k);
            YouTubeHistory.Video video = i < videos.size() ? videos.get(i) : null;
            if (video != null && video.artwork != null) drawArtwork(c, video.artwork, image);
            else {
                paint.setShader(null); paint.setColor(0x38284763); c.drawRoundRect(image,17*k,17*k,paint);
                paint.setColor(video == null ? 0x5CFFFFFF : 0xFFFF0033);
                c.drawCircle(image.centerX(),image.centerY(),28*k,paint);
                paint.setColor(0xFFF5F5F7);
                float x=image.centerX(),y=image.centerY(); triangle.reset(); triangle.moveTo(x-7*k,y-12*k);
                triangle.lineTo(x+13*k,y); triangle.lineTo(x-7*k,y+12*k); triangle.close(); c.drawPath(triangle,paint);
            }
            String name = video == null ? Ui.tr("Seu pr\u00f3ximo v\u00eddeo", "Your next video") : video.title;
            String author = video == null ? Ui.tr("Aparecer\u00e1 aqui ap\u00f3s assistir", "Appears here after you watch") : video.author;
            c.drawText(TextUtils.ellipsize(name,title,324*k,TextUtils.TruncateAt.END).toString(),card.left+18*k,card.top+236*k,title);
            c.drawText(TextUtils.ellipsize(author,detail,324*k,TextUtils.TruncateAt.END).toString(),card.left+18*k,card.top+262*k,detail);
            if (focused == i && video != null) {
                paint.setShader(null); paint.setColor(0xFFF5F5F7); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3*k);
                c.drawRoundRect(card,20*k,20*k,paint); paint.setStyle(Paint.Style.FILL);
                String action = video.videoId.isEmpty() ? Ui.tr("OK \u00b7 Buscar no YouTube", "OK \u00b7 Search in YouTube") : Ui.tr("OK \u00b7 Reproduzir no YouTube", "OK \u00b7 Play in YouTube");
                c.drawText(action,card.centerX(),card.bottom+38*k,hint);
            }
        }
    }

    private void drawArtwork(Canvas c, Bitmap bitmap, RectF rect) {
        BitmapShader shader = shaders.get(bitmap);
        if (shader == null) { shader = new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP); shaders.put(bitmap,shader); }
        float s = Math.max(rect.width()/bitmap.getWidth(),rect.height()/bitmap.getHeight());
        matrix.setScale(s,s);
        matrix.postTranslate(rect.centerX()-bitmap.getWidth()*s/2,rect.centerY()-bitmap.getHeight()*s/2);
        shader.setLocalMatrix(matrix); paint.setColor(0xFFFFFFFF); paint.setShader(shader);
        c.drawRoundRect(rect,17*scale,17*scale,paint); paint.setShader(null);
    }
}
