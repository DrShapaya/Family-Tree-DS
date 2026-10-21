package ru.drshapaya.androidft2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.view.animation.PathInterpolator;
import android.view.animation.LinearInterpolator;

/** Native Canvas counterpart of the approved logo-assembly preview. */
final class StartupSplashView extends View {
    private static final long ASSEMBLY_MS = 720L;
    private static final long EXIT_MS = 460L;
    // Padded tiles leave room for the area-light penumbra at every elevation.
    static final int[][] PART_BOUNDS = {{145,112,145,178}, {230,112,145,178},
        {120,210,285,145}, {95,263,150,165}, {185,263,150,165}, {275,263,150,165}};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PorterDuffXfermode multiplyAlpha = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private final PorterDuffXfermode addPremultiplied = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private final Path clip = new Path();
    private final Rect source = new Rect();
    private final RectF destination = new RectF();
    private final boolean animated;
    private final float animationScale;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Artwork artwork;
    private boolean dark, disposed, assemblyStarted, assemblyFinished, leaving, showLoading;
    private float progress;
    private ValueAnimator assemblyAnimator;
    private ImageView flyingIcon;
    private View destinationIcon;
    private float destinationAlpha;
    private Runnable assemblyCompletion, exitCompletion;
    private final Runnable finishAssembly = () -> finishAssembly();
    private final Runnable completeExit = this::completeExit;
    private final Runnable showLoadingLabel = () -> { showLoading = true; invalidate(); };
    private final Runnable startAssembly = () -> startAssembly();

    StartupSplashView(Context context, boolean animated, boolean dark) {
        super(context);
        this.animated = animated;
        animationScale = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        this.dark = dark;
        setTag("startup.splash");
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(AppLanguage.text(context, "Загрузка дерева…"));
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextAlign(Paint.Align.CENTER);
        clip.addRoundRect(0, 0, 512, 512, 123, 123, Path.Direction.CW);
        handler.postDelayed(showLoadingLabel, 1200L);
    }

    void setArtwork(Artwork value) {
        if (disposed) { if (value != null) value.close(); return; }
        artwork = value;
        handler.postDelayed(finishAssembly, animationTimeout(ASSEMBLY_MS));
        invalidate();
    }

    void setDark(boolean value) { dark = value; invalidate(); }

    void whenAssemblyFinished(Runnable callback) {
        if (disposed) return;
        if (assemblyFinished || !animated) callback.run();
        else assemblyCompletion = callback;
    }

