package ru.drshapaya.androidft2;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import java.util.concurrent.atomic.AtomicReference;

/** Actual panel switching, interrupted animations and transformed nav bounds. */
public final class PanelTransitionsTest extends InstrumentationTestCase {
    private MainActivity activity;
    private SharedPreferences prefs;
    private boolean hadTraining, training;

    @Override protected void setUp() throws Exception {
        super.setUp();
        Context context = getInstrumentation().getTargetContext();
        prefs = context.getSharedPreferences("androidft-ui", Context.MODE_PRIVATE);
        hadTraining = prefs.contains("training_offer_handled");
        training = prefs.getBoolean("training_offer_handled", false);
        prefs.edit().putBoolean("training_offer_handled", true).commit();
        activity = (MainActivity) getInstrumentation().startActivitySync(
            new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        TestStartup.await(getInstrumentation(), activity);
        main(() -> {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            activity.focusTree = false;
            activity.showPanel("");
        });
        awaitOrientation(false);
        SystemClock.sleep(600);
    }

    @Override protected void tearDown() throws Exception {
        if (activity != null) main(() -> activity.finish());
        SharedPreferences.Editor edit = prefs.edit();
        if (hadTraining) edit.putBoolean("training_offer_handled", training);
        else edit.remove("training_offer_handled");
        edit.commit();
        super.tearDown();
    }

    public void testOtherMenusAnimateAndRefreshDoesNotReplayEntrance() throws Exception {
        for (String name : new String[]{"view", "card", "links", "guides", "branch", "files", "settings", "people"}) {
            main(() -> {
                activity.showPanel(name);
                View panel = panel(name);
                assertEquals(name, View.VISIBLE, panel.getVisibility());
                if (PanelTransitions.enabled(panel)) {
                    assertEquals(name + " starts transparent", 0f, panel.getAlpha());
                    assertTrue(name + " starts below its final position", panel.getTranslationY() > 0f);
                }
            });
            SystemClock.sleep(550);
            main(() -> {
                assertSettled(panel(name));
                activity.showPanel(name);
                assertSettled(panel(name));
                activity.showPanel("");
                assertEquals(View.GONE, panel(name).getVisibility());
                assertSettled(panel(name));
            });
        }
    }

    public void testRapidSwitchingAndMoreCloseReopenLeaveNoInvisibleMenu() throws Exception {
        main(() -> activity.showPanel("more"));
        SystemClock.sleep(50);
        main(() -> activity.showPanel(""));
        SystemClock.sleep(40);
        main(() -> activity.showPanel("more"));
        SystemClock.sleep(650);
        main(() -> {
            assertEquals(View.VISIBLE, activity.morePanel.getVisibility());
            assertSettled(activity.morePanel);
            ViewGroup rows = (ViewGroup) activity.trainingMoreMenuTarget;
            for (int i = 0; i < rows.getChildCount(); i++) assertSettled(rows.getChildAt(i));
            activity.showPanel("more");
            assertSettled(activity.morePanel);
            activity.showPanel("card");
            activity.showPanel("people");
            activity.showPanel("view");
            activity.showPanel("links");
        });
        SystemClock.sleep(600);
        main(() -> {
            assertEquals("links", activity.activePanel);
            assertEquals(View.VISIBLE, activity.linksPanel.getVisibility());
            assertSettled(activity.linksPanel);
            for (View hidden : new View[]{activity.cardPanel, activity.peoplePanel, activity.viewPanel, activity.morePanel}) {
                assertEquals(View.GONE, hidden.getVisibility());
                assertSettled(hidden);
            }
        });
    }

    public void testActivePeopleButtonStaysBelowPanelBorder() throws Exception {
        main(() -> activity.showPanel("people"));
        SystemClock.sleep(550);
        main(() -> {
            int[] button = new int[2], bar = new int[2];
            activity.peopleNav.getLocationOnScreen(button);
            activity.bottomNavigation.getLocationOnScreen(bar);
            assertTrue("Scaled button must clear the top divider: " + button[1] + " vs " + bar[1],
                button[1] > bar[1] + activity.dp(1));
            assertTrue("People retains its distinct gradient", activity.peopleNav.getBackground()
                instanceof android.graphics.drawable.GradientDrawable);
        });
    }

    public void testScrimStaysStillAndAddButtonNeverFlashesAcrossMenus() throws Exception {
        main(() -> {
            activity.showPanel("more");
            assertEquals(0f, activity.morePanel.getTranslationY());
            assertEquals(0f, activity.morePanel.getTranslationX());
            View sheet = ((ViewGroup) activity.morePanel).getChildAt(0);
            if (PanelTransitions.enabled(sheet)) assertTrue(sheet.getTranslationY() > 0f);
            assertEquals(View.GONE, activity.addPersonButton.getVisibility());
        });
        SystemClock.sleep(90);
        main(() -> {
            activity.showPanel("");
            if (PanelTransitions.enabled(activity.morePanel))
                assertEquals("FAB stays hidden until the scrim closes", View.GONE, activity.addPersonButton.getVisibility());
        });
        SystemClock.sleep(30);
        for (String name : new String[]{"card", "links", "people", "more", "files", "settings", "view", "guides", "branch"}) {
            main(() -> {
                activity.showPanel(name);
                assertEquals(name, View.GONE, activity.addPersonButton.getVisibility());
            });
            SystemClock.sleep(25);
        }
        SystemClock.sleep(250);
        main(() -> {
            assertEquals(View.GONE, activity.addPersonButton.getVisibility());
            activity.showPanel("");
            assertEquals(View.VISIBLE, activity.addPersonButton.getVisibility());
        });
    }

    public void testSearchInterruptedCloseReopensAndPressFeedbackRestoresScale() throws Exception {
        Object search = activity.searchModule;
        View field = (View) member(search, "field");
        main(() -> {
            activity.searchModule.toggle();
            assertEquals(View.VISIBLE, field.getVisibility());
            if (PanelTransitions.enabled(field)) assertTrue(field.getTranslationX() < 0f);
        });
        SystemClock.sleep(60);
        main(() -> activity.searchModule.clearTextAndFocus());
        SystemClock.sleep(30);
        main(() -> activity.searchModule.toggle());
        SystemClock.sleep(400);
        main(() -> {
            assertEquals(View.VISIBLE, field.getVisibility());
            assertSettled(field);
            activity.searchModule.clearTextAndFocus();
            activity.undoBtn.setEnabled(true);
            activity.undoBtn.setPressed(true);
        });
        SystemClock.sleep(140);
        main(() -> {
            assertTrue("Undo has tactile press feedback", activity.undoBtn.getScaleX() < .98f);
            activity.undoBtn.setPressed(false);
        });
        SystemClock.sleep(250);
        main(() -> {
            assertEquals(1f, activity.undoBtn.getScaleX(), .001f);
            assertEquals(View.INVISIBLE, field.getVisibility());
            assertSettled(field);
            assertEquals(View.VISIBLE, ((View) member(search, "toggleButton")).getVisibility());
        });
    }

    public void testCameraFitMovesSmoothlyAndTouchCancelsFlight() throws Exception {
        main(() -> {
            TreeState sample = cameraSample();
            activity.treeView.setState(sample);
            setFloat(activity.treeView, "scale", .3f);
            setFloat(activity.treeView, "offsetX", 20f);
            setFloat(activity.treeView, "offsetY", 30f);
            activity.treeView.fit();
            if (PanelTransitions.enabled(activity.treeView)) {
                assertEquals(20f, value(activity.treeView, "offsetX"), .01f);
                assertEquals(.3f, value(activity.treeView, "scale"), .001f);
            }
        });
        SystemClock.sleep(130);
        float[] middle = new float[1];
        main(() -> middle[0] = value(activity.treeView, "offsetX"));
        SystemClock.sleep(500);
        main(() -> {
            if (PanelTransitions.enabled(activity.treeView))
                assertTrue(Math.abs(middle[0] - value(activity.treeView, "offsetX")) > 1f);
            activity.treeView.focusWorkspaceCenter();
        });
        SystemClock.sleep(80);
        float[] stopped = new float[1];
        main(() -> {
            touch(activity.treeView, android.view.MotionEvent.ACTION_DOWN);
            touch(activity.treeView, android.view.MotionEvent.ACTION_UP);
            stopped[0] = value(activity.treeView, "offsetX");
        });
        SystemClock.sleep(550);
        main(() -> {
            assertEquals("A new gesture cancels the camera flight", stopped[0], value(activity.treeView, "offsetX"), .01f);
            activity.treeView.setState(activity.state);
        });
    }

    public void testHeldZoomChangesContinuouslyAndStopsOnRelease() throws Exception {
        ViewGroup rail = (ViewGroup) activity.zoomRail;
        View zoomIn = rail.getChildAt(rail.getChildCount() - 1);
        main(() -> {
            setFloat(activity.treeView, "scale", .4f);
            touch(zoomIn, android.view.MotionEvent.ACTION_DOWN);
        });
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout() + 200);
        float[] sample = new float[2];
        main(() -> sample[0] = value(activity.treeView, "scale"));
        SystemClock.sleep(180);
        main(() -> {
            sample[1] = value(activity.treeView, "scale");
            assertTrue(sample[0] > .4f);
            assertTrue(sample[1] > sample[0] + .01f);
            touch(zoomIn, android.view.MotionEvent.ACTION_UP);
        });
        SystemClock.sleep(650);
        main(() -> sample[0] = value(activity.treeView, "scale"));
        SystemClock.sleep(150);
        main(() -> assertEquals("Release stops zooming", sample[0], value(activity.treeView, "scale"), .001f));
    }

