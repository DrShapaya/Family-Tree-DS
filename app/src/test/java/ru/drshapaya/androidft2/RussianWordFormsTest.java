package ru.drshapaya.androidft2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class RussianWordFormsTest {
    @Test
    public void selectsRussianPluralForms() {
        assertEquals("карточек", form(0));
        assertEquals("карточка", form(1));
        assertEquals("карточки", form(2));
        assertEquals("карточек", form(5));
        assertEquals("карточек", form(11));
        assertEquals("карточек", form(14));
        assertEquals("карточка", form(21));
        assertEquals("карточки", form(22));
        assertEquals("карточек", form(25));
    }

    @Test
    public void negativeCountsUseTheirAbsoluteValue() {
        assertEquals("карточка", form(-21));
        assertEquals("карточки", form(-22));
    }

    private static String form(int count) {
        return RussianWordForms.forCount(
            count,
            "карточка",
            "карточки",
            "карточек");
    }
}
