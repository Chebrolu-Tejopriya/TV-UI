package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import java.util.Locale;

/** Compact header island; remote focus expands Spotify's own session controls. */
final class SpotifyWidget {
    private final SpotifyPlayback playback;
    private final Spring expansion = new Spring(0.32f, 0.9f, 0);
    private final RectF panel=new RectF(), art=new RectF(), inner=new RectF();
    private final RectF[] controls={new RectF(),new RectF(),new RectF()};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path triangle=new Path();
    private final Matrix matrix=new Matrix();
    private Bitmap lastArt;
    private BitmapShader artShader;
    private TextPaint title,artist;
    private Paint small;
    private float scale,headerLeft,headerRight;

    SpotifyWidget(SpotifyPlayback playback) { this.playback=playback; }

    void layout(float k) {
        scale=k;
        title=new TextPaint(Ui.text(Ui.semibold,24*k,0xFFF5F5F7));
        artist=new TextPaint(Ui.text(Ui.medium,20*k,0xADFFFFFF));
        small=Ui.numbers(Ui.medium,18*k,0xB8FFFFFF);
        headerBounds(700*k,1548*k);
    }

    void headerBounds(float left,float right) {
        headerLeft=left; headerRight=Math.max(left+100*scale,right);
        geometry();
    }

    void expanded(boolean on,boolean snap) {
        expansion.target=on?1:0;
        if(snap)expansion.snap(expansion.target);
        geometry();
    }

    boolean step(float dt) {
        boolean moving=expansion.step(dt);
        if(moving)geometry();
        return moving;
    }

    private void geometry() {
        float k=scale,amount=Ui.clamp(expansion.value),center=(headerLeft+headerRight)/2;
        float width=Math.min((600+160*amount)*k,headerRight-headerLeft);
        panel.set(center-width/2,61*k,center+width/2,(145+76*amount)*k);
        art.set(panel.left+19*k,75*k,panel.left+75*k,131*k);
        for(int i=0;i<3;i++)controls[i].set(center+(i-1)*78*k-25*k,157*k,center+(i-1)*78*k+25*k,207*k);
    }

    boolean visible() { return Prefs.spotifyWidget()&&(!playback.hasAccess()||playback.present()); }
    RectF bounds(int i) { return expansion.target==0?panel:controls[Math.max(0,Math.min(2,i))]; }

    String controlText(int i) {
        if(!playback.hasAccess())return Ui.tr("Ativar controles do Spotify","Enable Spotify controls");
        String action=i==0?Ui.tr("Faixa anterior","Previous track"):i==2?Ui.tr("Pr\u00f3xima faixa","Next track")
                :playback.playing()||playback.buffering()?Ui.tr("Pausar","Pause"):Ui.tr("Reproduzir","Play");
        return action+", Spotify, "+playback.title()+(playback.artist().isEmpty()?"":", "+playback.artist())
                +(playback.enabled(i)?"":", "+Ui.tr("Indispon\u00edvel","Unavailable"));
    }

