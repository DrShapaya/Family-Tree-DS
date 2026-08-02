package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TreeStateTest {
    @Test
    public void newPeopleUseSurnameColorByDefault() {
        TreeState state = new TreeState();

        Person person = state.addPerson("Иванов Иван", 0f, 0f);

        assertEquals("auto-surname", person.colorMode);
        assertEquals(TreeState.colorFor("иванов", 0), person.color);
    }
}
