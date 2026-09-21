package ru.drshapaya.androidft2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/** Excel-like 1×1 … 6×6 tile chooser, supporting touch, mouse and arrow keys. */
final class ExportCutGrid extends View {
    private static final int COLUMNS = 6;
    private static final int ROWS = 4;
    private final TreeImageExport export;
    private final Runnable changed;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    ExportCutGrid(Context context, TreeImageExport export, Runnable changed) {
        super(context);
        this.export = export;
        this.changed = changed;
        setFocusable(true);
        describe();
    }

    private void describe() {
        setContentDescription("Разрезы: " + export.verticalCuts + " по вертикали, "
            + export.horizontalCuts + " по горизонтали. Стрелки изменяют число разрезов.");
    }

    @Override protected void onDraw(Canvas canvas) {
        float cellW = getWidth() / (float) COLUMNS;
        float cellH = getHeight() / (float) ROWS;
        for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLUMNS; x++) {
            paint.setColor(x <= export.verticalCuts && y <= export.horizontalCuts
                ? Color.rgb(30, 162, 146) : Color.rgb(216, 228, 230));
            canvas.drawRoundRect(x * cellW + 2, y * cellH + 2, (x + 1) * cellW - 2,
                (y + 1) * cellH - 2, 3, 3, paint);
        }
    }

    private void choose(float x, float y) {
        export.verticalCuts = Math.max(0, Math.min(COLUMNS - 1, (int) (x * COLUMNS / getWidth())));
        export.horizontalCuts = Math.max(0, Math.min(ROWS - 1, (int) (y * ROWS / getHeight())));
        describe(); invalidate(); changed.run();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
        if (event.getActionMasked() != MotionEvent.ACTION_CANCEL) choose(event.getX(), event.getY());
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false); performClick();
        }
        return true;
    }

    @Override public boolean onHoverEvent(MotionEvent event) {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
                && event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE) choose(event.getX(), event.getY());
        return super.onHoverEvent(event);
    }

    @Override public boolean onKeyDown(int key, KeyEvent event) {
        int x = export.verticalCuts, y = export.horizontalCuts;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) x--;
        else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) x++;
        else if (key == KeyEvent.KEYCODE_DPAD_UP) y--;
        else if (key == KeyEvent.KEYCODE_DPAD_DOWN) y++;
        else return super.onKeyDown(key, event);
        choose((Math.max(0, Math.min(COLUMNS - 1, x)) + 0.5f) * getWidth() / COLUMNS,
            (Math.max(0, Math.min(ROWS - 1, y)) + 0.5f) * getHeight() / ROWS);
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }
}