    public void testPopupsAnimateAndOnlineWindowHasFinalGeometryBeforeShow() throws Exception {
        Object add = member(activity, "addPersonMenuModule");
        Object actions = member(activity, "personMenusModule");
        TreeState original = activity.state;
        main(() -> {
            activity.state = cameraSample();
            activity.treeView.setState(activity.state);
            ((MainActivityAddPersonMenu) add).showAddPersonMenu();
            android.widget.PopupWindow popup = (android.widget.PopupWindow) member(add, "activePopup");
            assertNotNull(popup);
            if (PanelTransitions.enabled(popup.getContentView())) assertEquals(0f, popup.getContentView().getAlpha());
        });
        SystemClock.sleep(380);
        main(() -> {
            android.widget.PopupWindow popup = (android.widget.PopupWindow) member(add, "activePopup");
            assertSettled(popup.getContentView()); popup.dismiss();
            ((MainActivityPersonMenus) actions).openPersonActions(activity.state.selectedPerson(), activity.dp(220), activity.dp(240));
            popup = (android.widget.PopupWindow) member(actions, "activePopup");
            View menu = ((ViewGroup) popup.getContentView()).getChildAt(0);
            if (PanelTransitions.enabled(menu)) assertEquals(0f, menu.getAlpha());
        });
        SystemClock.sleep(380);
        main(() -> {
            android.widget.PopupWindow popup = (android.widget.PopupWindow) member(actions, "activePopup");
            View menu = ((ViewGroup) popup.getContentView()).getChildAt(0);
            assertSettled(menu); popup.dismiss();
            activity.state = original;
            activity.treeView.setState(original);
            Object online = member(activity, "onlineModule");
            ((MainActivityOnline) online).openDashboard();
            android.app.Dialog dialog = (android.app.Dialog) member(online, "dashboard");
            android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            assertEquals(android.view.Gravity.CENTER, params.gravity);
            assertTrue(params.width > 0); assertTrue(params.height > 0);
            assertEquals(0, params.x); assertEquals(0, params.y);
            assertEquals(0, params.windowAnimations);
        });
        SystemClock.sleep(400);
        main(() -> {
            Object online = member(activity, "onlineModule");
            android.app.Dialog dialog = (android.app.Dialog) member(online, "dashboard");
            View decor = dialog.getWindow().getDecorView();
            assertEquals(dialog.getWindow().getAttributes().width, decor.getWidth());
            dialog.dismiss();
        });
    }

