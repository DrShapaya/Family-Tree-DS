package ru.drshapaya.androidft2;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/** Uses real bitmap pixels, startup activities and Android's recreation path. */
public final class StartupSplashTest extends InstrumentationTestCase {
    private MainActivity activity;
    private SharedPreferences prefs;
    private boolean hadTraining, training, hadTheme;
    private String cachedTheme;

    @Override protected void setUp() throws Exception {
        super.setUp();
        prefs = getInstrumentation().getTargetContext().getSharedPreferences("androidft-ui", Context.MODE_PRIVATE);
        hadTraining = prefs.contains("training_offer_handled"); training = prefs.getBoolean("training_offer_handled", false);
        hadTheme = prefs.contains("startup_theme"); cachedTheme = prefs.getString("startup_theme", "");
        prefs.edit().putBoolean("training_offer_handled", true).commit();
    }

    @Override protected void tearDown() throws Exception {
        if (activity != null) main(() -> activity.finish());
        SharedPreferences.Editor edit = prefs.edit();
        if (hadTraining) edit.putBoolean("training_offer_handled", training); else edit.remove("training_offer_handled");
        if (hadTheme) edit.putString("startup_theme", cachedTheme); else edit.remove("startup_theme");
        edit.commit();
        super.tearDown();
    }

