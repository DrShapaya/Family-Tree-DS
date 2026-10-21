package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises actual themed activities, including their separately created dialog windows. */
public final class DarkThemeUiTest extends InstrumentationTestCase {
    private MainActivity activity;
    private TreeStore store;
    private TreeState original;
    private String language;
    private boolean hadTraining, training;
    private final JSONArray captures = new JSONArray();

    @Override protected void setUp() throws Exception {
        super.setUp();
        Context context = getInstrumentation().getTargetContext();
        language = AppLanguage.mode(context); AppLanguage.setMode(context, AppLanguage.RUSSIAN);
        SharedPreferences prefs = context.getSharedPreferences("androidft-ui", Context.MODE_PRIVATE);
        hadTraining = prefs.contains("training_offer_handled"); training = prefs.getBoolean("training_offer_handled", false);
        prefs.edit().putBoolean("training_offer_handled", true).commit();
        store = new TreeStore(context); original = store.load();
        TreeState sample = new TreeState(); sample.theme = "dark"; sample.showLifeStatusBadge = true;
        sample.useMaidenNames = true; sample.onboardingCompleted = true;
        String[] names = {"Соколов Алексей Петрович", "Соколова Мария Ивановна", "Соколов Николай Алексеевич"};
        for (int i = 0; i < names.length; i++) {
            Person p = new Person("dark-" + i); p.name = names[i]; p.bornYear = i == 2 ? "2010" : "1980";
            p.place = "Томск"; p.x = 80 + i * 320; p.y = i == 2 ? 340 : 80;
            if (i == 1) { p.maidenName = "Иванова"; p.maidenNameEnabled = true; p.surnameDisplay = "maiden"; }
            sample.people.put(p.id, p);
        }
        sample.links.add(new Relation("couple", "partner", "dark-0", "dark-1"));
        sample.links.add(new Relation("father", "parent", "dark-0", "dark-2"));
        sample.links.add(new Relation("mother", "parent", "dark-1", "dark-2"));
        sample.rootId = "dark-0"; sample.selectedId = "dark-1";
        store.save(sample); launch();
    }

    private void launch() {
        activity = (MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        TestStartup.await(getInstrumentation(), activity);
        getInstrumentation().waitForIdleSync();
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (activity != null) {
                onMain(() -> { activity.state = original; activity.theme = original.theme; activity.finish(); });
                getInstrumentation().waitForIdleSync();
            }
            store.save(original);
            SharedPreferences.Editor prefs = getInstrumentation().getTargetContext().getSharedPreferences("androidft-ui", Context.MODE_PRIVATE).edit();
            if (hadTraining) prefs.putBoolean("training_offer_handled", training); else prefs.remove("training_offer_handled"); prefs.commit();
            AppLanguage.setMode(getInstrumentation().getTargetContext(), language);
            AppThemePalette.setDark("dark".equals(original.theme));
        } finally { super.tearDown(); }
    }

    public void testDarkScreensAndDialogsHaveConsistentSurfaces() throws Exception {
        onMain(() -> { assertTrue(AppThemePalette.isDark()); assertHeader(); activity.treeView.fit(); });
        capture("01-tree");
        for (String panel : new String[]{"card", "people", "links", "more", "files", "view", "branch", "guides", "settings"}) {
            onMain(() -> activity.showPanel(panel)); capture("panel-" + panel);
        }
        onMain(() -> assertSolidButtonContrast(activity.getWindow().getDecorView()));
        onMain(() -> scrollVisible(activity.getWindow().getDecorView(), true)); capture("settings-bottom");
        onMain(() -> { activity.showPanel(""); activity.searchModule.toggle(); }); capture("search");
        onMain(() -> activity.searchModule.toggle());

        onMain(() -> activity.openPersonEditor()); capture("editor-profile");
        onMain(() -> scrollVisible(topWindow(), true)); capture("editor-profile-bottom");
        onMain(() -> clickText(topWindow(), "Память · 0")); capture("editor-memory");
        onMain(() -> clickText(topWindow(), "Связи")); capture("editor-relations"); closeWindow();

        onMain(() -> MainActivityStatistics.show(activity, activity.state.rootId)); capture("statistics-numbers");
        onMain(() -> topWindow().findViewWithTag("statistics.charts").performClick()); capture("statistics-charts");
        onMain(() -> scrollVisible(topWindow(), true)); capture("statistics-charts-bottom"); closeWindow();
        onMain(() -> activity.showTreeQualityDialog()); capture("quality"); closeWindow();
        onMain(() -> LifeStatusAppearanceDialog.show(activity)); capture("life-icons");
        onMain(() -> scrollVisible(topWindow(), true)); capture("life-colors"); closeWindow();
        onMain(() -> invoke(field(activity, "filesModule"), "showExportMenu")); capture("export"); closeWindow();
        onMain(() -> invoke(field(field(activity, "filesModule"), "importPicker"), "chooseSource")); capture("import"); closeWindow();
        onMain(() -> invoke(activity, "showAbout")); capture("about"); closeWindow();
        onMain(() -> invoke(field(activity, "workshopModule"), "open")); capture("workshop"); closeWindow();
        onMain(() -> invoke(field(activity, "onlineModule"), "openDashboard")); capture("online"); closeWindow();
        onMain(() -> invoke(activity, "showLanguageDialog")); capture("language"); closeWindow();
        onMain(() -> activity.showHelp("Тёмная тема", "Текст справки должен оставаться читаемым.")); capture("help"); closeWindow();
        writeReport("dark-portrait.json");
        for (int i = 0; i < captures.length(); i++) assertEquals(captures.getJSONObject(i).getString("screen"), 0,
            captures.getJSONObject(i).getJSONArray("lightSurfaces").length());
    }

