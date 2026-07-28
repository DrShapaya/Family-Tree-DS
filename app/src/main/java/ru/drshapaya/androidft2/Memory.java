package ru.drshapaya.androidft2;

final class Memory {
    String id = "";
    String type = "story";
    String title = "Воспоминание";
    String text = "";
    final java.util.List<MemoryAttachment> attachments = new java.util.ArrayList<>();
    // Одиночные поля оставлены для совместимости со старыми деревьями.
    String filename = "";
    String mimeType = "";
    String data = "";
    String at = "";
}
