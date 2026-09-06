package ru.drshapaya.androidft2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BirthdayWidgetRendererTest {
    @Test
    public void compactPhotoNameOmitsPatronymic() {
        assertEquals(
            "Иванов Иван",
            BirthdayWidgetRenderer.withoutPatronymic("Иванов   Иван Иванович"));
    }

    @Test
    public void compactPhotoNameKeepsTwoPartName() {
        assertEquals(
            "Anna Smith",
            BirthdayWidgetRenderer.withoutPatronymic("Anna Smith"));
    }
}
