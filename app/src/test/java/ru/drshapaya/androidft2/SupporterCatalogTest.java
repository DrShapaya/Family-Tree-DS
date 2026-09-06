package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public final class SupporterCatalogTest {
    @Test
    public void sortsSupportersAndCalculatesTenPercentRecord() {
        List<SupporterCatalog.Entry> entries = SupporterCatalog.parse(
            "{\"supporters\":["
                + "{\"name\":\"Первый\",\"amount\":100,\"currency\":\"rub\"},"
                + "{\"name\":\"Лидер\",\"amount\":250,\"currency\":\"RUB\"}]}"
        );

        assertEquals(2, entries.size());
        assertEquals("Лидер", entries.get(0).name);
        assertEquals("RUB", entries.get(0).currency);
        assertEquals(275L, SupporterCatalog.amountToBeat(entries.get(0)));
    }

    @Test
    public void ignoresInvalidAmounts() {
        List<SupporterCatalog.Entry> entries = SupporterCatalog.parse(
            "{\"supporters\":["
                + "{\"name\":\"Ноль\",\"amount\":0},"
                + "{\"name\":\"Верный\",\"amount\":99.5}]}"
        );

        assertEquals(1, entries.size());
        assertEquals(110L, SupporterCatalog.amountToBeat(entries.get(0)));
    }
}
