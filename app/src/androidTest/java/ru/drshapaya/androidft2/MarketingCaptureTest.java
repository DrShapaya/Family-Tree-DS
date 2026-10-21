package ru.drshapaya.androidft2;

import android.content.Intent;
import android.content.Context;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import java.lang.reflect.Field;

/**
 * Non-destructive UI sequences used to capture repeatable channel tutorials.
 *
 * <p>The tests only navigate existing screens and fill the people search. They
 * never save or mutate the loaded family tree.</p>
 */
public final class MarketingCaptureTest extends InstrumentationTestCase {
    private MainActivity activity;

    @Override protected void setUp() throws Exception {
        super.setUp();
        AppLanguage.setMode(getInstrumentation().getTargetContext(), AppLanguage.RUSSIAN);
        Intent intent = new Intent(getInstrumentation().getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) getInstrumentation().startActivitySync(intent);
        TestStartup.await(getInstrumentation(), activity);
        drainMainThread();
        SystemClock.sleep(900);
    }

    @Override protected void tearDown() throws Exception {
        if (activity != null) {
            getInstrumentation().runOnMainSync(activity::finish);
            activity = null;
        }
        super.tearDown();
    }

    public void testSmartSearchSequence() throws Exception {
        SystemClock.sleep(2400);
        showPanel("people");
        drainMainThread();
        SystemClock.sleep(3200);

        Field queryField = MainActivityPeople.class.getDeclaredField("query");
        queryField.setAccessible(true);
        EditText query = (EditText) queryField.get(activity.peopleModule);
        getInstrumentation().runOnMainSync(() -> {
            query.requestFocus();
            query.performClick();
            InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
        });
        drainMainThread();
        SystemClock.sleep(2600);

        String phrase = "предки Анны";
        for (int index = 1; index <= phrase.length(); index++) {
            String value = phrase.substring(0, index);
            getInstrumentation().runOnMainSync(() -> query.setText(value));
            drainMainThread();
            SystemClock.sleep(260);
        }
        SystemClock.sleep(1600);
        getInstrumentation().runOnMainSync(() -> {
            InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(query.getWindowToken(), 0);
            query.clearFocus();
        });
        drainMainThread();
        SystemClock.sleep(4800);
    }

    public void testFilesSequence() throws Exception {
        SystemClock.sleep(2400);
        showPanel("more");
        drainMainThread();
        SystemClock.sleep(3200);
        showPanel("files");
        drainMainThread();
        SystemClock.sleep(3800);
        openSavedVersions();
        drainMainThread();
        SystemClock.sleep(5200);
    }

    private void showPanel(String panel) {
        getInstrumentation().runOnMainSync(() -> {
            try {
                Field panelsField = MainActivity.class.getDeclaredField("panelsModule");
                panelsField.setAccessible(true);
                MainActivityPanels panels = (MainActivityPanels) panelsField.get(activity);
                panels.showPanel(panel);
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        });
    }

    private void openSavedVersions() {
        getInstrumentation().runOnMainSync(() -> {
            try {
                Field filesField = MainActivity.class.getDeclaredField("filesModule");
                filesField.setAccessible(true);
                MainActivityFiles files = (MainActivityFiles) filesField.get(activity);
                files.openVersions();
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        });
    }

    private void drainMainThread() {
        // waitForIdleSync can wait forever while animated backgrounds or ads
        // keep posting frames. A synchronous no-op still guarantees that all
        // UI work queued before it has been processed, without requiring the
        // main queue to become permanently idle.
        getInstrumentation().runOnMainSync(() -> {});
    }
}
