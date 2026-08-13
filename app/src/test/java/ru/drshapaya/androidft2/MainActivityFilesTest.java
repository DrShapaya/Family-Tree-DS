package ru.drshapaya.androidft2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MainActivityFilesTest {
    @Test
    public void openingAnotherTreeWarnsBeforeReplacingNonEmptyState() {
        TreeState current = new TreeState();
        current.people.put("current", new Person("current"));
        TreeState imported = new TreeState();
        imported.people.put("imported", new Person("imported"));

        assertTrue(MainActivityFiles.shouldWarnBeforeReplacing(current, imported));
    }

    @Test
    public void openingFirstTreeOnEmptyWorkspaceDoesNotWarn() {
        TreeState current = new TreeState();
        TreeState imported = new TreeState();
        imported.people.put("imported", new Person("imported"));

        assertFalse(MainActivityFiles.shouldWarnBeforeReplacing(current, imported));
        assertFalse(MainActivityFiles.shouldWarnBeforeReplacing(null, imported));
        assertFalse(MainActivityFiles.shouldWarnBeforeReplacing(current, null));
    }
}
