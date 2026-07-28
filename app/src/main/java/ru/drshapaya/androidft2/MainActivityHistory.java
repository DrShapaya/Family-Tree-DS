package ru.drshapaya.androidft2;

import android.graphics.PointF;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

final class MainActivityHistory {
    private static final int MAX_COMMANDS = 80;
    private static final long MAX_COMMAND_BYTES = 12L * 1024L * 1024L;

    private final MainActivity activity;
    private TreeState pendingBaseline;
    private TreeCommand pendingCommand;
    private String pendingLabel = "";
    private long undoBytes = 0L;

    MainActivityHistory(MainActivity activity) {
        this.activity = activity;
    }

    void recordUndo(String label, String detail) {
        commitPendingUndo();
        pendingBaseline = TreeStateCopier.copy(activity.state);
        pendingCommand = null;
        pendingLabel = label == null || label.trim().isEmpty()
            ? "последнее действие"
            : label.trim();
        activity.redoStack.clear();
        recordAction(label, detail);
        updateHistoryButtons();
    }

    void commitPendingUndo() {
        if (pendingBaseline == null || activity.state == null) return;
        TreeDeltaCommand next = TreeDeltaCommand.between(
            pendingBaseline,
            activity.state,
            pendingLabel);
        if (pendingCommand != null && activity.undoStack.peek() == pendingCommand) {
            activity.undoStack.pop();
            undoBytes -= pendingCommand.estimatedBytes();
        }
        pendingCommand = null;
        if (!next.isEmpty()) {
            activity.undoStack.push(next);
            pendingCommand = next;
            undoBytes += next.estimatedBytes();
            trimUndo();
        }
        updateHistoryButtons();
    }

    private void trimUndo() {
        while (activity.undoStack.size() > MAX_COMMANDS || undoBytes > MAX_COMMAND_BYTES) {
            TreeCommand removed = activity.undoStack.pollLast();
            if (removed == null) break;
            undoBytes = Math.max(0L, undoBytes - removed.estimatedBytes());
            if (removed == pendingCommand) {
                pendingCommand = null;
                pendingBaseline = null;
            }
        }
    }

    void recordMove(
        Map<String, PointF> before,
        Map<String, PointF> after,
        String detail
    ) {
        commitPendingUndo();
        pendingBaseline = null;
        pendingCommand = null;
        MovePeopleCommand command = new MovePeopleCommand(
            before,
            after,
            "Перемещена карточка");
        if (command.isEmpty()) return;
        activity.redoStack.clear();
        activity.undoStack.push(command);
        undoBytes += command.estimatedBytes();
        trimUndo();
        recordAction("Перемещена карточка", detail);
        updateHistoryButtons();
    }

    void cancelPendingUndo() {
        if (pendingCommand != null && activity.undoStack.peek() == pendingCommand) {
            activity.undoStack.pop();
            undoBytes = Math.max(0L, undoBytes - pendingCommand.estimatedBytes());
        }
        pendingBaseline = null;
        pendingCommand = null;
        pendingLabel = "";
        updateHistoryButtons();
    }

