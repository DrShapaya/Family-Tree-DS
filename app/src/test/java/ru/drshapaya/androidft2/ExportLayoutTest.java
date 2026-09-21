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
