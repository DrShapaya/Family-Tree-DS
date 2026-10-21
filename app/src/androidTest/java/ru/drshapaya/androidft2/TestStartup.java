package ru.drshapaya.androidft2;

import android.app.Instrumentation;
import android.os.SystemClock;

/** Waits for the actual model/UI initialization, not merely an idle loading screen. */
final class TestStartup {
    static void await(Instrumentation instrumentation, MainActivity activity) {
        long deadline=SystemClock.uptimeMillis()+30_000;
        boolean[] ready={false};
        while (SystemClock.uptimeMillis()<deadline) {
            instrumentation.runOnMainSync(() -> ready[0]=activity.state!=null && activity.treeView!=null);
            if (ready[0]) return;
            SystemClock.sleep(25);
        }
        throw new AssertionError("Tree startup did not complete");
    }
}
