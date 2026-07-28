package ru.drshapaya.androidft2;

final class Relation {
    String id;
    String type;
    String from;
    String to;
    String side = "right";

    Relation(String id, String type, String from, String to) {
        this.id = id;
        this.type = type;
        this.from = from;
        this.to = to;
    }

    Relation(String id, String type, String from, String to, String side) {
        this(id, type, from, to);
        this.side = "left".equals(side) ? "left" : "right";
    }
}