    public void testThemeSurvivesRelaunchAndLandscapeUsesSameHeader() throws Exception {
        onMain(() -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        Thread.sleep(1500); getInstrumentation().waitForIdleSync();
        // This activity handles orientation changes itself.
        onMain(() -> { assertHeader(); activity.showPanel(""); activity.treeView.fit(); }); capture("landscape-tree");
        onMain(() -> activity.showPanel("people")); capture("landscape-people");
        onMain(() -> activity.showPanel("settings")); capture("landscape-settings");
        onMain(() -> { activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); });
        Thread.sleep(1200);
        TreeState dark = TreeStateCopier.copy(activity.state); dark.theme = "dark";
        onMain(() -> activity.finish()); getInstrumentation().waitForIdleSync(); store.save(dark); launch();
        onMain(this::assertHeader); capture("dark-relaunch");
        switchTheme("light");
        onMain(() -> {
            assertFalse(AppThemePalette.isDark());
            assertEquals(Color.WHITE, ((ColorDrawable)activity.headerBrand.getBackground()).getColor());
            assertTrue(luminance(((TextView)activity.getWindow().getDecorView().findViewWithTag("header.title")).getCurrentTextColor()) < 0.1);
        }); capture("light-control");
        switchTheme("dark"); onMain(this::assertHeader); capture("dark-switch-back"); writeReport("theme-relaunch.json");
    }

