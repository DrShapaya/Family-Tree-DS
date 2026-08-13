package ru.drshapaya.androidft2;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Receives tree files from other apps without attaching AndroidFT's main UI to
 * the caller's task.
 */
public final class TreeFileOpenActivity extends Activity {
    private static final int URI_PERMISSION_FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openInMainTask(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openInMainTask(intent);
    }

    private void openInMainTask(Intent source) {
        Intent target = new Intent(this, MainActivity.class);
        target.setAction(Intent.ACTION_VIEW);

        if (source != null) {
            Uri uri = source.getData();
            String type = source.getType();
            if (type == null) target.setData(uri);
            else target.setDataAndType(uri, type);
            target.setClipData(source.getClipData());
            target.addFlags(source.getFlags() & URI_PERMISSION_FLAGS);
        }

        target.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(target);
        finish();
    }
}
