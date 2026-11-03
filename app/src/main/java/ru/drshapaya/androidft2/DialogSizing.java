package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Lets a dialog with a scrolling list shrink to its content, up to a maximum height. */
final class DialogSizing {
    private DialogSizing() {}

    /**
     * Call before {@code dialog.show()} with the window width already set. The height is
     * measured here, before the window appears, so the dialog opens at its final size and
     * position; resizing after the first frame made it jump. Content that grows later (an
     * opened accordion) is capped at {@code maxHeight} and scrolls.
     */
    static void wrapUpTo(Dialog dialog, LinearLayout shell, ScrollView scroll, int maxHeight) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams attrs = window.getAttributes();
        int width = attrs.width > 0 ? attrs.width : shell.getResources().getDisplayMetrics().widthPixels;
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        shell.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        if (shell.getMeasuredHeight() > maxHeight) {
            capAt(window, scroll, maxHeight);
            return;
        }
        attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(attrs);
        shell.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (bottom - top <= maxHeight) return;
                shell.removeOnLayoutChangeListener(this);
                capAt(window, scroll, maxHeight);
            }
        });
    }

    private static void capAt(Window window, ScrollView scroll, int maxHeight) {
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        WindowManager.LayoutParams capped = window.getAttributes();
        capped.height = maxHeight;
        window.setAttributes(capped);
    }
}
