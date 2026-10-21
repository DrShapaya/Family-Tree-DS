package ru.drshapaya.androidft2;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Press feedback without consuming touch, click, long-press or accessibility events. */
final class PressFeedback {
    private PressFeedback() { }

    static void install(View view) {
        StateListAnimator states = new StateListAnimator();
        states.addState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled},
            scale(view, .94f, 90L));
        states.addState(new int[]{}, scale(view, 1f, 180L));
        view.setStateListAnimator(states);
    }

    private static AnimatorSet scale(View view, float value, long duration) {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, value),
            ObjectAnimator.ofFloat(view, View.SCALE_Y, value));
        set.setDuration(duration);
        set.setInterpolator(new DecelerateInterpolator(2f));
        return set;
    }
}
