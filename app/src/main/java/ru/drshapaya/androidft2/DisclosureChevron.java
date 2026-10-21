package ru.drshapaya.androidft2;

import android.content.Context;
import android.widget.ImageView;
import android.view.animation.DecelerateInterpolator;

/** Rounded vector chevron that keeps its shape at every font scale. */
final class DisclosureChevron extends ImageView {
    DisclosureChevron(Context context, int color) {
        super(context);
        setImageResource(R.drawable.ic_disclosure_chevron);
        setColorFilter(color);
        setScaleType(ScaleType.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void expanded(boolean expanded) {
        animate().cancel();
        if (!PanelTransitions.enabled(this)) setRotation(expanded ? 180f : 0f);
        else animate().rotation(expanded ? 180f : 0f).setDuration(220L)
            .setInterpolator(new DecelerateInterpolator(2f)).start();
    }
}
