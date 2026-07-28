package ru.drshapaya.androidft2;

import android.content.Context;
import android.os.Build;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

final class DiagnosticsLogger {
    private static final long MAX_LOG_BYTES = 512L * 1024L;
    private static final int MAX_BREADCRUMBS = 40;
    private static final ArrayDeque<String> breadcrumbs = new ArrayDeque<>();
    private static int peopleCount;
    private static int linkCount;
    private static int guideCount;

    private DiagnosticsLogger() {
    }

    static synchronized void breadcrumb(Context context, String event) {
        String safe = event == null ? "" : event.replaceAll("[^a-zA-Z0-9._=-]", "");
        if (safe.isEmpty()) return;
        String line = timestamp() + " " + safe;
        breadcrumbs.addLast(line);
        while (breadcrumbs.size() > MAX_BREADCRUMBS) breadcrumbs.removeFirst();
        append(context, line + "\n");
    }

    static synchronized void updateStateMetrics(TreeState state) {
        peopleCount = state == null ? 0 : state.people.size();
        linkCount = state == null ? 0 : state.links.size();
        guideCount = state == null ? 0 : state.guides.size();
    }

    static synchronized void handled(Context context, String area, Throwable error) {
        String type = error == null ? "Unknown" : error.getClass().getName();
        append(context, timestamp() + " handled area=" + safeArea(area) + " type=" + type + "\n");
    }

    static synchronized void crash(Context context, Throwable error) {
        StringBuilder report = new StringBuilder(4096);
        Runtime runtime = Runtime.getRuntime();
        report.append("\n").append(timestamp()).append(" CRASH\n")
            .append("appVersion=").append(MainActivity.VERSION_NAME).append('\n')
            .append("android=").append(Build.VERSION.SDK_INT).append('\n')
            .append("device=").append(Build.MANUFACTURER).append('/').append(Build.MODEL).append('\n')
            .append("state people=").append(peopleCount)
            .append(" links=").append(linkCount)
            .append(" guides=").append(guideCount).append('\n')
            .append("heap used=").append(runtime.totalMemory() - runtime.freeMemory())
            .append(" max=").append(runtime.maxMemory()).append('\n');
        if (!breadcrumbs.isEmpty()) {
            report.append("breadcrumbs:\n");
            for (String breadcrumb : breadcrumbs) report.append("  ").append(breadcrumb).append('\n');
        }
        if (error != null) {
            report.append("exception=").append(error.getClass().getName()).append('\n');
            StackTraceElement[] stack = error.getStackTrace();
            int count = Math.min(80, stack == null ? 0 : stack.length);
            for (int i = 0; i < count; i++) report.append("  at ").append(stack[i]).append('\n');
            Throwable cause = error.getCause();
            if (cause != null && cause != error) {
                report.append("cause=").append(cause.getClass().getName()).append('\n');
                StackTraceElement[] causeStack = cause.getStackTrace();
                int causeCount = Math.min(32, causeStack == null ? 0 : causeStack.length);
                for (int i = 0; i < causeCount; i++) {
                    report.append("  at ").append(causeStack[i]).append('\n');
                }
            }
        }
        append(context, report.toString());
    }

    private static void append(Context context, String text) {
        if (context == null || text == null || text.isEmpty()) return;
        try {
            File directory = new File(context.getFilesDir(), "diagnostics");
            if (!directory.exists() && !directory.mkdirs()) return;
            File file = new File(directory, "androidft.log");
            if (file.length() >= MAX_LOG_BYTES) rotate(directory, file);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file, true),
                StandardCharsets.UTF_8))) {
                writer.write(text);
            }
        } catch (Exception ignored) {
            // Diagnostics must never become a new crash source.
        }
    }

    private static void rotate(File directory, File current) {
        File oldest = new File(directory, "androidft.3.log");
        if (oldest.exists()) oldest.delete();
        for (int index = 2; index >= 1; index--) {
            File source = new File(directory, "androidft." + index + ".log");
            if (source.exists()) source.renameTo(new File(directory, "androidft." + (index + 1) + ".log"));
        }
        current.renameTo(new File(directory, "androidft.1.log"));
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
    }

    private static String safeArea(String value) {
        String safe = value == null ? "" : value.replaceAll("[^a-zA-Z0-9._-]", "");
        return safe.isEmpty() ? "unknown" : safe;
    }
}
