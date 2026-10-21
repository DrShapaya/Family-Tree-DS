package ru.drshapaya.androidft2;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Locale;

/** Formats and shares a person's biography, optionally with a photo. */
final class MainActivityBiographyShare {
    private final MainActivity activity;

    MainActivityBiographyShare(MainActivity activity) {
        this.activity = activity;
    }

    void shareBiography(Person person) {
        if (person == null) return;
        String subject = person.name == null ? "" : person.name.trim();
        String biography = biographyText(person);
        String mediaId = person.photoMediaId == null ? "" : person.photoMediaId.trim();
        String dataUrl = person.photo == null ? "" : person.photo.trim();
        if (mediaId.isEmpty() && dataUrl.isEmpty()) {
            startBiographyShare(subject, biography, null, "text/plain");
            return;
        }
        new Thread(() -> {
            try {
                String extension = biographyPhotoExtension(mediaId, dataUrl);
                String mime = "png".equals(extension)
                    ? "image/png"
                    : "webp".equals(extension) ? "image/webp" : "image/jpeg";
                File shared = new File(activity.getCacheDir(), "shared");
                if (!shared.isDirectory() && !shared.mkdirs()) {
                    throw new java.io.IOException("Не удалось подготовить фотографию");
                }
                File photo = new File(shared, "biography-photo-" + System.currentTimeMillis() + "." + extension);
                try (OutputStream output = new FileOutputStream(photo)) {
                    if (!mediaId.isEmpty() && activity.store.mediaStore().exists(mediaId)) {
                        activity.store.mediaStore().copyTo(mediaId, output);
                    } else {
                        int comma = dataUrl.indexOf(',');
                        if (comma < 0 || comma + 1 >= dataUrl.length()) {
                            throw new java.io.IOException("Фотография повреждена");
                        }
                        output.write(Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT));
                    }
                    output.flush();
                }
                Uri photoUri = TreeShareProvider.uriFor(photo.getName());
                activity.runOnUiThread(() -> startBiographyShare(subject, biography, photoUri, mime));
            } catch (Exception error) {
                DiagnosticsLogger.handled(activity, "biography.share.photo", error);
                activity.runOnUiThread(() -> {
                    activity.toast("Фото не удалось приложить, отправляю текст");
                    startBiographyShare(subject, biography, null, "text/plain");
                });
            }
        }, "biography-photo-share").start();
    }

    private String biographyText(Person person) {
        StringBuilder biography = new StringBuilder();
        biography.append(person.name == null || person.name.trim().isEmpty() ? activity.tr("Без имени") : person.name.trim());
        String gender = PersonGender.resolve(person);
        if (PersonGender.MALE.equals(gender)) biography.append("\nПол: мужской");
        else if (PersonGender.FEMALE.equals(gender)) biography.append("\nПол: женский");
        int age = personAge(person);
        if (age >= 0) {
            biography.append("\nВозраст: ").append(age).append(" ")
                .append(RussianWordForms.forCount(age, "год", "года", "лет"));
        }
        String born = activity.humanDate(person.bornDay, person.bornMonth, person.bornYear);
        String died = activity.humanDate(person.diedDay, person.diedMonth, person.diedYear);
        if (!born.isEmpty() && !died.isEmpty()) biography.append("\n").append(born).append(" — ").append(died);
        else if (!born.isEmpty()) biography.append("\nДата рождения: ").append(born);
        else if (!died.isEmpty()) biography.append("\nДата смерти: ").append(died);
        if (person.place != null && !person.place.trim().isEmpty()) biography.append("\n").append(person.place.trim());
        if (person.notes != null && !person.notes.trim().isEmpty()) biography.append("\n\n").append(person.notes.trim());
        if (!person.memories.isEmpty()) {
            biography.append("\n\n").append(activity.tr("Память")).append(":");
            int limit = Math.min(8, person.memories.size());
            for (int index = 0; index < limit; index++) {
                Memory memory = person.memories.get(index);
                String title = memory.title == null || memory.title.trim().isEmpty()
                    ? activity.tr("Воспоминание")
                    : memory.title.trim();
                biography.append("\n• ").append(title);
                if (memory.text != null && !memory.text.trim().isEmpty()) {
                    biography.append(": ").append(memory.text.trim());
                }
            }
        }
        return biography.toString();
    }

    private int personAge(Person person) {
        int bornYear = positiveYear(person == null ? "" : person.bornYear);
        if (bornYear <= 0) return -1;
        Calendar endpoint = Calendar.getInstance();
        int diedYear = positiveYear(person.diedYear);
        boolean deceased = diedYear > 0;
        int endYear = deceased ? diedYear : endpoint.get(Calendar.YEAR);
        int age = endYear - bornYear;
        if (age < 0) return -1;
        int bornMonth = positiveNumber(person.bornMonth, 1, 12);
        int bornDay = positiveNumber(person.bornDay, 1, 31);
        int endMonth = deceased ? positiveNumber(person.diedMonth, 1, 12) : endpoint.get(Calendar.MONTH) + 1;
        int endDay = deceased ? positiveNumber(person.diedDay, 1, 31) : endpoint.get(Calendar.DAY_OF_MONTH);
        if (bornMonth > 0 && endMonth > 0
            && (endMonth < bornMonth || endMonth == bornMonth && bornDay > 0 && endDay > 0 && endDay < bornDay)) {
            age--;
        }
        return Math.max(-1, age);
    }

    private int positiveYear(String value) {
        return positiveNumber(value, 1, 9999);
    }

    private int positiveNumber(String value, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed >= minimum && parsed <= maximum ? parsed : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String biographyPhotoExtension(String mediaId, String dataUrl) {
        String source = mediaId == null ? "" : mediaId.toLowerCase(Locale.ROOT);
        int dot = source.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < source.length()) {
            String extension = source.substring(dot + 1);
            if ("png".equals(extension) || "webp".equals(extension) || "jpg".equals(extension)) return extension;
            if ("jpeg".equals(extension)) return "jpg";
        }
        String legacy = dataUrl == null ? "" : dataUrl.toLowerCase(Locale.ROOT);
        if (legacy.startsWith("data:image/png")) return "png";
        if (legacy.startsWith("data:image/webp")) return "webp";
        return "jpg";
    }

    private void startBiographyShare(String subject, String biography, Uri photoUri, String mime) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.putExtra(Intent.EXTRA_TEXT, biography);
        if (photoUri != null) {
            send.putExtra(Intent.EXTRA_STREAM, photoUri);
            send.setClipData(ClipData.newRawUri("Фото", photoUri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        activity.startActivity(Intent.createChooser(send, activity.tr("Поделиться биографией")));
    }

}
