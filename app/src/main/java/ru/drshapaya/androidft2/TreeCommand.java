package ru.drshapaya.androidft2;

interface TreeCommand {
    void undo(TreeState state);
    void redo(TreeState state);
    int estimatedBytes();
    String label();
}
