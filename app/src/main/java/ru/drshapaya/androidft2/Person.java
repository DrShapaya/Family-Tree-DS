package ru.drshapaya.androidft2;

final class Person {
    String id;
    String name = "";
    String born = "";
    String died = "";
    String bornDay = "";
    String bornMonth = "";
    String bornYear = "";
    String diedDay = "";
    String diedMonth = "";
    String diedYear = "";
    String place = "";
    String notes = "";
    String photoMediaId = "";
    String photo = "";
    float avatarScale = 1f;
    float avatarOffsetX = 0f;
    float avatarOffsetY = 0f;
    String gender = PersonGender.UNKNOWN;
    boolean genderManual = false;
    final java.util.List<Memory> memories = new java.util.ArrayList<>();
    String colorMode = "auto-name";
    String manualColor = "#84c7ae";
    int color = 0xff84c7ae;
    float x = Float.NaN;
    float y = Float.NaN;
    boolean pinned = false;

    Person(String id) {
        this.id = id;
    }
}