    private TreeState cameraSample() {
        TreeState sample = new TreeState();
        Person person = new Person("motion-sample"); person.name = "Motion"; person.x = 1200f; person.y = 800f;
        sample.people.put(person.id, person); sample.selectedId = person.id; sample.rootId = person.id;
        return sample;
    }

    private static Object member(Object target, String name) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true); return field.get(target);
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private static float value(Object target, String name) { return (Float) member(target, name); }

    private static void setFloat(Object target, String name, float value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true); field.setFloat(target, value);
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private static void touch(View target, int action) {
        long now = SystemClock.uptimeMillis();
        android.view.MotionEvent event = android.view.MotionEvent.obtain(now, now, action,
            target.getWidth() / 2f, target.getHeight() / 2f, 0);
        target.dispatchTouchEvent(event); event.recycle();
    }

    public void testLandscapeAnimatesScrollContainerAndFullPeoplePanel() throws Exception {
        main(() -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        awaitOrientation(true);
        SystemClock.sleep(300);
        AtomicReference<View> container = new AtomicReference<>();
        main(() -> {
            activity.showPanel("card");
            container.set((View) activity.cardPanel.getParent());
            assertTrue(container.get() instanceof android.widget.ScrollView);
            if (PanelTransitions.enabled(container.get())) {
                assertTrue(container.get().getTranslationX() > 0f);
                assertEquals(0f, container.get().getAlpha());
            }
        });
        SystemClock.sleep(550);
        main(() -> {
            assertSettled(container.get());
            activity.showPanel("people");
            assertEquals(View.GONE, activity.stage.getVisibility());
        });
        SystemClock.sleep(550);
        main(() -> {
            assertEquals(View.VISIBLE, activity.peoplePanel.getVisibility());
            assertSettled(activity.peoplePanel);
            activity.showPanel("");
            assertEquals(View.GONE, activity.peoplePanel.getVisibility());
            assertEquals(View.VISIBLE, activity.stage.getVisibility());
        });
    }

    private View panel(String name) {
        switch (name) {
            case "view": return activity.viewPanel;
            case "card": return activity.cardPanel;
            case "links": return activity.linksPanel;
            case "guides": return activity.guidePanel;
            case "branch": return activity.branchPanel;
            case "files": return activity.filesPanel;
            case "settings": return activity.settingsPanel;
            case "people": return activity.peoplePanel;
            default: throw new AssertionError(name);
        }
    }

    private void assertSettled(View view) {
        assertEquals(1f, view.getAlpha(), .001f);
        assertEquals(0f, view.getTranslationX(), .1f);
        assertEquals(0f, view.getTranslationY(), .1f);
    }

    private void awaitOrientation(boolean landscape) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] ready = {false};
        while (SystemClock.uptimeMillis() < deadline) {
            main(() -> ready[0] = activity.isLandscapeLayout() == landscape
                && activity.bottomNavigation.getWidth() > 0 && !activity.bottomNavigation.isLayoutRequested());
            if (ready[0]) return;
            SystemClock.sleep(25);
        }
        fail("Orientation layout did not complete");
    }

    private void main(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
