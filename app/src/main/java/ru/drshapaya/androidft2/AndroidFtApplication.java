package ru.drshapaya.androidft2;

import android.app.Application;

public final class AndroidFtApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            DiagnosticsLogger.crash(this, error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
        DiagnosticsLogger.breadcrumb(this, "app.start");
    }
}
