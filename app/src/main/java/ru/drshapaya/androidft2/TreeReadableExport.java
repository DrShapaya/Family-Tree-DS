package ru.drshapaya.androidft2;

import org.json.JSONObject;
import java.io.IOException;
import java.io.Writer;

/** Offline presentation exports; .ftree remains the lossless backup format. */
final class TreeReadableExport {
    interface Photos { String portrait(Person person) throws Exception; }

    static void html(TreeState state, Writer out, String template, boolean english, Photos photos) throws Exception {
        String marker = "__TREE_DATA__";
        int split = template.indexOf(marker);
        if (split < 0) throw new IOException("HTML template is missing its data marker");
        out.write(template.substring(0, split));
        out.write("{\"english\":" + english + ",\"root\":" + json(JSONObject.quote(state.rootId)) + ",\"people\":[");
        int index = 0;
        for (Person p : state.people.values()) {
            if (index > 0) out.write(",");
            JSONObject person = new JSONObject().put("id", p.id).put("name", PersonNames.display(p, state.useMaidenNames))
                .put("fullName", p.name).put("maidenName", p.maidenName)
                .put("born", date(p, true)).put("died", date(p, false)).put("place", p.place)
                .put("notes", p.notes).put("photo", safePhoto(photos.portrait(p)))
                .put("x", Float.isFinite(p.x) ? p.x : (index % 8) * 250)
                .put("y", Float.isFinite(p.y) ? p.y : (index / 8) * 150);
            org.json.JSONArray memories = new org.json.JSONArray();
            for (Memory memory : p.memories) memories.put(new JSONObject().put("title", memory.title).put("text", memory.text));
            person.put("memories", memories);
            out.write(json(person.toString()));
            index++;
        }
        out.write("],\"links\":[");
        boolean first = true;
        for (Relation r : state.links) {
            if (!state.people.containsKey(r.from) || !state.people.containsKey(r.to)) continue;
            if (!first) out.write(",");
            first = false;
            out.write(json(new JSONObject().put("from", r.from).put("to", r.to).put("type", r.type).toString()));
        }
        out.write("]}");
        out.write(template.substring(split + marker.length()));
    }

    static void xml(TreeState state, Writer out, Photos photos) throws Exception {
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.write("<family-tree xmlns=\"urn:family-tree-ds:export:1\" version=\"1\" root=\"" + escape(state.rootId) + "\">\n<people>\n");
        for (Person p : state.people.values()) {
            out.write("<person id=\"" + escape(p.id) + "\">\n");
            field(out, "name", p.name);
            field(out, "surname", p.surname); field(out, "given-name", p.givenName);
            field(out, "patronymic", p.patronymic); field(out, "maiden-name", p.maidenName);
            field(out, "name-parts-set", String.valueOf(p.namePartsSet));
            field(out, "maiden-name-enabled", String.valueOf(p.maidenNameEnabled));
            field(out, "surname-display", p.surnameDisplay);
            field(out, "gender", p.gender);
            field(out, "life-status", PersonLifeStatus.normalize(p.lifeStatus));
            xmlDate(out, "birth", p.bornDay, p.bornMonth, p.bornYear, p.born);
            xmlDate(out, "death", p.diedDay, p.diedMonth, p.diedYear, p.died);
            field(out, "birth-place", p.place);
            field(out, "notes", p.notes);
            String photo = safePhoto(photos.portrait(p));
            if (!photo.isEmpty()) field(out, "portrait", photo);
            if (Float.isFinite(p.x) && Float.isFinite(p.y)) out.write("<position x=\"" + p.x + "\" y=\"" + p.y + "\"/>\n");
            out.write("<memories>\n");
            for (Memory m : p.memories) {
                out.write("<memory id=\"" + escape(m.id) + "\" type=\"" + escape(m.type) + "\">\n");
                field(out, "title", m.title); field(out, "text", m.text); field(out, "date", m.at);
                out.write("</memory>\n");
            }
            out.write("</memories>\n</person>\n");
        }
        out.write("</people>\n<relationships>\n");
        for (Relation r : state.links) if (state.people.containsKey(r.from) && state.people.containsKey(r.to)) {
            out.write("<relationship id=\"" + escape(r.id) + "\" type=\"" + escape(r.type)
                + "\" from=\"" + escape(r.from) + "\" to=\"" + escape(r.to) + "\" side=\"" + escape(r.side) + "\"/>\n");
        }
        out.write("</relationships>\n");
        field(out, "use-maiden-names", String.valueOf(state.useMaidenNames));
        field(out, "show-quality-badge", String.valueOf(state.showQualityBadge));
        field(out, "show-life-status-badge", String.valueOf(state.showLifeStatusBadge));
        field(out, "life-status-appearance", state.lifeStatusAppearance.encode());
        out.write("</family-tree>\n");
    }

    private static void xmlDate(Writer out, String tag, String day, String month, String year, String text) throws IOException {
        out.write("<" + tag + " day=\"" + escape(day) + "\" month=\"" + escape(month) + "\" year=\"" + escape(year) + "\">" + escape(text) + "</" + tag + ">\n");
    }
    private static void field(Writer out, String tag, String value) throws IOException {
        out.write("<" + tag + ">" + escape(value) + "</" + tag + ">\n");
    }
    static String date(Person p, boolean birth) {
        String d = birth ? p.bornDay : p.diedDay, m = birth ? p.bornMonth : p.diedMonth, y = birth ? p.bornYear : p.diedYear;
        if (!d.isEmpty() && !m.isEmpty() && !y.isEmpty()) return d + "." + m + "." + y;
        if (!m.isEmpty() && !y.isEmpty()) return m + "." + y;
        return !y.isEmpty() ? y : (birth ? p.born : p.died);
    }
    static String json(String value) {
        return value.replace("&", "\\u0026").replace("<", "\\u003c").replace(">", "\\u003e")
            .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }
    static String safePhoto(String value) {
        return value != null && value.matches("(?s)^data:image/(jpeg|png|webp|gif|bmp);base64,[A-Za-z0-9+/=\\r\\n]+$") ? value : "";
    }
    static String escape(String text) {
        StringBuilder out = new StringBuilder();
        if (text == null) return "";
        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i); i += Character.charCount(c);
            if (!(c == 9 || c == 10 || c == 13 || (c >= 32 && c <= 0xd7ff) || (c >= 0xe000 && c <= 0xfffd) || (c >= 0x10000 && c <= 0x10ffff))) continue;
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&apos;"); break;
                default: out.appendCodePoint(c);
            }
        }
        return out.toString();
    }
}
