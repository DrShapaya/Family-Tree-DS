package ru.drshapaya.androidft2;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.test.InstrumentationTestCase;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;

/** Device tests exercise real Canvas/PDF rasterization, including tile seams. */
public class ExportRenderingTest extends InstrumentationTestCase {
    private MainActivity activity;
    private TreeState previous;
    private File artifacts;
    private String previousLanguage;

    @Override protected void setUp() throws Exception {
        super.setUp();
        Intent intent = new Intent(getInstrumentation().getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (MainActivity) getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        artifacts = new File(activity.getExternalMediaDirs()[0], "additional_test_output");
        artifacts.mkdirs();
        previousLanguage = AppLanguage.mode(activity);
        AppLanguage.setMode(activity, AppLanguage.RUSSIAN);
        getInstrumentation().runOnMainSync(() -> {
            previous = activity.state;
            activity.state = sampleTree();
        });
    }

    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            AppLanguage.setMode(activity, previousLanguage);
            activity.state = previous;
            activity.finish();
        });
        super.tearDown();
    }

    private TreeState sampleTree() {
        TreeState state = new TreeState();
        String[] names = {"Иванов Александр", "Иванова Мария", "Иванов Дмитрий", "Иванова Анна"};
        for (int i = 0; i < names.length; i++) {
            Person person = new Person("person-" + i);
            person.name = names[i]; person.born = "1980";
            person.bornYear = "1980"; person.place = "Томск";
            person.x = (i % 2) * 440; person.y = (i / 2) * 280;
            person.color = i % 2 == 0 ? 0xff84c7ae : 0xfff2d16b;
            state.people.put(person.id, person);
        }
        state.links.add(new Relation("partner", "partner", "person-0", "person-1"));
        state.links.add(new Relation("parent", "parent", "person-0", "person-2"));
        state.links.add(new Relation("sibling", "sibling", "person-2", "person-3"));
        return state;
    }

    private TreeState largePreviewTree() {
        TreeState state = new TreeState();
        for (int i = 0; i < 32; i++) {
            Person person = new Person("preview-person-" + i);
            person.name = "Участник большой семьи " + (i + 1);
            person.born = Integer.toString(1940 + i);
            person.bornYear = person.born;
            person.place = "Томск";
            person.x = (i % 8) * 440;
            person.y = (i / 8) * 280;
            person.color = i % 2 == 0 ? 0xff8fc6a4 : 0xffefd180;
            state.people.put(person.id, person);
        }
        return state;
    }

    private void save(Bitmap bitmap, String name) {
        try (FileOutputStream out = new FileOutputStream(new File(artifacts, name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } catch (Exception error) { throw new AssertionError(error); }
    }

    public void testStylesAndTileSeams() {
        getInstrumentation().runOnMainSync(() -> {
            TreeImageExport export = new TreeImageExport(activity);
            try {
                String[] cards = {"standard", "card_theme_archive", "card_theme_midnight", "card_theme_glass", "card_theme_fire"};
                String[] links = {"standard", "link_style_ink", "link_style_ribbon", "link_style_blueprint", "link_style_fire"};
                String[] backgrounds = {"standard", "canvas_background_paper", "canvas_background_blueprint", "canvas_background_deep_blueprint", "canvas_background_garden"};
                for (int i = 0; i < cards.length; i++) {
                    export.renderer.setCardStyle(cards[i]);
                    export.renderer.setCardEdgeStyle(i == 4 ? "card_edge_fire" : "standard");
                    export.renderer.setLinkStyle(links[i]);
                    export.background = backgrounds[i]; export.grid = true; export.apply();
                    ExportLayout layout = new ExportLayout(export.crop.width(), export.crop.height(), 1.5f, 4_000_000, 0, 0);
                    Bitmap image = export.tile(layout, 0, 0);
                    save(image, "style-" + i + ".png"); image.recycle();
                    export.renderer.setTheme("dark");
                    AppThemePalette.setDark(true);
                    image = export.tile(layout, 0, 0);
                    save(image, "style-dark-" + i + ".png"); image.recycle();
                    AppThemePalette.setDark(false);
                }
                export.background = "canvas_background_paper";
                export.frame = "export_frame_classic";
                export.apply();
                export.crop.inset(11.25f, 13.75f);
                ExportLayout full = new ExportLayout(export.crop.width(), export.crop.height(), 1.7f, 4_000_000, 0, 0);
                ExportLayout parts = new ExportLayout(export.crop.width(), export.crop.height(), 1.7f, 4_000_000, 2, 1);
                assertEquals(full.width, parts.width); assertEquals(full.height, parts.height);
                Bitmap single = export.tile(full, 0, 0);
                Bitmap stitched = Bitmap.createBitmap(full.width, full.height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(stitched);
                for (int row = 0; row < parts.rows; row++) for (int col = 0; col < parts.columns; col++) {
                    Bitmap tile = export.tile(parts, col, row);
                    canvas.drawBitmap(tile, parts.x(col), parts.y(row), null); tile.recycle();
                }
                int[] a = new int[full.width * full.height], b = new int[a.length];
                single.getPixels(a, 0, full.width, 0, 0, full.width, full.height);
                stitched.getPixels(b, 0, full.width, 0, 0, full.width, full.height);
                int mismatch = 0;
                for (int i = 0; i < a.length; i++) if (a[i] != b[i]) mismatch++;
                save(single, "single.png"); save(stitched, "stitched.png");
                single.recycle(); stitched.recycle();
                assertTrue("Tile mismatch pixels: " + mismatch, mismatch < a.length / 200);
            } finally { export.close(); AppThemePalette.setDark(false); }
        });
    }

    public void testPaperFollowsWorldTransformAndFireAnimates() {
        getInstrumentation().runOnMainSync(() -> {
            PaperTexture paper = new PaperTexture();
            Bitmap first = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888);
            Bitmap shifted = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888);
            paper.draw(new Canvas(first), 640, 400, 1, 0, 0, false);
            paper.draw(new Canvas(shifted), 640, 400, 1, -64, -32, false);
            for (int y = 0; y < 320; y += 7) for (int x = 0; x < 560; x += 7)
                assertEquals(first.getPixel(x + 64, y + 32), shifted.getPixel(x, y));
            save(first, "paper-100.png");
            paper.draw(new Canvas(shifted), 640, 400, 2, 0, 0, false);
            save(shifted, "paper-200.png");
            // At 2x the same fibres occupy twice as much space, rather than staying on the screen.
            int difference = 0, samples = 0;
            for (int y = 2; y < 198; y += 3) for (int x = 2; x < 318; x += 3) {
                difference += Math.abs(Color.red(first.getPixel(x, y)) - Color.red(shifted.getPixel(x * 2, y * 2)));
                samples++;
            }
            assertTrue("Paper zoom changed grain locations", difference / (float) samples < 3f);
            first.recycle(); shifted.recycle();

            TreeCanvasView view = new TreeCanvasView(activity);
            view.setState(sampleTree()); view.setCardStyle("card_theme_fire"); view.setCardEdgeStyle("card_edge_fire");
            view.setCanvasStyle("canvas_background_paper"); view.setGenerationLines(false);
            view.layout(0, 0, 1000, 700);
            try {
                java.lang.reflect.Field time = TreeCanvasView.class.getDeclaredField("fireFrameSeconds");
                time.setAccessible(true);
                Method card = TreeCanvasView.class.getDeclaredMethod("drawCard", Canvas.class, Person.class, boolean.class);
                card.setAccessible(true);
                java.lang.reflect.Field zoom = TreeCanvasView.class.getDeclaredField("scale");
                java.lang.reflect.Field x = TreeCanvasView.class.getDeclaredField("offsetX");
                java.lang.reflect.Field y = TreeCanvasView.class.getDeclaredField("offsetY");
                zoom.setAccessible(true); x.setAccessible(true); y.setAccessible(true);
                zoom.setFloat(view, 2f); x.setFloat(view, 100); y.setFloat(view, 110);
                Person person = sampleTree().people.get("person-0");
                int[] previousPixels = null;
                for (int frame = 0; frame < 18; frame++) {
                    Bitmap bitmap = Bitmap.createBitmap(760, 530, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    paper.draw(canvas, 760, 530, 2, 100, 110, false);
                    time.setFloat(view, 2.4f + frame / 12f);
                    card.invoke(view, canvas, person, true);
                    int[] pixels = new int[760 * 530];
                    bitmap.getPixels(pixels, 0, 760, 0, 0, 760, 530);
                    if (previousPixels != null) {
                        int changes = 0;
                        for (int i = 0; i < pixels.length; i++) if (pixels[i] != previousPixels[i]) changes++;
                        assertTrue("Fire frame did not animate", changes > 100);
                    }
                    previousPixels = pixels;
                    save(bitmap, String.format(java.util.Locale.US, "fire-frame-%02d.png", frame));
                    bitmap.recycle();
                }
            } catch (Exception error) { throw new AssertionError(error); }
            finally { view.releaseExportResources(); }
        });
    }

    public void testIndependentEdgesAndSmokeAnimation() {
        getInstrumentation().runOnMainSync(() -> {
            TreeImageExport export = new TreeImageExport(activity);
            try {
                String[] themes = {"standard", "card_theme_fire", "card_theme_smoke", "card_theme_botanical"};
                String[] edges = {"standard", "card_edge_fire", "card_edge_smoke", "card_edge_botanical"};
                export.background = "standard"; export.grid = true; export.generations = false;
                export.apply();
                for (String theme : themes) for (String edge : edges) {
                    export.renderer.setCardStyle(theme); export.renderer.setCardEdgeStyle(edge);
                    export.renderer.setLinkStyle("card_edge_botanical".equals(edge) ? "link_style_botanical" : "standard");
                    ExportLayout layout = new ExportLayout(export.crop.width(), export.crop.height(), 1.5f, 4_000_000, 0, 0);
                    Bitmap image = export.tile(layout, 0, 0);
                    save(image, theme + "-" + edge + ".png"); image.recycle();
                    assertTileSeams(export);
                }
                export.compact = true; export.straight = true; export.apply();
                Bitmap compact = export.preview();
                save(compact, "botanical-compact-straight.png"); compact.recycle();
                export.renderer.setTheme("dark"); AppThemePalette.setDark(true);
                Bitmap dark = export.preview();
                save(dark, "botanical-dark.png"); dark.recycle();
                export.renderer.setTheme("light"); AppThemePalette.setDark(false);
                export.renderer.setCardStyle("card_theme_botanical");
                export.renderer.setCardEdgeStyle("card_edge_botanical");
                export.renderer.setLinkStyle("link_style_botanical");
                export.frame = "export_frame_botanical";
                Bitmap botanical = export.preview();
                save(botanical, "botanical-card-and-frame.png"); botanical.recycle();
            } finally { export.close(); AppThemePalette.setDark(false); }
            SmokeCardEffect smoke = new SmokeCardEffect();
            int[] previousPixels = null;
            for (int frame = 0; frame < 18; frame++) {
                Bitmap bitmap = Bitmap.createBitmap(760, 530, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap); canvas.drawColor(0xfff5f4ef);
                canvas.translate(100, 110); canvas.scale(2, 2);
                smoke.behind(canvas, 280, 160, 31, 2.4f + frame / 12f);
                android.graphics.Paint paint = new android.graphics.Paint(3);
                paint.setColor(0xff29343d); canvas.drawRoundRect(0, 0, 280, 160, 10, 10, paint);
                smoke.surface(canvas, 280, 160, 31); smoke.rim(canvas, 280, 160);
                paint.setColor(Color.WHITE); paint.setTextSize(19);
                canvas.drawText("Дымные края", 36, 65, paint);
                paint.setTextSize(13); paint.setColor(0xffd7e3ea);
                canvas.drawText("Сочетаются с любой карточкой", 36, 96, paint);
                int[] pixels = new int[760 * 530];
                bitmap.getPixels(pixels, 0, 760, 0, 0, 760, 530);
                if (previousPixels != null) {
                    int changes = 0;
                    for (int i = 0; i < pixels.length; i++) if (pixels[i] != previousPixels[i]) changes++;
                    assertTrue("Smoke frame did not animate", changes > 100);
                }
                previousPixels = pixels;
                save(bitmap, String.format(java.util.Locale.US, "smoke-frame-%02d.png", frame)); bitmap.recycle();
            }
        });
    }

    private void assertTileSeams(TreeImageExport export) {
        ExportLayout full = new ExportLayout(export.crop.width(), export.crop.height(), 1.25f, 4_000_000, 0, 0);
        ExportLayout parts = new ExportLayout(export.crop.width(), export.crop.height(), 1.25f, 4_000_000, 2, 1);
        Bitmap single = export.tile(full, 0, 0);
        Bitmap stitched = Bitmap.createBitmap(full.width, full.height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(stitched);
        for (int row = 0; row < parts.rows; row++) for (int col = 0; col < parts.columns; col++) {
            Bitmap tile = export.tile(parts, col, row);
            canvas.drawBitmap(tile, parts.x(col), parts.y(row), null); tile.recycle();
        }
        int[] a = new int[full.width * full.height], b = new int[a.length];
        single.getPixels(a, 0, full.width, 0, 0, full.width, full.height);
        stitched.getPixels(b, 0, full.width, 0, 0, full.width, full.height);
        int mismatch = 0;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) mismatch++;
        save(single, "edge-seam-single.png"); save(stitched, "edge-seam-stitched.png");
        // Software Canvas may round translucent antialiasing differently after tile translation.
        // Allow channel differences up to 8, but reject missing geometry or visible seams.
        int significant = 0, seamPixels = 0;
        for (int y = 0; y < full.height; y++) for (int x = 0; x < full.width; x++) {
            boolean seam = false;
            for (int column = 1; column < parts.columns; column++)
                seam |= Math.abs(x - parts.x(column)) <= 4;
            for (int row = 1; row < parts.rows; row++)
                seam |= Math.abs(y - parts.y(row)) <= 4;
            if (!seam) continue;
            seamPixels++;
            int index = y * full.width + x;
            int delta = Math.max(Math.abs(Color.red(a[index]) - Color.red(b[index])),
                Math.max(Math.abs(Color.green(a[index]) - Color.green(b[index])),
                    Math.abs(Color.blue(a[index]) - Color.blue(b[index]))));
            if (delta > 8) significant++;
        }
        single.recycle(); stitched.recycle();
        assertTrue("Edge tile differences: " + mismatch + "; seam differences: " + significant,
            significant < Math.max(1, seamPixels / 100));
    }

    public void testLegacyFireOwnershipMigration() {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("card-edges-migration-test", 0);
        try {
            prefs.edit().clear().putInt("tokens", 57)
                .putStringSet("owned", new java.util.HashSet<>(java.util.Arrays.asList("card_theme_fire", "card_theme_archive")))
                .putString("selected_card_themes", "card_theme_fire").commit();
            RewardWallet.migrateCardEdges(prefs);
            assertTrue(prefs.getStringSet("owned", java.util.Collections.emptySet()).contains("card_edge_fire"));
            assertEquals("card_edge_fire", prefs.getString("selected_card_edges", ""));
            assertEquals("card_theme_fire", prefs.getString("selected_card_themes", ""));
            assertEquals(57, prefs.getInt("tokens", 0));
            prefs.edit().putString("selected_card_edges", "standard").commit();
            RewardWallet.migrateCardEdges(prefs);
            assertEquals("standard", prefs.getString("selected_card_edges", ""));
            prefs.edit().remove("card_edges_migrated").putString("selected_card_edges", "card_edge_botanical").commit();
            RewardWallet.migrateCardEdges(prefs);
            assertEquals("card_edge_botanical", prefs.getString("selected_card_edges", ""));
            prefs.edit().clear().commit(); RewardWallet.migrateCardEdges(prefs);
            assertFalse(prefs.getStringSet("owned", java.util.Collections.emptySet()).contains("card_edge_fire"));
            assertFalse(prefs.contains("selected_card_edges"));
        } finally { prefs.edit().clear().commit(); }
    }

    public void testPdfFitsOnePage() throws Exception {
        MainActivityFiles files = new MainActivityFiles(activity);
        Method write = MainActivityFiles.class.getDeclaredMethod("writePdf", TreeState.class,
            java.io.OutputStream.class, String.class, boolean.class, int.class, boolean.class, boolean.class, String.class);
        write.setAccessible(true);
        TreeState large = sampleTree();
        large.people.get("person-3").x = 12000;
        large.people.get("person-3").y = 8000;
        File pdf = new File(artifacts, "fit.pdf");
        try (FileOutputStream out = new FileOutputStream(pdf)) {
            write.invoke(files, large, out, "A4", true, 100, false, true, "export_frame_classic");
        }
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(fd)) {
            assertEquals(1, renderer.getPageCount());
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                Bitmap image = Bitmap.createBitmap(page.getWidth() * 2, page.getHeight() * 2, Bitmap.Config.ARGB_8888);
                page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                assertEquals(Color.WHITE, image.getPixel(0, 0));
                save(image, "pdf-fit.png"); image.recycle();
            }
        }
    }

    public void testBotanicalPathsAreCachedWithoutZoomSimplification() {
        BotanicalEffect effect = new BotanicalEffect();
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(40, 90);
        path.cubicTo(230, -25, 410, 220, 690, 80);
        for (int frame = 0; frame < 80; frame++) {
            Bitmap bitmap = Bitmap.createBitmap(760, 260, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            float zoom = frame % 2 == 0 ? .14f : 1f;
            canvas.save(); canvas.scale(zoom, zoom);
            effect.link(canvas, path, false, false);
            canvas.restore();
            for (int person = 0; person < 40; person++) {
                canvas.save();
                canvas.translate((person % 10) * 72, 120 + (person / 10) * 28);
                canvas.scale(.14f, .14f);
                effect.behind(canvas, 280, 160, person);
                canvas.restore();
            }
            if (frame == 0) {
                int green = 0;
                for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
                    int pixel = bitmap.getPixel(x, y);
                    if (Color.green(pixel) > Color.red(pixel) * 1.25f
                            && Color.green(pixel) > Color.blue(pixel) * 1.15f) green++;
                }
                assertTrue("Detailed leaves disappeared at distant zoom", green > 70);
                save(bitmap, "botanical-zoom-014.png");
            }
            bitmap.recycle();
        }
        assertEquals("Link paths must be built once across zoom frames", 1, effect.linkBuildCountForTests());
        assertTrue("Card edge variants must stay bounded", effect.cardBuildCountForTests() <= 8);
    }

    public void testExportMenuBotanicalCanvasAndFireFrame() throws Exception {
        MainActivityFiles files = new MainActivityFiles(activity);
        android.app.Dialog[] menu = new android.app.Dialog[1];
        getInstrumentation().runOnMainSync(() -> {
            files.showExportMenu();
            try {
                java.lang.reflect.Field field = MainActivityFiles.class.getDeclaredField("exportMenuDialog");
                field.setAccessible(true);
                menu[0] = (android.app.Dialog) field.get(files);
                android.view.View root = menu[0].getWindow().getDecorView();
                assertNull(findText(root, "Дополнительные форматы  ▾"));
                assertNotNull(findText(root, "JSON"));
                assertNotNull(findText(root, "GEDCOM"));
                assertNotNull(findText(root, "PDF"));
                assertNull(findText(root, "PNG"));
            } catch (Exception error) { throw new AssertionError(error); }
        });
        getInstrumentation().waitForIdleSync(); android.os.SystemClock.sleep(350);
        Bitmap menuShot = getInstrumentation().getUiAutomation().takeScreenshot();
        save(menuShot, "export-menu-redesign.png"); menuShot.recycle();
        getInstrumentation().runOnMainSync(() -> menu[0].dismiss());

        android.app.Dialog[] pdf = new android.app.Dialog[1];
        getInstrumentation().runOnMainSync(() -> {
            try {
                Method showPdf = MainActivityFiles.class.getDeclaredMethod("showPdfSettings");
                showPdf.setAccessible(true); showPdf.invoke(files);
                java.lang.reflect.Field field = MainActivityFiles.class.getDeclaredField("pdfDialog");
                field.setAccessible(true); pdf[0] = (android.app.Dialog) field.get(files);
                assertNotNull(findTextStarting(pdf[0].getWindow().getDecorView(), "Рамка:"));
            } catch (Exception error) { throw new AssertionError(error); }
        });
        getInstrumentation().waitForIdleSync(); android.os.SystemClock.sleep(300);
        Bitmap pdfShot = getInstrumentation().getUiAutomation().takeScreenshot();
        save(pdfShot, "pdf-settings-redesign.png"); pdfShot.recycle();
        getInstrumentation().runOnMainSync(() -> pdf[0].dismiss());

        getInstrumentation().runOnMainSync(() -> {
            TreeImageExport export = new TreeImageExport(activity);
            try {
                export.background = "canvas_background_botanical";
                export.frame = "export_frame_fire";
                export.renderer.setCardStyle("card_theme_fire");
                export.renderer.setCardEdgeStyle("card_edge_fire");
                export.renderer.setLinkStyle("link_style_blueprint");
                export.grid = false; export.generations = false; export.apply();
                ExportLayout layout = new ExportLayout(
                    export.crop.width(), export.crop.height(), 1.4f, 4_000_000, 0, 0);
                Bitmap image = export.tile(layout, 0, 0);
                save(image, "botanical-fire-export.png");
                int warm = 0;
                for (int y = 0; y < image.getHeight(); y += 3)
                    for (int x = 0; x < image.getWidth(); x += 3) {
                        int pixel = image.getPixel(x, y);
                        if (Color.red(pixel) > 190 && Color.green(pixel) > 70
                                && Color.green(pixel) < 210 && Color.blue(pixel) < 70) warm++;
                    }
                assertTrue("Fire frame/flames were not rendered", warm > 80);
                image.recycle();
                assertTileSeams(export);
            } finally { export.close(); }

            Bitmap frame = Bitmap.createBitmap(720, 460, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(frame); canvas.drawColor(Color.WHITE);
            ExportDecoration.draw(canvas, new RectF(0, 0, 720, 460), "export_frame_fire", false);
            save(frame, "export-frame-fire.png"); frame.recycle();
        });
        files.close();
    }

    public void testPreviewDialog() throws Exception {
        MainActivityFiles files = new MainActivityFiles(activity);
        getInstrumentation().runOnMainSync(() -> {
            try {
                Method show = MainActivityFiles.class.getDeclaredMethod("showPngSettings");
                show.setAccessible(true); show.invoke(files);
            } catch (Exception error) { throw new AssertionError(error); }
        });
        getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(600); // Let the dialog's window animation finish before capture.
        Bitmap screen = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screen); save(screen, "png-dialog.png"); screen.recycle();
        java.lang.reflect.Field dialogField = MainActivityFiles.class.getDeclaredField("pngDialog");
        dialogField.setAccessible(true);
        android.app.Dialog dialog = (android.app.Dialog) dialogField.get(files);
        boolean compactBefore = activity.compactCards;
        getInstrumentation().runOnMainSync(() -> {
            android.view.View root = dialog.getWindow().getDecorView();
            ExportPreviewView preview = find(root, ExportPreviewView.class);
            ExportCutGrid grid = find(root, ExportCutGrid.class);
            assertNotNull(preview); assertNotNull(grid);
            Bitmap lowQuality = preview.export.preview(preview.export.extent, 2f, 4_000_000L);
            Bitmap highQuality = preview.export.preview(preview.export.extent, 10f, 32_000_000L);
            assertTrue((long) highQuality.getWidth() * highQuality.getHeight()
                > (long) lowQuality.getWidth() * lowQuality.getHeight());
            lowQuality.recycle(); highQuality.recycle();
            Bitmap selectedQuality = preview.export.preview(preview.export.extent, 4f, 8_000_000L);
            ExportLayout selectedLayout = preview.export.layout(4f, 8_000_000L);
            float exportPixelsPerWorld = selectedLayout.width / preview.export.crop.width();
            float previewPixelsPerWorld = selectedQuality.getWidth() / preview.export.extent.width();
            assertEquals(exportPixelsPerWorld, previewPixelsPerWorld, 0.02f);
            selectedQuality.recycle();
            touch(grid, android.view.MotionEvent.ACTION_DOWN, grid.getWidth() * 2.5f / 6f, grid.getHeight() * 1.5f / 6f);
            touch(grid, android.view.MotionEvent.ACTION_UP, grid.getWidth() * 2.5f / 6f, grid.getHeight() * 1.5f / 6f);
            assertEquals(2, preview.export.verticalCuts); assertEquals(1, preview.export.horizontalCuts);
            try {
                java.lang.reflect.Field field = ExportPreviewView.class.getDeclaredField("selection");
                field.setAccessible(true);
                RectF rect = new RectF((RectF) field.get(preview));
                java.lang.reflect.Field imageField = ExportPreviewView.class.getDeclaredField("image");
                java.lang.reflect.Field displayField = ExportPreviewView.class.getDeclaredField("displayExtent");
                imageField.setAccessible(true); displayField.setAccessible(true);
                RectF image = new RectF((RectF) imageField.get(preview));
                RectF display = new RectF((RectF) displayField.get(preview));
                touch(preview, android.view.MotionEvent.ACTION_DOWN, rect.centerX(), rect.top);
                touch(preview, android.view.MotionEvent.ACTION_MOVE, rect.centerX(), image.top);
                touch(preview, android.view.MotionEvent.ACTION_UP, rect.centerX(), image.top);
                assertEquals(display.top, preview.export.crop.top, 1f);
                float before = preview.export.crop.width();
                touch(preview, android.view.MotionEvent.ACTION_DOWN, rect.right, rect.centerY());
                touch(preview, android.view.MotionEvent.ACTION_MOVE, rect.right - 45, rect.centerY());
                touch(preview, android.view.MotionEvent.ACTION_UP, rect.right - 45, rect.centerY());
                assertTrue(preview.export.crop.width() < before);
            } catch (Exception error) { throw new AssertionError(error); }
            android.widget.ScrollView scroll = find(root, android.widget.ScrollView.class);
            scroll.fullScroll(android.view.View.FOCUS_DOWN);
        });
        getInstrumentation().waitForIdleSync(); android.os.SystemClock.sleep(400);
        screen = getInstrumentation().getUiAutomation().takeScreenshot();
        save(screen, "png-controls.png"); screen.recycle();
        assertEquals(compactBefore, activity.compactCards);
        getInstrumentation().runOnMainSync(files::close);
    }

    public void testLargeTreePreviewRendering() {
        getInstrumentation().runOnMainSync(() -> {
            TreeState before = activity.state;
            activity.state = largePreviewTree();
            TreeImageExport export = null;
            ExportPreviewView preview = null;
            Bitmap screen = null;
            try {
                export = new TreeImageExport(activity);
                preview = new ExportPreviewView(activity, export);
                preview.layout(0, 0, 1000, 750);
                preview.refresh(2f, 4_000_000L);
                screen = Bitmap.createBitmap(1000, 750, Bitmap.Config.ARGB_8888);
                preview.draw(new Canvas(screen));
                save(screen, "large-tree-png-preview.png");
                assertEquals(32, export.state.people.size());
            } finally {
                if (screen != null) screen.recycle();
                if (preview != null) preview.release();
                if (export != null) export.close();
                activity.state = before;
            }
        });
    }

    public void testWorkshopEdgeSectionAndIndependentSelection() throws Exception {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("androidft-rewards", 0);
        java.util.Set<String> oldOwned = new java.util.HashSet<>(prefs.getStringSet("owned", java.util.Collections.emptySet()));
        String oldTheme = prefs.getString("selected_card_themes", null), oldEdge = prefs.getString("selected_card_edges", null);
        android.app.Dialog[] dialog = new android.app.Dialog[1];
        try {
            java.util.Set<String> owned = new java.util.HashSet<>(oldOwned);
            owned.add("card_theme_smoke"); owned.add("card_edge_smoke"); owned.add("card_edge_botanical");
            prefs.edit().putStringSet("owned", owned).putString("selected_card_themes", "card_theme_smoke")
                .putString("selected_card_edges", "card_edge_smoke").commit();
            getInstrumentation().runOnMainSync(() -> {
                try {
                    Method open = MainActivity.class.getDeclaredMethod("openWorkshop"); open.setAccessible(true); open.invoke(activity);
                    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("workshopDialog"); field.setAccessible(true);
                    dialog[0] = (android.app.Dialog) field.get(activity);
                    Method select = MainActivity.class.getDeclaredMethod("chooseWorkshopItem", String.class, String.class); select.setAccessible(true);
                    select.invoke(activity, RewardCatalog.CARD_EDGES, "card_edge_botanical");
                    RewardWallet wallet = new RewardWallet(activity);
                    assertEquals("card_theme_smoke", wallet.selected(RewardCatalog.CARD_THEMES));
                    assertEquals("card_edge_botanical", wallet.selected(RewardCatalog.CARD_EDGES));
                    TreeImageExport export = new TreeImageExport(activity);
                    try {
                        java.lang.reflect.Field edge = TreeCanvasView.class.getDeclaredField("cardEdgeStyle"); edge.setAccessible(true);
                        assertEquals("card_edge_botanical", edge.get(export.renderer));
                    } finally { export.close(); }
                } catch (Exception error) { throw new AssertionError(error); }
            });
            getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(() -> {
                android.view.View root = dialog[0].getWindow().getDecorView();
                android.widget.TextView heading = findText(root, "Края карточек"); assertNotNull(heading);
                android.widget.ScrollView scroll = find(root, android.widget.ScrollView.class);
                scroll.scrollTo(0, heading.getTop());
            });
            getInstrumentation().waitForIdleSync(); android.os.SystemClock.sleep(350);
            Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
            save(screenshot, "workshop-card-edges.png"); screenshot.recycle();
        } finally {
            prefs.edit().putStringSet("owned", oldOwned).putString("selected_card_themes", oldTheme)
                .putString("selected_card_edges", oldEdge).commit();
            getInstrumentation().runOnMainSync(() -> { if (dialog[0] != null) dialog[0].dismiss(); });
        }
    }

    private android.widget.TextView findText(android.view.View view, String text) {
        if (view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView) view).getText()))
            return (android.widget.TextView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.TextView result = findText(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private android.widget.TextView findTextStarting(android.view.View view, String text) {
        if (view instanceof android.widget.TextView
                && ((android.widget.TextView) view).getText().toString().startsWith(text))
            return (android.widget.TextView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.TextView result = findTextStarting(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void touch(android.view.View view, int action, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent event = android.view.MotionEvent.obtain(now, now, action, x, y, 0);
        view.dispatchTouchEvent(event); event.recycle();
    }

    private <T> T find(android.view.View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T result = find(group.getChildAt(i), type);
                if (result != null) return result;
            }
        }
        return null;
    }

    public void testZipContainsRequestedPartsAndManifest() throws Exception {
        TreeImageExport[] ref = new TreeImageExport[1];
        getInstrumentation().runOnMainSync(() -> {
            ref[0] = new TreeImageExport(activity);
            ref[0].verticalCuts = 2; ref[0].horizontalCuts = 2;
        });
        TreeImageExport export = ref[0];
        ExportLayout layout = export.layout(2f, 4_000_000);
        File file = new File(artifacts, "parts.zip");
        try {
            Method write = MainActivityFiles.class.getDeclaredMethod("writeImageArchive", TreeImageExport.class,
                ExportLayout.class, java.io.OutputStream.class);
            write.setAccessible(true);
            try (FileOutputStream out = new FileOutputStream(file)) {
                write.invoke(new MainActivityFiles(activity), export, layout, out);
            }
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
                assertEquals(11, zip.size());
                assertNotNull(zip.getEntry("overview.png"));
                try (java.io.InputStream in = zip.getInputStream(zip.getEntry("manifest.json"))) {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
                    org.json.JSONObject manifest = new org.json.JSONObject(bytes.toString("UTF-8"));
                    assertEquals(3, manifest.getInt("rows")); assertEquals(3, manifest.getInt("columns"));
                    assertEquals(9, manifest.getJSONArray("tiles").length());
                }
            }
        } finally { getInstrumentation().runOnMainSync(export::close); }
    }

    public void testWidgetFrameAndTransparency() throws Exception {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("androidft-rewards", 0);
        java.util.Set<String> owned = new java.util.HashSet<>(prefs.getStringSet("owned", java.util.Collections.emptySet()));
        String selected = prefs.getString("selected_widget_frames", "standard");
        java.util.Set<String> next = new java.util.HashSet<>(owned);
        next.add("widget_frame_family"); next.add("widget_frame_fire"); next.add("widget_frame_botanical");
        try {
            BirthdayWidgetSettings settings = new BirthdayWidgetSettings();
            for (String frame : new String[] {"widget_frame_family", "widget_frame_fire", "widget_frame_botanical"}) {
                prefs.edit().putStringSet("owned", next).putString("selected_widget_frames", frame).commit();
                Bitmap background = BirthdayWidgetRenderer.renderBackground(activity, null, settings, 320, 150);
                save(background, frame + ".png"); background.recycle();
            }
            settings.backgroundEnabled = false;
            Bitmap background = BirthdayWidgetRenderer.renderBackground(activity, null, settings, 180, 70);
            int[] pixels = new int[background.getWidth() * background.getHeight()];
            background.getPixels(pixels, 0, background.getWidth(), 0, 0, background.getWidth(), background.getHeight());
            for (int pixel : pixels) assertEquals(0, Color.alpha(pixel));
            background.recycle();
        } finally {
            prefs.edit().putStringSet("owned", owned).putString("selected_widget_frames", selected).commit();
        }
    }
}
