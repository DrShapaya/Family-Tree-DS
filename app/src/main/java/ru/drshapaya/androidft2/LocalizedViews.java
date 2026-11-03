package ru.drshapaya.androidft2;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

final class LocalizedViews {
    private LocalizedViews() {}

    static void setRaw(TextView view, CharSequence text) {
        if (view instanceof LocalizedTextView) {
            ((LocalizedTextView) view).setRawText(text);
        } else {
            view.setText(text);
        }
    }

    static float scaledTextSize(Context context, float size) {
        float scale = context == null
            ? 1f
            : MainActivity.normalizeFontScale(context
                .getSharedPreferences("androidft-ui", Context.MODE_PRIVATE)
                .getFloat("font_scale", 1f));
        return readableSize(size) * scale;
    }

    /**
     * Many captions were set at 8-10sp and were hard to read on a phone. Small sizes are
     * lifted onto a readable floor (8 to 10, 9 to 10.5, 10 to 11) while keeping their order;
     * 11sp and larger stay as written.
     */
    static float readableSize(float size) {
        if (size <= 0f || size >= 11f) return size;
        if (size <= 8f) return 10f;
        return 10f + (size - 8f) * 0.5f;
    }
}

final class LocalizedTextView extends TextView {
    LocalizedTextView(Context context) {
        super(context);
    }

    LocalizedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public void setText(CharSequence text, BufferType type) {
        super.setText(AppLanguage.translate(getContext(), text), type);
    }

    @Override public void setTextColor(int color) {
        super.setTextColor(AppThemePalette.text(color));
    }

    @Override public void setTextSize(float size) {
        super.setTextSize(TypedValue.COMPLEX_UNIT_SP, LocalizedViews.scaledTextSize(getContext(), size));
    }

    @Override public void setTextSize(int unit, float size) {
        super.setTextSize(unit, LocalizedViews.scaledTextSize(getContext(), size));
    }

    void setRawText(CharSequence text) {
        super.setText(text, BufferType.NORMAL);
    }
}

final class LocalizedButton extends Button {
    LocalizedButton(Context context) {
        super(context);
    }

    LocalizedButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public void setText(CharSequence text, BufferType type) {
        super.setText(AppLanguage.translate(getContext(), text), type);
    }

    @Override public void setTextColor(int color) {
        super.setTextColor(AppThemePalette.text(color));
    }

    @Override public void setTextSize(float size) {
        super.setTextSize(TypedValue.COMPLEX_UNIT_SP, LocalizedViews.scaledTextSize(getContext(), size));
    }

    @Override public void setTextSize(int unit, float size) {
        super.setTextSize(unit, LocalizedViews.scaledTextSize(getContext(), size));
    }
}

final class LocalizedCheckBox extends CheckBox {
    LocalizedCheckBox(Context context) {
        super(context);
    }

    LocalizedCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public void setText(CharSequence text, BufferType type) {
        super.setText(AppLanguage.translate(getContext(), text), type);
    }

    @Override public void setTextColor(int color) {
        super.setTextColor(AppThemePalette.text(color));
    }

    @Override public void setTextSize(float size) {
        super.setTextSize(TypedValue.COMPLEX_UNIT_SP, LocalizedViews.scaledTextSize(getContext(), size));
    }

    @Override public void setTextSize(int unit, float size) {
        super.setTextSize(unit, LocalizedViews.scaledTextSize(getContext(), size));
    }
}

final class LocalizedEditText extends EditText {
    LocalizedEditText(Context context) {
        super(context);
    }

    LocalizedEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public void setTextColor(int color) {
        super.setTextColor(AppThemePalette.text(color));
    }

    @Override public void setTextSize(float size) {
        super.setTextSize(TypedValue.COMPLEX_UNIT_SP, LocalizedViews.scaledTextSize(getContext(), size));
    }

    @Override public void setTextSize(int unit, float size) {
        super.setTextSize(unit, LocalizedViews.scaledTextSize(getContext(), size));
    }
}