    void recordAction(String label, String detail) {
        if (activity.state == null || label == null || label.trim().isEmpty()) return;
        HistoryEntry entry = new HistoryEntry();
        entry.id = "h_" + java.util.UUID.randomUUID().toString().replace("-", "");
        entry.label = safeHistoryText(label, 120);
        entry.detail = safeHistoryText(detail, 160);
        entry.at = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date());
        activity.state.history.add(0, entry);
        while (activity.state.history.size() > 30) activity.state.history.remove(activity.state.history.size() - 1);
        updateHistoryPanel();
    }

    void undo() {
        commitPendingUndo();
        if (activity.undoStack.isEmpty()) return;
        try {
            pendingBaseline = null;
            pendingCommand = null;
            TreeCommand command = activity.undoStack.pop();
            undoBytes = Math.max(0L, undoBytes - command.estimatedBytes());
            command.undo(activity.state);
            activity.redoStack.push(command);
            activity.saveOnly();
            activity.bindState();
        } catch (Exception error) {
            activity.toast("Не удалось отменить");
        }
    }

    void redo() {
        if (activity.redoStack.isEmpty()) return;
        try {
            pendingBaseline = null;
            pendingCommand = null;
            TreeCommand command = activity.redoStack.pop();
            command.redo(activity.state);
            activity.undoStack.push(command);
            undoBytes += command.estimatedBytes();
            trimUndo();
            activity.saveOnly();
            activity.bindState();
        } catch (Exception error) {
            activity.toast("Не удалось вернуть");
        }
    }

    void updateHistoryButtons() {
        activity.styleHistoryButton(activity.undoBtn, !activity.undoStack.isEmpty());
        activity.styleHistoryButton(activity.redoBtn, !activity.redoStack.isEmpty());
        updateHistoryPanel();
    }

    void updateHistoryPanel() {
        if (activity.historyPanel == null || activity.historyList == null) return;
        boolean visible = activity.state != null && !activity.state.historyHidden && activity.activePanel.isEmpty();
        activity.historyPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        activity.historyList.removeAllViews();
        if (activity.historyHint != null) {
            String undoText = activity.undoStack.isEmpty() ? "Undo: нет" : "Undo: " + currentActionLabel();
            String redoText = activity.redoStack.isEmpty() ? "Redo: нет" : "Redo: " + actionLabelFromSnapshot(activity.redoStack.peek());
            activity.historyHint.setText(undoText + " · " + redoText);
        }
        int count = activity.state.history == null ? 0 : Math.min(8, activity.state.history.size());
        if (count == 0) {
            TextView empty = new TextView(activity);
            empty.setText("Действий пока нет");
            empty.setTextColor(Color.rgb(101, 113, 122));
            empty.setTextSize(12);
            empty.setTypeface(activity.uiBold());
            empty.setIncludeFontPadding(false);
            activity.historyList.addView(empty, historyItemParams());
            return;
        }
        for (int i = 0; i < count; i++) activity.historyList.addView(historyItem(activity.state.history.get(i)), historyItemParams());
    }

    View historyItem(HistoryEntry entry) {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(8));
        item.setBackground(activity.panelBg(Color.WHITE, activity.dp(8), Color.rgb(217, 224, 229)));

        TextView label = new TextView(activity);
        String detail = entry.detail == null || entry.detail.isEmpty() ? "" : " · " + entry.detail;
        label.setText((entry.label == null || entry.label.isEmpty() ? "Действие" : entry.label) + detail);
        label.setTextColor(Color.rgb(28, 34, 38));
        label.setTextSize(12);
        label.setTypeface(activity.uiBold());
        label.setIncludeFontPadding(false);
        item.addView(label);

        TextView time = new TextView(activity);
        time.setText(historyTime(entry.at));
        time.setTextColor(Color.rgb(101, 113, 122));
        time.setTextSize(11);
        time.setTypeface(activity.ui());
        time.setIncludeFontPadding(false);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-1, -2);
        timeParams.setMargins(0, activity.dp(3), 0, 0);
        item.addView(time, timeParams);
        return item;
    }

    String currentActionLabel() {
        TreeCommand command = activity.undoStack.peek();
        if (command != null && command.label() != null && !command.label().isEmpty()) return command.label();
        return "последнее действие";
    }

    LinearLayout.LayoutParams historyItemParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, activity.dp(7));
        return params;
    }

    String actionLabelFromSnapshot(TreeCommand command) {
        return command == null || command.label() == null || command.label().isEmpty()
            ? "последнее действие"
            : command.label();
    }

    static String historyTime(String at) {
        if (at != null && at.length() >= 16 && at.charAt(4) == '-' && at.charAt(7) == '-') {
            return at.substring(8, 10) + "." + at.substring(5, 7) + " " + at.substring(11, 16);
        }
        try {
            long millis = Long.parseLong(at == null ? "" : at);
            return new SimpleDateFormat("dd.MM HH:mm", Locale.US).format(new Date(millis));
        } catch (Exception ignored) {
            return new SimpleDateFormat("dd.MM HH:mm", Locale.US).format(new Date());
        }
    }

    static String safeHistoryText(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) text = text.substring(0, max);
        return text;
    }
}