    public void testParentsThenLinksThenChildrenAndFinalIconIsUnchanged() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        StartupSplashView.Artwork artwork = StartupSplashView.Artwork.load(context.getResources(), true);
        main(() -> {
            StartupSplashView view = new StartupSplashView(context, true, false);
            Bitmap result = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            try {
                view.setArtwork(artwork);
                Canvas canvas = new Canvas(result);
                view.drawLogo(canvas, .19f);
                assertTrue("Parents appear first", palePixels(result, 180, 150, 150, 80) > 300);
                assertTrue("Parent shadows arrive with the figures", shadowPixels(result, artwork.background, 180, 228, 155, 35) > 25);
                assertEquals("No children yet", 0, palePixels(result, 130, 300, 260, 80));
                view.drawLogo(canvas, .36f);
                assertTrue("Links grow after parents", palePixels(result, 150, 240, 210, 48) > 150);
                assertEquals("Last child has not appeared", 0, palePixels(result, 305, 310, 80, 65));
                view.drawLogo(canvas, .70f);
                capture(result, "startup-logo-assembly.png");
                int children = palePixels(result, 130, 300, 260, 80);
                assertTrue("All children appear: " + children, children > 1000);
                assertTrue("Child shadows are present before the original icon crossfade",
                    shadowPixels(result, artwork.background, 130, 370, 270, 42) > 100);
                result.eraseColor(Color.TRANSPARENT);
                view.drawLogo(canvas, 1f);
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false;
                Bitmap original = BitmapFactory.decodeResource(context.getResources(), R.drawable.app_icon, options);
                try {
                    int[] a = new int[512 * 512], b = new int[a.length];
                    original.getPixels(a, 0, 512, 0, 0, 512, 512); result.getPixels(b, 0, 512, 0, 0, 512, 512);
                    assertTrue("The final frame is the user's original icon", Arrays.equals(a, b));
                } finally { original.recycle(); }
                view.layout(0, 0, 600, 1000);
                for (boolean dark : new boolean[]{false, true}) {
                    view.setDark(dark);
                    Bitmap screenshot = Bitmap.createBitmap(600, 1000, Bitmap.Config.ARGB_8888);
                    try {
                        view.drawFrame(new Canvas(screenshot), .59f);
                        assertEquals(dark ? Color.rgb(16, 23, 27) : Color.rgb(248, 251, 252), screenshot.getPixel(5, 5));
                        capture(screenshot, dark ? "startup-dark.png" : "startup-light.png");
                    } finally { screenshot.recycle(); }
                }
            } finally { result.recycle(); view.dispose(); }
        });
    }

    public void testLauncherCompletesAndNewIntentDoesNotReplaySplash() throws Exception {
        launch();
        awaitDismissal();
        main(() -> {
            TreeState state = activity.state;
            View tree = activity.treeView;
            activity.onNewIntent(launcherIntent());
            assertSame(state, activity.state);
            assertSame(tree, activity.treeView);
            assertNull(activity.findViewById(android.R.id.content).findViewWithTag("startup.splash"));
            assertNull(activity.findViewById(android.R.id.content).findViewWithTag("startup.flying-icon"));
            assertEquals("The flight hands off to the real header icon", 1f, activity.headerIdentityIcon.getAlpha());
        });
    }

    public void testRecreationAndFileIntentsDoNotReplayAssembly() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        assertFalse(StartupSplash.shouldAnimate(context, launcherIntent(), new Bundle()));
        assertFalse(StartupSplash.shouldAnimate(context, new Intent(Intent.ACTION_VIEW), null));
        launch(); awaitDismissal();
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        try {
            main(() -> activity.recreate());
            MainActivity recreated = (MainActivity) monitor.waitForActivityWithTimeout(10_000);
            assertNotNull("Activity recreates", recreated);
            activity = recreated;
            TestStartup.await(getInstrumentation(), activity);
            awaitDismissal();
        } finally { getInstrumentation().removeMonitor(monitor); }
    }

    public void testCancelledStartupCanReceiveLateArtworkSafely() throws Exception {
        launch(); awaitDismissal();
        StartupSplashView.Artwork late = StartupSplashView.Artwork.load(activity.getResources(), true);
        main(() -> {
            StartupSplash splash = new StartupSplash(activity, new Bundle());
            splash.close();
            splash.setArtwork(late);
            assertTrue(late.original.isRecycled());
        });
    }

    public void testAnimationPolicyFollowsSystemMotionSetting() {
        Context context = getInstrumentation().getTargetContext();
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            assertEquals(android.animation.ValueAnimator.areAnimatorsEnabled(),
                StartupSplash.shouldAnimate(context, launcherIntent(), null));
        }
    }

    public void testBakedAreaLightShadowsHavePenumbraAndNoClippedEdges() {
        StartupSplashView.Artwork artwork = StartupSplashView.Artwork.load(getInstrumentation().getTargetContext().getResources(), true);
        try {
            for (int part = 0; part < artwork.shadows.length; part++) {
                Bitmap atlas = artwork.shadows[part];
                int width = atlas.getWidth(), height = atlas.getHeight() / 5;
                long[] opacity = new long[5];
                int[] footprint = new int[5];
                for (int frame = 0; frame < 5; frame++) {
                    int softPixels = 0;
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                        int alpha = Color.alpha(atlas.getPixel(x, frame * height + y));
                        if (x == 0 || y == 0 || x == width-1 || y == height-1)
                            assertEquals("No rectangular shadow cuts, part " + part, 0, alpha);
                        opacity[frame] += alpha;
                        if (alpha > 0) footprint[frame]++;
                        if (alpha > 0 && alpha < 40) softPixels++;
                    }
                    if (part != 2 || frame > 0) assertTrue("Soft penumbra", softPixels > 100);
                }
                if (part != 2) {
                    assertTrue("A raised figure casts a wider shadow", footprint[4] > footprint[0]);
                    assertTrue("Contact occlusion strengthens as the figure settles", opacity[0] > opacity[4]);
                }
            }
        } finally { artwork.close(); }
    }

    public void testOriginalIconCrossfadeNeverDimsTheBacking() throws Exception {
        StartupSplashView.Artwork artwork = StartupSplashView.Artwork.load(getInstrumentation().getTargetContext().getResources(), true);
        main(() -> {
            StartupSplashView view = new StartupSplashView(getInstrumentation().getTargetContext(), true, false);
            Bitmap frame = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            try {
                view.setArtwork(artwork);
                for (float progress : new float[]{.72f, .77f, .82f, .86f, .92f, .98f, 1f}) {
                    frame.eraseColor(Color.TRANSPARENT);
                    view.drawLogo(new Canvas(frame), progress);
                    assertTrue("No opacity dip at " + progress, Color.alpha(frame.getPixel(110, 250)) >= 254);
                }
            } finally { frame.recycle(); view.dispose(); }
        });
    }

    public void testSystemLaunchPaletteMatchesLoadedAppTheme() throws Exception {
        launch(); awaitDismissal();
        if (android.os.Build.VERSION.SDK_INT >= 31) main(() -> {
            boolean dark = "dark".equals(activity.theme);
            assertEquals(dark ? android.content.res.Configuration.UI_MODE_NIGHT_YES
                : android.content.res.Configuration.UI_MODE_NIGHT_NO,
                activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
            assertEquals(dark ? Color.rgb(16, 23, 27) : Color.rgb(248, 251, 252),
                activity.getResources().getColor(R.color.startup_background, activity.getTheme()));
        });
    }

    public void testTitleDoesNotFlashBeforeArtworkArrives() throws Exception {
        main(() -> {
            StartupSplashView view = new StartupSplashView(getInstrumentation().getTargetContext(), true, false);
            Bitmap pixels = Bitmap.createBitmap(600, 1000, Bitmap.Config.ARGB_8888);
            try {
                view.layout(0, 0, 600, 1000);
                view.draw(new Canvas(pixels));
                assertEquals(0f, view.assemblyProgress());
                int[] actual = new int[600 * 1000];
                pixels.getPixels(actual, 0, 600, 0, 0, 600, 1000);
                for (int pixel : actual) assertEquals("Only the backing before artwork", Color.rgb(248, 251, 252), pixel);
            } finally { pixels.recycle(); view.dispose(); }
        });
    }

    public void testTreeInstallsUnderCompletedAnimationWithoutDetachingSplash() throws Exception {
        launch(); awaitDismissal();
        StartupSplashView.Artwork artwork = StartupSplashView.Artwork.load(activity.getResources(), true);
        AtomicInteger detached = new AtomicInteger(), completed = new AtomicInteger(), frames = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        main(() -> {
            StartupSplash splash = new StartupSplash(activity, null);
            StartupSplashView view = activity.findViewById(android.R.id.content).findViewWithTag("startup.splash");
            ViewGroup parent = (ViewGroup) view.getParent();
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(View v) { }
                public void onViewDetachedFromWindow(View v) { detached.incrementAndGet(); }
            });
            float[] previous = {0f};
            view.getViewTreeObserver().addOnDrawListener(() -> {
                if (!view.isAttachedToWindow()) return;
                float progress = view.assemblyProgress();
                if (progress < previous[0]) failure.compareAndSet(null, new AssertionError("Assembly rewound"));
                previous[0] = progress;
                if (progress > 0 && progress < 1) frames.incrementAndGet();
            });
            splash.setArtwork(artwork);
            // A fast tree must not shorten or interrupt the chosen animation.
            splash.whenAssemblyFinished(() -> {
                try {
                    assertEquals(1f, view.assemblyProgress());
                    assertEquals(0, detached.get());
                    FrameLayout tree = new FrameLayout(activity);
                    assertTrue(splash.installContent(tree));
                    assertSame(parent, view.getParent());
                    assertSame(view, parent.getChildAt(parent.getChildCount() - 1));
                    splash.onTreeReady(null, () -> completed.incrementAndGet());
                } catch (Throwable error) { failure.set(error); splash.close(); completed.incrementAndGet(); }
            });
        });
        awaitCount(completed, 1);
        if (failure.get() != null) throw new AssertionError(failure.get());
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) assertTrue("Real intermediate frames: " + frames, frames.get() > 3);
        assertEquals("Only the final removal detaches the splash", 1, detached.get());
    }

    public void testExitCompletesOnceEvenWhenAnimationIsCancelledAndViewStopsDrawing() throws Exception {
        launch(); awaitDismissal();
        AtomicInteger completed = new AtomicInteger();
        AtomicReference<StartupSplashView> ref = new AtomicReference<>();
        main(() -> {
            StartupSplashView view = new StartupSplashView(activity, true, false);
            ref.set(view);
            ((ViewGroup) activity.findViewById(android.R.id.content)).addView(view);
            view.fadeOut(completed::incrementAndGet);
            view.animate().cancel();
            view.setVisibility(View.INVISIBLE);
        });
        try { awaitCount(completed, 1); SystemClock.sleep(300); assertEquals(1, completed.get()); }
        finally { main(() -> { ref.get().dispose(); ((ViewGroup) ref.get().getParent()).removeView(ref.get()); }); }
    }

    public void testIconShrinksAndMovesIntoHeaderAndCancellationRestoresTarget() throws Exception {
        launch(); awaitDismissal();
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        StartupSplashView.Artwork artwork = StartupSplashView.Artwork.load(activity.getResources(), true);
        AtomicReference<StartupSplashView> ref = new AtomicReference<>();
        AtomicReference<View> flight = new AtomicReference<>();
        AtomicInteger completed = new AtomicInteger();
        float[] start = new float[2];
        main(() -> {
            StartupSplashView view = new StartupSplashView(activity, true, false);
            ref.set(view);
            view.setArtwork(artwork);
            ((ViewGroup) activity.findViewById(android.R.id.content)).addView(view, new ViewGroup.LayoutParams(-1, -1));
            view.whenAssemblyFinished(() -> {
                view.finishInto(activity.headerIdentityIcon, completed::incrementAndGet);
                View icon = activity.findViewById(android.R.id.content).findViewWithTag("startup.flying-icon");
                flight.set(icon);
                start[0] = icon.getY(); start[1] = icon.getScaleX();
            });
        });
        try {
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (flight.get() == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(5);
            assertNotNull("Separate native flight layer", flight.get());
            SystemClock.sleep(180);
            main(() -> {
                assertTrue("Icon travels upwards", flight.get().getY() < start[0] - 30);
                assertTrue("Icon shrinks towards the header", flight.get().getScaleX() < start[1] - .1f);
                assertEquals("The header icon stays hidden until handoff", 0f, activity.headerIdentityIcon.getAlpha());
                ref.get().dispose();
                assertEquals("Cancellation restores the actual header icon", 1f, activity.headerIdentityIcon.getAlpha());
                assertNull(activity.findViewById(android.R.id.content).findViewWithTag("startup.flying-icon"));
            });
            SystemClock.sleep(600);
            assertEquals("A disposed transition never completes late", 0, completed.get());
        } finally {
            main(() -> { ref.get().dispose(); ((ViewGroup) ref.get().getParent()).removeView(ref.get()); });
        }
    }

    public void testColdLaunchFlightLandsAtActualHeaderAfterInsets() throws Exception {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        activity = (MainActivity) getInstrumentation().startActivitySync(launcherIntent().addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
        AtomicInteger closest = new AtomicInteger(Integer.MAX_VALUE);
        main(() -> activity.findViewById(android.R.id.content).getViewTreeObserver().addOnDrawListener(() -> {
            View icon = activity.findViewById(android.R.id.content).findViewWithTag("startup.flying-icon");
            View target = activity.headerIdentityIcon;
            if (icon == null || target == null || icon.getWidth() == 0) return;
            if (icon.getScaleX() > target.getWidth() / (float) icon.getWidth() * 1.15f) return;
            int[] from = new int[2], to = new int[2];
            icon.getLocationOnScreen(from); target.getLocationOnScreen(to);
            int error = Math.abs(from[0] - to[0]) + Math.abs(from[1] - to[1]);
            closest.set(Math.min(closest.get(), error));
        }));
        TestStartup.await(getInstrumentation(), activity);
        awaitDismissal();
        assertTrue("The flight reaches the real header, including status-bar inset: " + closest.get(), closest.get() < 20);
    }

    private void awaitCount(AtomicInteger count, int expected) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 6000;
        while (SystemClock.uptimeMillis() < deadline && count.get() < expected) SystemClock.sleep(25);
        assertEquals("Startup callback must complete exactly once", expected, count.get());
    }

    private Intent launcherIntent() {
        return new Intent(Intent.ACTION_MAIN, null, getInstrumentation().getTargetContext(), MainActivity.class)
            .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private void launch() {
        activity = (MainActivity) getInstrumentation().startActivitySync(launcherIntent().addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
        TestStartup.await(getInstrumentation(), activity);
    }

    private void awaitDismissal() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] dismissed = {false};
        while (SystemClock.uptimeMillis() < deadline) {
            main(() -> dismissed[0] = activity.findViewById(android.R.id.content).findViewWithTag("startup.splash") == null);
            if (dismissed[0]) return;
            SystemClock.sleep(25);
        }
        fail("Splash must release the loaded tree promptly");
    }

    private static int palePixels(Bitmap bitmap, int x, int y, int width, int height) {
        int count = 0;
        for (int py = y; py < y + height; py++) for (int px = x; px < x + width; px++) {
            int color = bitmap.getPixel(px, py);
            if (Color.red(color) > 160 && Color.green(color) > 160 && Color.blue(color) > 160) count++;
        }
        return count;
    }

    private static int shadowPixels(Bitmap bitmap, Bitmap backing, int x, int y, int width, int height) {
        int count = 0;
        for (int py = y; py < y + height; py++) for (int px = x; px < x + width; px++) {
            int color = bitmap.getPixel(px, py), base = backing.getPixel(px, py);
            if (Color.green(base) - Color.green(color) > 10 || Color.blue(base) - Color.blue(color) > 10) count++;
        }
        return count;
    }

    private void capture(Bitmap bitmap, String name) {
        File file = new File(getInstrumentation().getTargetContext().getExternalCacheDir(), name);
        try (FileOutputStream output = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
        catch (Exception error) { throw new AssertionError(error); }
    }

    private void main(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