    public void testOtherMenusAndPhotoEditorInDarkTheme() throws Exception {
        Object files = field(activity, "filesModule");
        onMain(() -> invoke(files, "openVersions")); capture("versions"); closeWindow();
        onMain(() -> invoke(files, "showPngSaveSettings")); capture("png-settings");
        onMain(() -> assertSolidButtonContrast(topWindow()));
        onMain(() -> scrollVisible(topWindow(), true)); capture("png-settings-bottom"); closeWindow();
        onMain(() -> invoke(files, "showPdfSettings")); capture("pdf-settings"); closeWindow();
        onMain(() -> activity.showKinshipModeDialog()); capture("kinship-mode"); closeWindow();
        onMain(() -> { activity.showPanel("people"); invoke(activity.peopleModule, "showFilters"); }); capture("people-filters"); closeWindow();
        onMain(() -> invoke(activity.peopleModule, "showSorting")); capture("people-sorting"); closeWindow();
        onMain(() -> { activity.showPanel(""); invoke(activity, "showAddPersonMenu"); }); capture("add-person"); closeWindow();
        Bitmap photo = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888); photo.eraseColor(Color.rgb(52, 110, 141));
        java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream(); photo.compress(Bitmap.CompressFormat.PNG, 100, data); photo.recycle();
        onMain(() -> {
            Person person = activity.state.selectedPerson(); person.photo = "data:image/png;base64," + android.util.Base64.encodeToString(data.toByteArray(), android.util.Base64.NO_WRAP);
            activity.showPersonPhotoEditor(person);
        }); capture("photo-editor");
        onMain(() -> assertTrue(clickText(topWindow(), "▣ Позиция"))); capture("photo-position"); closeWindow();
        onMain(() -> assertTrue(clickText(topWindow(), "✂ Обрезать"))); capture("photo-crop"); closeWindow(); closeWindow();
        onMain(() -> activity.showPhotoPreview(activity.state.selectedPerson().photo)); capture("photo-preview"); closeWindow();
        onMain(() -> {
            AppDialog.create(activity, R.drawable.ic_menu_warning, "Импорт не выполнен")
                .message("Не удалось прочитать файл дерева.").action("Понятно", true, null).show();
        }); capture("import-error"); closeWindow();
        writeReport("dark-extra.json");
        for (int i = 0; i < captures.length(); i++) assertEquals(captures.getJSONObject(i).getString("screen"), 0,
            captures.getJSONObject(i).getJSONArray("lightSurfaces").length());
    }

    private void switchTheme(String next) {
        android.app.Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        onMain(() -> {
            try {
                Method method = MainActivity.class.getDeclaredMethod("setTheme", String.class);
                method.setAccessible(true); method.invoke(activity, next);
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        MainActivity recreated = (MainActivity)getInstrumentation().waitForMonitorWithTimeout(monitor, 8000);
        getInstrumentation().removeMonitor(monitor); assertNotNull("Activity recreated on theme change", recreated);
        activity = recreated; TestStartup.await(getInstrumentation(), activity); getInstrumentation().waitForIdleSync();
    }

    private void assertSolidButtonContrast(View view) {
        if (!view.isShown()) return;
        if (view instanceof TextView && view.getBackground() instanceof GradientDrawable) {
            GradientDrawable background = (GradientDrawable)view.getBackground();
            if (background.getColors() == null && background.getColor() != null) {
                int fill = background.getColor().getDefaultColor();
                if (Color.alpha(fill) == 255) assertTrue("Unreadable label: " + ((TextView)view).getText(),
                    contrast(((TextView)view).getCurrentTextColor(), fill) >= 4.5);
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) assertSolidButtonContrast(((ViewGroup)view).getChildAt(i));
    }

    private void assertHeader() {
        int background = ((ColorDrawable)activity.headerBrand.getBackground()).getColor();
        TextView title = activity.getWindow().getDecorView().findViewWithTag("header.title");
        assertTrue("Dark header", luminance(background) < 0.1);
        assertTrue("Header title contrast", contrast(title.getCurrentTextColor(), background) >= 7);
        assertTrue("Header statistics contrast", contrast(activity.stats.getCurrentTextColor(), background) >= 4.5);
        assertEquals(0, activity.getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void capture(String name) throws Exception {
        getInstrumentation().waitForIdleSync(); Thread.sleep(650);
        Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try (FileOutputStream output = new FileOutputStream(new File(activity.getExternalCacheDir(), "dark-" + name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { bitmap.recycle(); }
        JSONArray leaks = new JSONArray();
        if (AppThemePalette.isDark()) onMain(() -> scan(topWindow(), leaks));
        JSONObject record = new JSONObject(); record.put("screen", name); record.put("lightSurfaces", leaks); captures.put(record);
    }

    private void scan(View view, JSONArray leaks) {
        if (!view.isShown() || view instanceof ColorPickerView) return;
        Drawable background = view.getBackground(); int[] colors = null;
        if (background instanceof ColorDrawable) colors = new int[]{((ColorDrawable)background).getColor()};
        if (background instanceof GradientDrawable) {
            GradientDrawable gradient = (GradientDrawable)background; colors = gradient.getColors();
            if (colors == null && gradient.getColor() != null) colors = new int[]{gradient.getColor().getDefaultColor()};
        }
        if (colors != null && view.getWidth() >= activity.dp(96) && view.getHeight() >= activity.dp(36)) {
            for (int color : colors) if (Color.alpha(color) > 180 && luminance(color) > 0.65) {
                leaks.put(view.getClass().getSimpleName() + ":" + view.getTag() + ":" + Integer.toHexString(color)); break;
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) scan(((ViewGroup)view).getChildAt(i), leaks);
    }

    private void writeReport(String name) throws Exception {
        try (FileOutputStream output = new FileOutputStream(new File(activity.getExternalCacheDir(), name))) {
            output.write(captures.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }
    private View topWindow() {
        java.util.List<View> windows = WindowInspector.getGlobalWindowViews();
        for (int i = windows.size() - 1; i >= 0; i--) if (windows.get(i).hasWindowFocus()) return windows.get(i);
        return activity.getWindow().getDecorView();
    }
    private void closeWindow() throws InterruptedException {
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        // The parent dialog regains window focus after the platform's dismiss animation.
        Thread.sleep(350); getInstrumentation().waitForIdleSync();
    }
    private void scrollVisible(View view, boolean down) {
        if (!view.isShown()) return;
        if (view instanceof ScrollView) { ((ScrollView)view).fullScroll(down ? View.FOCUS_DOWN : View.FOCUS_UP); return; }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) scrollVisible(((ViewGroup)view).getChildAt(i), down);
    }
    private boolean clickText(View view, String text) {
        if (!view.isShown()) return false;
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText()) && view.isClickable()) { view.performClick(); return true; }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) if (clickText(((ViewGroup)view).getChildAt(i), text)) return true;
        return false;
    }
    private static double luminance(int color) {
        return 0.2126 * linear(Color.red(color)) + 0.7152 * linear(Color.green(color)) + 0.0722 * linear(Color.blue(color));
    }
    private static double linear(int value) { double c = value / 255d; return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
    private static double contrast(int a, int b) { double x = luminance(a), y = luminance(b); return (Math.max(x,y) + 0.05) / (Math.min(x,y) + 0.05); }
    private void onMain(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    private static Object field(Object owner, String name) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private static Object invoke(Object owner, String name) {
        try { Method method = owner.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(owner); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
}
