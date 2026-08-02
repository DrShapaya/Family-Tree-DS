package ru.drshapaya.androidft2;

import android.content.Context;
import android.util.AttributeSet;
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

}