    private void startAssembly() {
        if (disposed || assemblyStarted || assemblyFinished || artwork == null) return;
        assemblyStarted = true;
        if (!animated) { finishAssembly(); return; }
        assemblyAnimator = ValueAnimator.ofFloat(0f, 1f);
        assemblyAnimator.setDuration(ASSEMBLY_MS);
        assemblyAnimator.setInterpolator(new LinearInterpolator());
        assemblyAnimator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        assemblyAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { finishAssembly(); }
        });
        assemblyAnimator.start();
    }

    private void finishAssembly() {
        if (disposed || assemblyFinished) return;
        assemblyFinished = true;
        progress = 1f;
        handler.removeCallbacks(finishAssembly);
        if (assemblyAnimator != null) assemblyAnimator.cancel();
        invalidate();
        Runnable callback = assemblyCompletion;
        assemblyCompletion = null;
        // Let the final artwork reach the screen before constructing the tree UI.
        if (callback != null) handler.post(() -> {
            if (!disposed) postOnAnimation(() -> { if (!disposed) callback.run(); });
        });
    }

    void finishInto(View target, Runnable callback) {
        if (disposed || leaving) return;
        leaving = true;
        exitCompletion = callback;
        handler.removeCallbacks(showLoadingLabel);
        boolean fly = animated && artwork != null && target != null && target.isShown()
            && target.getWidth() > 0 && target.getHeight() > 0 && getParent() instanceof ViewGroup;
        if (fly) {
            ViewGroup parent = (ViewGroup) getParent();
            int[] origin = new int[2], sourceLocation = new int[2], targetLocation = new int[2];
            parent.getLocationOnScreen(origin);
            getLocationOnScreen(sourceLocation);
            target.getLocationOnScreen(targetLocation);
            float size = logoSize();
            float x = sourceLocation[0] - origin[0] + getWidth() / 2f - size / 2f;
            float y = sourceLocation[1] - origin[1] + getHeight() * .42f - size / 2f;
            flyingIcon = new ImageView(getContext());
            flyingIcon.setTag("startup.flying-icon");
            flyingIcon.setImageBitmap(artwork.original);
            flyingIcon.setScaleType(ImageView.ScaleType.FIT_XY);
            flyingIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            flyingIcon.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getWidth() * 123f / 512f);
                }
            });
            flyingIcon.setClipToOutline(true);
            parent.addView(flyingIcon, new ViewGroup.LayoutParams(Math.round(size), Math.round(size)));
            // Establish RenderNode bounds before native property animation;
            // otherwise the first layout can change the moving layer's origin.
            flyingIcon.layout(0, 0, Math.round(size), Math.round(size));
            flyingIcon.setX(x); flyingIcon.setY(y);
            flyingIcon.setPivotX(0f); flyingIcon.setPivotY(0f);
            destinationIcon = target;
            destinationAlpha = target.getAlpha();
            target.setAlpha(0f);
            invalidate();
            flyingIcon.animate().x(targetLocation[0] - origin[0]).y(targetLocation[1] - origin[1])
                .scaleX(target.getWidth() / (float) Math.round(size))
                .scaleY(target.getHeight() / (float) Math.round(size))
                .setDuration(EXIT_MS).setInterpolator(new PathInterpolator(.22f, .75f, .2f, 1f))
                .withLayer().withEndAction(completeExit).start();
        }
        animate().alpha(0f).setDuration(animated ? EXIT_MS : 0L)
            .withLayer().withEndAction(fly ? null : completeExit).start();
        handler.postDelayed(completeExit, animationTimeout(EXIT_MS));
    }

    void fadeOut(Runnable callback) { finishInto(null, callback); }

    private void clearFlight() {
        if (destinationIcon != null) { destinationIcon.setAlpha(destinationAlpha); destinationIcon = null; }
        if (flyingIcon != null) {
            flyingIcon.animate().cancel();
            flyingIcon.setImageDrawable(null);
            if (flyingIcon.getParent() instanceof ViewGroup) ((ViewGroup) flyingIcon.getParent()).removeView(flyingIcon);
            flyingIcon = null;
        }
    }

    private void completeExit() {
        if (disposed) return;
        Runnable callback = exitCompletion;
        exitCompletion = null;
        handler.removeCallbacks(completeExit);
        clearFlight();
        if (callback != null) callback.run();
    }

    float assemblyProgress() { return artwork == null ? 0f : (!animated ? 1f : progress); }

    @Override protected void onDraw(Canvas canvas) {
        if (disposed) return;
        drawFrame(canvas, assemblyProgress());
        // Start on the frame after the first complete, textured logo backing.
        if (artwork != null && !assemblyStarted) postOnAnimation(startAssembly);
    }

    void drawFrame(Canvas canvas, float assembly) {
        int backing = dark ? Color.rgb(16, 23, 27) : Color.rgb(248, 251, 252);
        paint.setColor(backing);
        paint.setAlpha(255);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setAlpha(255);
        float size = logoSize();
        float centerX = getWidth() / 2f, centerY = getHeight() * .42f;
        float drawnSize = size * (.96f + .04f * ease(assembly));
        float left = centerX - drawnSize / 2f, top = centerY - drawnSize / 2f;
        int save = canvas.save();
        canvas.translate(left, top);
        canvas.scale(drawnSize / 512f, drawnSize / 512f);
        canvas.clipPath(clip);
        if (flyingIcon == null) drawLogo(canvas, assembly);
        canvas.restoreToCount(save);
        text.setColor(dark ? Color.rgb(237, 245, 245) : Color.rgb(28, 34, 38));
        text.setTextSize(dp(23));
        text.setAlpha(artwork == null ? 0 : Math.round(255 * phase(assembly, .35f, .6f)));
        canvas.drawText("Family Tree DS", centerX, centerY + size / 2f + dp(36), text);
        if (!this.leaving && showLoading) {
            text.setTextSize(dp(13));
            text.setAlpha(180);
            canvas.drawText(AppLanguage.text(getContext(), "Загрузка дерева…"), centerX,
                centerY + size / 2f + dp(65), text);
        }
        text.setAlpha(255);
    }

    void drawLogo(Canvas canvas, float progress) {
        if (artwork == null) return;
        if (!animated || artwork.parts == null || progress >= 1f) {
            canvas.drawBitmap(artwork.original, 0, 0, paint);
            return;
        }
        float blend = phase(progress, .72f, 1f);
        int assemblyLayer = blend > 0f ? canvas.saveLayer(0, 0, 512, 512, null) : -1;
        canvas.drawBitmap(artwork.background, 0, 0, paint);
        // Shadows stay on the receiving plane. As the raised figures settle,
        // their area-light penumbra tightens and contact occlusion strengthens.
        drawShadow(canvas, 0, phase(progress, .025f, .19f), .88f);
        drawShadow(canvas, 1, phase(progress, .09f, .26f), .88f);
        drawShadow(canvas, 2, phase(progress, .20f, .49f), 1f);
        drawShadow(canvas, 3, phase(progress, .36f, .57f), .72f);
        drawShadow(canvas, 4, phase(progress, .43f, .64f), .72f);
        drawShadow(canvas, 5, phase(progress, .50f, .71f), .72f);
        drawPart(canvas, 0, phase(progress, .025f, .19f), 18, .88f);
        drawPart(canvas, 1, phase(progress, .09f, .26f), 18, .88f);
        int save = canvas.save();
        canvas.clipRect(120, 232, 405, 232 + 67 * phase(progress, .20f, .49f));
        drawPart(canvas, 2, 1, 0, 1);
        canvas.restoreToCount(save);
        drawPart(canvas, 3, phase(progress, .36f, .57f), 20, .72f);
        drawPart(canvas, 4, phase(progress, .43f, .64f), 20, .72f);
        drawPart(canvas, 5, phase(progress, .50f, .71f), 20, .72f);
        if (assemblyLayer != -1) {
            // Crossfade premultiplied colors with weights summing to one.
            // Two SRC_OVER fades would dim the entire icon midway through.
            paint.setAlpha(Math.round(255 * (1f - blend)));
            paint.setXfermode(multiplyAlpha);
            canvas.drawRect(0, 0, 512, 512, paint);
            paint.setAlpha(Math.round(255 * blend));
            paint.setXfermode(addPremultiplied);
            canvas.drawBitmap(artwork.original, 0, 0, paint);
            paint.setXfermode(null);
            canvas.restoreToCount(assemblyLayer);
        }
        paint.setAlpha(255);
    }

    private void drawPart(Canvas canvas, int part, float amount, float rise, float scale) {
        if (amount <= 0) return;
        int[] bounds = PART_BOUNDS[part];
        int x = bounds[0], y = bounds[1], width = bounds[2], height = bounds[3];
        int save = canvas.save();
        canvas.translate(x + width / 2f, y + height / 2f - rise * (1f - amount));
        float resolvedScale = scale + (1f - scale) * amount;
        canvas.scale(resolvedScale, resolvedScale);
        source.set(0, 0, width, height);
        destination.set(-width / 2f, -height / 2f, width / 2f, height / 2f);
        paint.setAlpha(Math.round(255 * amount));
        canvas.drawBitmap(artwork.parts[part], source, destination, paint);
        paint.setAlpha(255);
        canvas.restoreToCount(save);
    }

    private void drawShadow(Canvas canvas, int part, float amount, float scale) {
        if (amount <= 0) return;
        int[] bounds = PART_BOUNDS[part];
        int width = bounds[2], height = bounds[3];
        float frame = (part == 2 ? amount : 1f - amount) * 4f;
        int low = Math.min(4, (int) frame), high = Math.min(4, low + 1);
        float fraction = frame - low;
        int save = canvas.save();
        canvas.translate(bounds[0] + width / 2f, bounds[1] + height / 2f);
        float resolvedScale = scale + (1f - scale) * amount;
        canvas.scale(resolvedScale, resolvedScale);
        destination.set(-width / 2f, -height / 2f, width / 2f, height / 2f);
        source.set(0, low * height, width, (low + 1) * height);
        paint.setAlpha(Math.round(255 * amount * (1f - fraction)));
        canvas.drawBitmap(artwork.shadows[part], source, destination, paint);
        if (fraction > 0) {
            source.set(0, high * height, width, (high + 1) * height);
            paint.setAlpha(Math.round(255 * amount * fraction));
            canvas.drawBitmap(artwork.shadows[part], source, destination, paint);
        }
        paint.setAlpha(255);
        canvas.restoreToCount(save);
    }

    void dispose() {
        disposed = true;
        assemblyCompletion = null;
        exitCompletion = null;
        handler.removeCallbacksAndMessages(null);
        removeCallbacks(startAssembly);
        if (assemblyAnimator != null) {
            assemblyAnimator.removeAllListeners();
            assemblyAnimator.removeAllUpdateListeners();
            assemblyAnimator.cancel();
        }
        animate().cancel();
        clearFlight();
        if (artwork != null) { artwork.close(); artwork = null; }
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float logoSize() { return Math.min(dp(166), Math.min(getWidth() * .54f, getHeight() * .36f)); }
    // Timers are independent of drawing, while respecting the user's motion speed.
    private long animationTimeout(long duration) { return Math.round(duration * animationScale) + 1500L; }
    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static float ease(float value) { float inverse = 1f - clamp(value); return 1f - inverse * inverse * inverse; }
    private static float phase(float value, float start, float end) { return ease((value - start) / (end - start)); }

    static final class Artwork implements AutoCloseable {
        final Bitmap original, background;
        final Bitmap[] parts, shadows;

        private Artwork(Bitmap original, Bitmap background, Bitmap[] parts, Bitmap[] shadows) {
            this.original = original; this.background = background; this.parts = parts; this.shadows = shadows;
            // All layers use the same 512px coordinate space. Android must not
            // apply resource-density scaling on top of our Canvas transform.
            original.setDensity(Bitmap.DENSITY_NONE);
            if (background != null) background.setDensity(Bitmap.DENSITY_NONE);
            original.prepareToDraw();
            if (background != null) background.prepareToDraw();
            if (parts != null) for (Bitmap part : parts) { part.setDensity(Bitmap.DENSITY_NONE); part.prepareToDraw(); }
            if (shadows != null) for (Bitmap shadow : shadows) { shadow.setDensity(Bitmap.DENSITY_NONE); shadow.prepareToDraw(); }
        }

        static Artwork load(Resources resources, boolean animated) {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false;
            Bitmap original = BitmapFactory.decodeResource(resources, R.drawable.app_icon, options);
            if (original == null) return null;
            if (original.getWidth() != 512 || original.getHeight() != 512) {
                Bitmap scaled = Bitmap.createScaledBitmap(original, 512, 512, true);
                if (scaled != original) original.recycle();
                original = scaled;
            }
            if (!animated) return new Artwork(original, null, null, null);
            return new Artwork(original,
                BitmapFactory.decodeResource(resources, R.drawable.startup_logo_background, options),
                new Bitmap[]{BitmapFactory.decodeResource(resources, R.drawable.startup_part_parent_left, options),
                    BitmapFactory.decodeResource(resources, R.drawable.startup_part_parent_right, options),
                    BitmapFactory.decodeResource(resources, R.drawable.startup_part_links, options),
                    BitmapFactory.decodeResource(resources, R.drawable.startup_part_child_left, options),
                    BitmapFactory.decodeResource(resources, R.drawable.startup_part_child_middle, options),
                    BitmapFactory.decodeResource(resources, R.drawable.startup_part_child_right, options)},
                new Bitmap[]{BitmapFactory.decodeResource(resources, R.drawable.startup_shadow_parent_left, options),
                    BitmapFactory.decodeResource(resources, R.drawable.startup_shadow_parent_right, options),
                    BitmapFactory.decodeResource(resources, R.drawable.startup_shadow_links, options),
                    BitmapFactory.decodeResource(resources, R.drawable.startup_shadow_child_left, options),
                    BitmapFactory.decodeResource(resources, R.drawable.startup_shadow_child_middle, options),
                    BitmapFactory.decodeResource(resources, R.drawable.startup_shadow_child_right, options)});
        }

        @Override public void close() {
            if (!original.isRecycled()) original.recycle();
            if (background != null && !background.isRecycled()) background.recycle();
            if (parts != null) for (Bitmap part : parts) if (!part.isRecycled()) part.recycle();
            if (shadows != null) for (Bitmap shadow : shadows) if (!shadow.isRecycled()) shadow.recycle();
        }
    }
}
