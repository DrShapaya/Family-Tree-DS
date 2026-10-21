package ru.drshapaya.androidft2;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExportLayoutTest {
    @Test public void everyCutCombinationCoversImageExactlyWithoutGaps() {
        for (int vertical = 0; vertical <= 5; vertical++) {
            for (int horizontal = 0; horizontal <= 5; horizontal++) {
                ExportLayout layout = new ExportLayout(913.7f, 581.3f, 2.1f, 100_000, vertical, horizontal);
                long area = 0;
                for (int y = 0; y < layout.rows; y++) for (int x = 0; x < layout.columns; x++) {
                    int w = layout.x(x + 1) - layout.x(x), h = layout.y(y + 1) - layout.y(y);
                    assertTrue(w > 0 && h > 0);
                    assertTrue((long) w * h <= 100_000);
                    area += (long) w * h;
                }
                assertEquals((long) layout.width * layout.height, area);
                assertEquals(0, layout.x(0)); assertEquals(0, layout.y(0));
                assertEquals(layout.width, layout.x(layout.columns));
                assertEquals(layout.height, layout.y(layout.rows));
            }
        }
    }

    @Test public void longThinAndHugeTreesRespectBitmapLimits() {
        for (float[] shape : new float[][] {{1, 1_000_000}, {1_000_000, 1}, {1_000_000, 1_000_000}}) {
            ExportLayout layout = new ExportLayout(shape[0], shape[1], 16, 2_000_000, 2, 2);
            assertTrue(layout.width <= 28000 && layout.height <= 28000);
            assertTrue((long) ((layout.width + 2) / 3) * ((layout.height + 2) / 3) <= 2_000_000);
            assertTrue((layout.width + 2) / 3 <= ExportLayout.MAX_BITMAP_SIDE);
            assertTrue((layout.height + 2) / 3 <= ExportLayout.MAX_BITMAP_SIDE);
        }
    }

    @Test public void bitmapBudgetScalesWithHeapAndStaysWithinSafeBounds() {
        assertEquals(1_000_000L, ExportLayout.safePixelsForHeap(16L * 1024L * 1024L));
        assertEquals(4_194_304L, ExportLayout.safePixelsForHeap(128L * 1024L * 1024L));
        assertEquals(8_388_608L, ExportLayout.safePixelsForHeap(256L * 1024L * 1024L));
        assertEquals(12_000_000L, ExportLayout.safePixelsForHeap(1024L * 1024L * 1024L));
    }

    @Test public void randomizedLargeLayoutsNeverExceedPerBitmapLimits() {
        java.util.Random random = new java.util.Random(0x504E47L);
        for (int iteration = 0; iteration < 10_000; iteration++) {
            float worldWidth = (float) Math.pow(10d, random.nextDouble() * 6d);
            float worldHeight = (float) Math.pow(10d, random.nextDouble() * 6d);
            float requestedScale = 0.05f + random.nextFloat() * 15.95f;
            long budget = 4_096L + (long) (random.nextDouble() * 11_995_904L);
            int verticalCuts = random.nextInt(6);
            int horizontalCuts = random.nextInt(6);
            ExportLayout layout = new ExportLayout(
                worldWidth,
                worldHeight,
                requestedScale,
                budget,
                verticalCuts,
                horizontalCuts);

            assertTrue(layout.width >= layout.columns && layout.width <= 28_000);
            assertTrue(layout.height >= layout.rows && layout.height <= 28_000);
            long covered = 0L;
            for (int row = 0; row < layout.rows; row++) {
                for (int column = 0; column < layout.columns; column++) {
                    int width = layout.x(column + 1) - layout.x(column);
                    int height = layout.y(row + 1) - layout.y(row);
                    assertTrue(width > 0 && width <= ExportLayout.MAX_BITMAP_SIDE);
                    assertTrue(height > 0 && height <= ExportLayout.MAX_BITMAP_SIDE);
                    assertTrue((long) width * height <= budget);
                    covered += (long) width * height;
                }
            }
            assertEquals((long) layout.width * layout.height, covered);
        }
    }

    @Test public void fitPdfKeepsEntireTreeOnPageAndDoesNotEnlargeSmallTrees() {
        assertEquals(1f, ExportLayout.fitPdfScale(200, 100, 786, 521), 0.0001f);
        for (float[] shape : new float[][] {{12000, 4000}, {4000, 12000}, {8000, 8000}}) {
            float scale = ExportLayout.fitPdfScale(shape[0], shape[1], 786, 521);
            assertTrue(shape[0] * scale <= 786.01f);
            assertTrue(shape[1] * scale <= 521.01f);
        }
    }
}
