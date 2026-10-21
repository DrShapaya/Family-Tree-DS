package ru.drshapaya.androidft2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.widget.ImageView;

/** Requests only attached, visible thumbnails; detached/recycled cells release their requests. */
final class ThumbnailImageView extends ImageView {
    private final TreeMediaStore store;
    private final String mediaId, inline;
    private final Person crop;
    private final int target;
    private final Runnable ready = this::postInvalidateOnAnimation;
    private Bitmap displayed;
    private int displayedTarget;
    private final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF destination=new RectF();
    private final Path corners=new Path();

    public ThumbnailImageView(Context context) { this(context, null, "", "", 64, null); }

    ThumbnailImageView(Context context, TreeMediaStore store, String mediaId, String inline, int target, Person crop) {
        super(context);
        this.store = store; this.mediaId = mediaId; this.inline = inline; this.target = target; this.crop = crop;
        setScaleType(ScaleType.CENTER_CROP);
    }

    @Override protected void onDraw(Canvas canvas) {
        ThumbnailLoader.SHARED.begin(this);
        int pixels = getWidth()>0 && getHeight()>0 ? Math.max(getWidth(),getHeight()) : target;
        int requested = Math.max(16, Math.min(2048, (pixels+31)/32*32));
        Bitmap bitmap = displayed == null || displayedTarget<requested
            ? ThumbnailLoader.SHARED.get(this, ready, store, mediaId, inline, requested, 2) : displayed;
        if (bitmap != null && bitmap != displayed) {
            displayed = bitmap; displayedTarget=requested;
            setImageBitmap(bitmap);
        }
        int checkpoint=canvas.save();
        canvas.clipPath(corners);
        if (displayed!=null && crop!=null) {
            canvas.drawBitmap(displayed,AvatarTransform.sourceRect(displayed,crop),destination,bitmapPaint);
        } else super.onDraw(canvas);
        canvas.restoreToCount(checkpoint);
    }

    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) {
        super.onSizeChanged(width,height,oldWidth,oldHeight);
        destination.set(0,0,width,height);
        float radius=(crop==null?14f:16f)*getResources().getDisplayMetrics().density;
        corners.reset();corners.addRoundRect(destination,radius,radius,Path.Direction.CW);
    }

    @Override protected void onDetachedFromWindow() {
        ThumbnailLoader.SHARED.cancel(this);
        displayed = null; displayedTarget=0; setImageDrawable(null);
        super.onDetachedFromWindow();
    }
}