    void draw(Canvas c,Scene scene,int focused) {
        if(title==null||!visible())return;
        float k=scale,amount=Ui.clamp(expansion.value),radius=(42-6*amount)*k;
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x24000000);inner.set(panel);inner.offset(0,7*k);c.drawRoundRect(inner,radius,radius,paint);
        paint.setColor(0xFA050607);c.drawRoundRect(panel,radius,radius,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f*k);paint.setColor(0x30FFFFFF);
        c.drawRoundRect(panel,radius,radius,paint);paint.setStyle(Paint.Style.FILL);
        c.save();c.clipRect(panel);
        Bitmap artwork=playback.artwork();
        if(artwork!=null)drawArtwork(c,artwork);
        else {
            paint.setColor(0xFF1ED760);c.drawCircle(art.centerX(),art.centerY(),22*k,paint);
            paint.setColor(0xFF051209);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3*k);paint.setStrokeCap(Paint.Cap.ROUND);
            for(int i=0;i<3;i++) {
                inner.set(art.centerX()-14*k,art.centerY()+(-12+i*7)*k,art.centerX()+14*k,art.centerY()+(4+i*7)*k);
                c.drawArc(inner,212,116,false,paint);
            }
            paint.setStyle(Paint.Style.FILL);paint.setStrokeCap(Paint.Cap.BUTT);
        }
        float textX=art.right+17*k,textWidth=Math.max(40*k,panel.right-textX-65*k);
        String song=playback.hasAccess()?playback.title():Ui.tr("Ativar controles do Spotify","Enable Spotify controls");
        String by=playback.hasAccess()?playback.artist():Ui.tr("OK para abrir os ajustes","OK to open settings");
        if(playback.hasAccess()&&!playback.playing()) {
            String status=playback.buffering()?Ui.tr("Conectando","Connecting"):Ui.tr("Pausado","Paused");
            by=by.isEmpty()?status:by+" \u00b7 "+status;
        }
        c.drawText(TextUtils.ellipsize(song,title,textWidth,TextUtils.TruncateAt.END).toString(),textX,99*k,title);
        c.drawText(TextUtils.ellipsize(by,artist,textWidth,TextUtils.TruncateAt.END).toString(),textX,125*k,artist);
        drawBars(c,panel.right-34*k,103*k);
        float content=Ui.clamp((amount-0.25f)/0.75f);
        if(content>0.001f) {
            if(playback.hasAccess()) {
                long duration=playback.duration(),position=playback.position();
                float left=panel.left+28*k,right=panel.right-28*k;
                paint.setColor(Ui.alpha(0x38FFFFFF,content));c.drawRoundRect(left,146*k,right,149*k,1.5f*k,1.5f*k,paint);
                if(duration>0) {
                    paint.setColor(Ui.alpha(0xFF1ED760,content));
                    c.drawRoundRect(left,146*k,left+(right-left)*Math.min(1,position/(float)duration),149*k,1.5f*k,1.5f*k,paint);
                }
                small.setAlpha(Math.round(184*content));
                c.drawText(time(position),left,188*k,small);
                String end=duration>0?time(duration):"--:--";
                c.drawText(end,right-small.measureText(end),188*k,small);
                for(int i=0;i<3;i++)drawControl(c,i,focused==i,playback.enabled(i),content);
            } else drawControl(c,1,focused==1,true,content);
        }
        c.restore();
    }

    private void drawArtwork(Canvas c,Bitmap bitmap) {
        if(lastArt!=bitmap) {lastArt=bitmap;artShader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);}
        float s=Math.max(art.width()/bitmap.getWidth(),art.height()/bitmap.getHeight());
        matrix.setScale(s,s);matrix.postTranslate(art.centerX()-bitmap.getWidth()*s/2,art.centerY()-bitmap.getHeight()*s/2);
        artShader.setLocalMatrix(matrix);paint.setColor(0xFFFFFFFF);paint.setShader(artShader);
        c.drawRoundRect(art,12*scale,12*scale,paint);paint.setShader(null);
    }

    private void drawBars(Canvas c,float x,float y) {
        float k=scale;
        paint.setColor(playback.playing()?0xFF1ED760:0xB31ED760);
        if(playback.playing()) {
            float seconds=SystemClock.uptimeMillis()/1000f;
            for(int i=0;i<5;i++) {
                float h=(6+17*(0.5f+0.5f*(float)Math.sin(seconds*(4.3f+i*0.6f)+i*1.7f)))*k;
                float left=x+(i-2)*5.5f*k-1.5f*k;
                c.drawRoundRect(left,y-h/2,left+3*k,y+h/2,1.5f*k,1.5f*k,paint);
            }
        } else {
            c.drawRoundRect(x-6*k,y-8*k,x-2*k,y+8*k,k,k,paint);
            c.drawRoundRect(x+2*k,y-8*k,x+6*k,y+8*k,k,k,paint);
        }
    }

    private static String time(long milliseconds) {
        long seconds=Math.max(0,milliseconds)/1000;
        return String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }

    private void drawControl(Canvas c,int i,boolean focused,boolean enabled,float alpha) {
        float k=scale;RectF button=controls[i];
        paint.setColor(Ui.alpha(focused?0xFFFFFFFF:enabled?0x28FFFFFF:0x14FFFFFF,alpha));c.drawRoundRect(button,25*k,25*k,paint);
        paint.setColor(Ui.alpha(focused?0xFF101A2A:enabled?0xFFF5F5F7:0x59FFFFFF,alpha));
        float x=button.centerX(),y=button.centerY();
        if(!playback.hasAccess()) {
            c.drawRect(x-10*k,y-2*k,x+10*k,y+2*k,paint);
            triangle.reset();triangle.moveTo(x+3*k,y-9*k);triangle.lineTo(x+12*k,y);triangle.lineTo(x+3*k,y+9*k);triangle.close();c.drawPath(triangle,paint);
        } else if(i==1&&(playback.playing()||playback.buffering())) {
            c.drawRoundRect(x-8*k,y-10*k,x-3*k,y+10*k,k,k,paint);c.drawRoundRect(x+3*k,y-10*k,x+8*k,y+10*k,k,k,paint);
        } else {
            float direction=i==0?-1:1;
            triangle.reset();triangle.moveTo(x-7*k*direction,y-10*k);triangle.lineTo(x+9*k*direction,y);
            triangle.lineTo(x-7*k*direction,y+10*k);triangle.close();c.drawPath(triangle,paint);
            if(i!=1)c.drawRect(x+(i==0?-12:10)*k,y-10*k,x+(i==0?-9:13)*k,y+10*k,paint);
        }
    }
}
