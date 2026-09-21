package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void preservesConfiguredOrderAndDisplayLimit() {
        SupporterCatalog.RemoteData data = SupporterCatalog.parseData(
            "{\"displayLimit\":3,\"supporters\":["
                + "{\"name\":\"Второй\",\"amount\":500,\"order\":1},"
                + "{\"name\":\"Первый\",\"amount\":100,\"order\":0},"
                + "{\"name\":\"Третий\",\"amount\":900,\"order\":2},"
                + "{\"name\":\"Четвёртый\",\"amount\":1200,\"order\":3}]}"
        );

        assertEquals(3, data.displayLimit);
        assertEquals("Первый", data.supporters.get(0).name);
        assertEquals("Второй", data.supporters.get(1).name);
    }

    @Test
    public void preservesColorAndHeartWithoutDroppingSupporter() {
        SupporterCatalog.RemoteData data = SupporterCatalog.parseData(
            "{\"supporters\":["
                + "{\"name\":\"С сердцем\",\"amount\":500,\"color\":\"#E8789D\",\"showHeart\":true},"
                + "{\"name\":\"Обычный\",\"amount\":300,\"color\":\"wrong\"}]}"
        );

        assertEquals(2, data.supporters.size());
        assertEquals("#E8789D", data.supporters.get(0).color);
        assertTrue(data.supporters.get(0).showHeart);
        assertEquals("#087A73", data.supporters.get(1).color);
        assertFalse(data.supporters.get(1).showHeart);
    }

    @Test
    public void calculatesFundraiserProgressFromAdminPanelDonations() {
        SupporterCatalog.RemoteData data = SupporterCatalog.parseData(
            "{\"fundraiser\":{\"goal\":30000,\"raised\":0,\"currency\":\"RUB\"},"
                + "\"supporters\":["
                + "{\"name\":\"Первый\",\"amount\":67,\"currency\":\"RUB\"},"
                + "{\"name\":\"Второй\",\"amount\":42,\"currency\":\"RUB\"}]}"
        );

        assertEquals(109d, data.fundraiser.raised, 0d);
    }

    @Test
    public void keepsHigherManualFundraiserAmountAndIgnoresOtherCurrencies() {
        SupporterCatalog.RemoteData data = SupporterCatalog.parseData(
            "{\"fundraiser\":{\"goal\":30000,\"raised\":500,\"currency\":\"RUB\"},"
                + "\"supporters\":["
                + "{\"name\":\"Рубли\",\"amount\":100,\"currency\":\"RUB\"},"
                + "{\"name\":\"Другая валюта\",\"amount\":1000,\"currency\":\"USD\"}]}"
        );

        assertEquals(500d, data.fundraiser.raised, 0d);
    }
}
