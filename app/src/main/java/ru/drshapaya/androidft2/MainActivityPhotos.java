package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** Photo viewing and avatar editing flows kept separate from the main screen. */
final class MainActivityPhotos {
    private final MainActivity activity;

    MainActivityPhotos(MainActivity activity) {
        this.activity = activity;
    }

    void showPhotoPreview(String dataUrl) {
        showPhotoPreviewBitmap(activity.bitmapFromDataUrl(dataUrl), "", dataUrl);
    }

    void showMediaPhotoPreview(String mediaId) {
        showPhotoPreviewBitmap(activity.store.mediaStore().decodeBitmap(mediaId, 1800), mediaId, "");
    }

    void showPersonPhotoEditor(Person person) {
        if (person == null) return;
        Bitmap source = person.photoMediaId == null || person.photoMediaId.isEmpty()
            ? activity.bitmapFromDataUrl(person.photo)
            : activity.store.mediaStore().decodeBitmap(person.photoMediaId, 2200);
        if (source == null) {
            activity.showHelp("Фото", "Фото не удалось открыть.");
            return;
        }
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12));
        shell.setBackground(activity.panelBg(Color.WHITE, activity.dp(20), Color.argb(56, 63, 82, 94)));

        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(activity);
        mark.setImageResource(R.drawable.ic_menu_edit);
        mark.setColorFilter(activity.uiColor(Color.rgb(8, 122, 115)));
        mark.setPadding(activity.dp(9), activity.dp(9), activity.dp(9), activity.dp(9));
        mark.setBackground(activity.panelBg(Color.rgb(232, 248, 246), activity.dp(999), Color.TRANSPARENT));
        top.addView(mark, new LinearLayout.LayoutParams(activity.dp(42), activity.dp(42)));
        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(activity.dp(10), 0, activity.dp(6), 0);
        TextView title = new LocalizedTextView(activity);
        title.setText("Редактор аватара");
        title.setTextSize(17);
        title.setTextColor(activity.uiColor(Color.rgb(28, 34, 38)));
        title.setTypeface(activity.uiBold());
        heading.addView(title, new LinearLayout.LayoutParams(-1, activity.dp(25)));
        TextView subtitle = new LocalizedTextView(activity);
        subtitle.setText(person.name == null || person.name.trim().isEmpty() ? "Без имени" : person.name.trim());
        subtitle.setTextSize(10);
        subtitle.setTextColor(activity.uiColor(Color.rgb(101, 113, 122)));
        subtitle.setTypeface(activity.ui());
        heading.addView(subtitle, new LinearLayout.LayoutParams(-1, activity.dp(19)));
        top.addView(heading, new LinearLayout.LayoutParams(0, activity.dp(44), 1));
        top.addView(activity.closeButton(v -> dialog.dismiss()), new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)));
        shell.addView(top, new LinearLayout.LayoutParams(-1, activity.dp(46)));

        FrameLayout imagePlate = new FrameLayout(activity);
        imagePlate.setPadding(activity.dp(5), activity.dp(5), activity.dp(5), activity.dp(5));
        imagePlate.setBackground(activity.panelBg(Color.rgb(235, 241, 243), activity.dp(16), Color.rgb(207, 219, 225)));
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(source);
        imagePlate.addView(image, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, 0, 1);
        imageParams.setMargins(0, activity.dp(10), 0, activity.dp(10));
        shell.addView(imagePlate, imageParams);

        final Bitmap[] working = {source};
        final boolean[] bitmapChanged = {false};
        final float[] position = {person.avatarScale, person.avatarOffsetX, person.avatarOffsetY};

        LinearLayout rotationTools = new LinearLayout(activity);
        rotationTools.setPadding(activity.dp(5), activity.dp(5), activity.dp(5), activity.dp(5));
        rotationTools.setBackground(activity.panelBg(Color.rgb(247, 250, 251), activity.dp(15), Color.rgb(217, 224, 229)));
        Button rotateLeft = avatarToolButton("↶ 90°", v -> {
            if (!activity.requireEditingEnabled()) return;
            Bitmap transformed = transformAvatar(working[0], -90f, false);
            if (transformed != null) {
                working[0] = transformed;
                image.setImageBitmap(transformed);
                bitmapChanged[0] = true;
                resetAvatarPosition(position);
            }
        });
        Button rotateRight = avatarToolButton("↷ 90°", v -> {
            if (!activity.requireEditingEnabled()) return;
            Bitmap transformed = transformAvatar(working[0], 90f, false);
            if (transformed != null) {
                working[0] = transformed;
                image.setImageBitmap(transformed);
                bitmapChanged[0] = true;
                resetAvatarPosition(position);
            }
        });
        Button mirror = avatarToolButton("↔ Отразить", v -> {
            if (!activity.requireEditingEnabled()) return;
            Bitmap transformed = transformAvatar(working[0], 0f, true);
            if (transformed != null) {
                working[0] = transformed;
                image.setImageBitmap(transformed);
                bitmapChanged[0] = true;
                resetAvatarPosition(position);
            }
        });
        rotationTools.addView(rotateLeft, avatarToolParams(0));
        rotationTools.addView(rotateRight, avatarToolParams(activity.dp(4)));
        rotationTools.addView(mirror, avatarToolParams(activity.dp(4)));
        shell.addView(rotationTools, new LinearLayout.LayoutParams(-1, activity.dp(58)));

        LinearLayout cropTools = new LinearLayout(activity);
        cropTools.setPadding(activity.dp(5), activity.dp(5), activity.dp(5), activity.dp(5));
        cropTools.setBackground(activity.panelBg(Color.rgb(247, 250, 251), activity.dp(15), Color.rgb(217, 224, 229)));
        Button crop = avatarToolButton("✂ Обрезать", v -> {
            if (!activity.requireEditingEnabled()) return;
            showFreeAvatarCropDialog(working[0], transformed -> {
                working[0] = transformed;
                image.setImageBitmap(transformed);
                bitmapChanged[0] = true;
                resetAvatarPosition(position);
            });
        });
        Button positionButton = avatarToolButton("▣ Позиция", v -> {
            if (!activity.requireEditingEnabled()) return;
            showAvatarPositionDialog(working[0], position[0], position[1], position[2], (scale, x, y) -> {
                position[0] = scale;
                position[1] = x;
                position[2] = y;
            });
        });
        cropTools.addView(crop, avatarToolParams(0));
        cropTools.addView(positionButton, avatarToolParams(activity.dp(4)));
        LinearLayout.LayoutParams cropToolsParams = new LinearLayout.LayoutParams(-1, activity.dp(58));
        cropToolsParams.setMargins(0, activity.dp(6), 0, 0);
        shell.addView(cropTools, cropToolsParams);

        TextView hint = new LocalizedTextView(activity);
        hint.setText("«Обрезать» удаляет края. «Позиция» меняет вид на карточке, не обрезая фото.");
        hint.setTextSize(9);
        hint.setTextColor(activity.uiColor(Color.rgb(101, 113, 122)));
        hint.setTypeface(activity.ui());
        hint.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(hint, new LinearLayout.LayoutParams(-1, activity.dp(34)));

        LinearLayout actions = new LinearLayout(activity);
        Button cancel = activity.actionButton("Закрыть", v -> dialog.dismiss());
        cancel.setBackground(activity.panelBg(Color.rgb(247, 250, 251), activity.dp(13), Color.rgb(217, 224, 229)));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, activity.dp(50), 1));
        Button save = activity.actionButton("Сохранить", v -> {
            if (!activity.requireEditingEnabled()) return;
            v.setEnabled(false);
            rotateLeft.setEnabled(false);
            rotateRight.setEnabled(false);
            mirror.setEnabled(false);
            crop.setEnabled(false);
            positionButton.setEnabled(false);
            Bitmap result = working[0];
            activity.toast("Сохранение аватара…");
            if (bitmapChanged[0]) {
                new Thread(() -> saveEditedAvatar(person, result, true, position[0], position[1], position[2], dialog), "avatar-editor-save").start();
            } else {
                saveEditedAvatar(person, result, false, position[0], position[1], position[2], dialog);
            }
        });
        save.setTextColor(Color.WHITE);
        save.setBackground(activity.tealGradientBg(activity.dp(13)));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, activity.dp(50), 1.25f);
        saveParams.setMargins(activity.dp(8), 0, 0, 0);
        actions.addView(save, saveParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, activity.dp(50));
        actionsParams.setMargins(0, activity.dp(6), 0, 0);
        shell.addView(actions, actionsParams);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - activity.dp(20), activity.dp(640));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = Math.min(Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.9f), activity.dp(880));
            attrs.dimAmount = 0.44f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    private Button avatarToolButton(String label, View.OnClickListener action) {
        Button button = activity.actionButton(label, action);
        button.setTextSize(10);
        button.setSingleLine(true);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(activity.dp(4), 0, activity.dp(4), activity.dp(2));
        button.setTextColor(activity.uiColor(Color.rgb(8, 122, 115)));
        button.setBackground(activity.panelBg(Color.rgb(235, 248, 246), activity.dp(11), Color.argb(64, 24, 169, 153)));
        return button;
    }

    private LinearLayout.LayoutParams avatarToolParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, activity.dp(48), 1);
        params.setMargins(leftMargin, 0, 0, 0);
        return params;
    }

    private static Bitmap transformAvatar(Bitmap source, float degrees, boolean mirror) {
        if (source == null) return null;
        try {
            Matrix matrix = new Matrix();
            if (mirror) matrix.postScale(-1f, 1f);
            if (degrees != 0f) matrix.postRotate(degrees);
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void resetAvatarPosition(float[] position) {
        position[0] = 1f;
        position[1] = 0f;
        position[2] = 0f;
    }

    private void saveEditedAvatar(Person person, Bitmap bitmap, boolean bitmapChanged,
                                  float scale, float offsetX, float offsetY, Dialog dialog) {
        try {
            TreeMediaStore.StoredMedia stored = null;
            if (bitmapChanged) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 96, bytes)) {
                    throw new IllegalStateException("Фото не удалось подготовить");
                }
                try (ByteArrayInputStream input = new ByteArrayInputStream(bytes.toByteArray())) {
                    stored = activity.store.mediaStore().importPhoto(input, "avatar_edited.jpg", "image/jpeg");
                }
            }
            TreeMediaStore.StoredMedia finalStored = stored;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed() || activity.state.people.get(person.id) != person) return;
                activity.recordUndo("Изменён аватар", person.name == null || person.name.isEmpty() ? "Без имени" : person.name);
                if (finalStored != null) {
                    person.photoMediaId = finalStored.id;
                    person.photo = "";
                }
                person.avatarScale = AvatarTransform.clamp(scale, 1f, 8f);
                person.avatarOffsetX = offsetX;
                person.avatarOffsetY = offsetY;
                activity.saveOnly();
                activity.bindState();
                activity.treeView.invalidate();
                activity.refreshOpenPersonEditor();
                dialog.dismiss();
                activity.toast("Аватар сохранён");
            });
        } catch (Exception error) {
            activity.runOnUiThread(() -> activity.toast("Аватар не сохранён: " + error.getMessage()));
        }
    }

    private interface BitmapCropResult {
        void accept(Bitmap bitmap);
    }

    private interface AvatarPositionResult {
        void accept(float scale, float offsetX, float offsetY);
    }

    private void showFreeAvatarCropDialog(Bitmap bitmap, BitmapCropResult result) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = activity.editorDialogShell();
        shell.addView(activity.editorDialogHeader(dialog, R.drawable.ic_menu_edit, "Свободная обрезка", "Перемещайте и тяните углы"));
        FreeAvatarCropView cropView = new FreeAvatarCropView(activity);
        cropView.setBitmap(bitmap);
        LinearLayout.LayoutParams cropParams = new LinearLayout.LayoutParams(-1, 0, 1);
        cropParams.setMargins(0, activity.dp(10), 0, activity.dp(10));
        shell.addView(cropView, cropParams);
        LinearLayout actions = new LinearLayout(activity);
        actions.addView(activity.actionButton("Отмена", v -> dialog.dismiss()), new LinearLayout.LayoutParams(0, activity.dp(50), 1));
        Button apply = activity.actionButton("Применить обрезку", v -> {
            Bitmap cropped = cropView.croppedBitmap();
            if (cropped == null) {
                activity.toast("Не удалось обрезать фото");
                return;
            }
            result.accept(cropped);
            dialog.dismiss();
        });
        apply.setTextColor(Color.WHITE);
        apply.setBackground(activity.tealGradientBg(activity.dp(13)));
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(0, activity.dp(50), 1.45f);
        applyParams.setMargins(activity.dp(8), 0, 0, 0);
        actions.addView(apply, applyParams);
        shell.addView(actions, new LinearLayout.LayoutParams(-1, activity.dp(50)));
        activity.showEditorDialog(dialog, shell);
    }

    private void showAvatarPositionDialog(Bitmap bitmap, float scale, float x, float y, AvatarPositionResult result) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = activity.editorDialogShell();
        shell.addView(activity.editorDialogHeader(dialog, R.drawable.ic_menu_image, "Вид на карточке", "Фото останется целым"));
        AvatarPositionView positionView = new AvatarPositionView(activity);
        positionView.setBitmap(bitmap);
        positionView.setPosition(scale, x, y);
        LinearLayout.LayoutParams positionParams = new LinearLayout.LayoutParams(-1, 0, 1);
        positionParams.setMargins(0, activity.dp(10), 0, activity.dp(6));
        shell.addView(positionView, positionParams);
        TextView hint = new LocalizedTextView(activity);
        hint.setText("Перемещайте фото одним пальцем, масштабируйте щипком. Затемнённая область не удаляется.");
        hint.setTextSize(9);
        hint.setTextColor(activity.uiColor(Color.rgb(101, 113, 122)));
        hint.setTypeface(activity.ui());
        hint.setPadding(activity.dp(4), 0, activity.dp(4), activity.dp(6));
        shell.addView(hint, new LinearLayout.LayoutParams(-1, activity.dp(50)));
        LinearLayout actions = new LinearLayout(activity);
        Button reset = activity.actionButton("Сбросить", v -> positionView.setPosition(1f, 0f, 0f));
        actions.addView(reset, new LinearLayout.LayoutParams(0, activity.dp(50), 1));
        Button apply = activity.actionButton("Готово", v -> {
            result.accept(positionView.positionScale(), positionView.positionX(), positionView.positionY());
            dialog.dismiss();
        });
        apply.setTextColor(Color.WHITE);
        apply.setBackground(activity.tealGradientBg(activity.dp(13)));
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(0, activity.dp(50), 1.2f);
        applyParams.setMargins(activity.dp(8), 0, 0, 0);
        actions.addView(apply, applyParams);
        shell.addView(actions, new LinearLayout.LayoutParams(-1, activity.dp(50)));
        activity.showEditorDialog(dialog, shell);
    }

    private void showPhotoPreviewBitmap(Bitmap bitmap, String mediaId, String dataUrl) {
        if (bitmap == null) {
            activity.showHelp("Фото", "Фото не удалось открыть.");
            return;
        }
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12));
        shell.setBackground(activity.panelBg(Color.WHITE, activity.dp(18), Color.argb(56, 63, 82, 94)));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(activity);
        mark.setImageResource(R.drawable.ic_menu_image);
        mark.setColorFilter(activity.uiColor(Color.rgb(8, 122, 115)));
        mark.setPadding(activity.dp(9), activity.dp(9), activity.dp(9), activity.dp(9));
        mark.setBackground(activity.panelBg(Color.rgb(232, 248, 246), activity.dp(999), Color.TRANSPARENT));
        top.addView(mark, new LinearLayout.LayoutParams(activity.dp(42), activity.dp(42)));
        TextView title = new LocalizedTextView(activity);
        title.setText("Фотография");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(18);
        title.setTypeface(activity.uiBold());
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, activity.dp(44), 1);
        titleParams.setMargins(activity.dp(10), 0, activity.dp(8), 0);
        top.addView(title, titleParams);
        top.addView(activity.closeButton(v -> dialog.dismiss()), new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)));
        shell.addView(top);

        FrameLayout imagePlate = new FrameLayout(activity);
        imagePlate.setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4));
        imagePlate.setBackground(activity.panelBg(Color.rgb(239, 244, 246), activity.dp(14), Color.rgb(213, 223, 228)));
        ImageView image = new ImageView(activity);
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imagePlate.addView(image, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, 0, 1);
        imageParams.setMargins(0, activity.dp(12), 0, 0);
        shell.addView(imagePlate, imageParams);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button download = activity.actionButton("Скачать фото", v -> activity.requestPhotoDownload(mediaId, dataUrl));
        download.setTextColor(Color.WHITE);
        download.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_download, 0, 0, 0);
        download.setCompoundDrawablePadding(activity.dp(8));
        activity.tintDrawables(download, Color.WHITE);
        download.setBackground(activity.tealGradientBg(activity.dp(10)));
        actions.addView(download, new LinearLayout.LayoutParams(0, activity.dp(48), 1.35f));

        Button close = activity.actionButton("Закрыть", v -> dialog.dismiss());
        LinearLayout.LayoutParams closeButtonParams = new LinearLayout.LayoutParams(0, activity.dp(48), 1f);
        closeButtonParams.setMargins(activity.dp(8), 0, 0, 0);
        actions.addView(close, closeButtonParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, activity.dp(48));
        actionsParams.setMargins(0, activity.dp(12), 0, 0);
        shell.addView(actions, actionsParams);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - activity.dp(24), activity.dp(620));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = Math.min(
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.84f),
                activity.dp(820));
            attrs.dimAmount = 0.42f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

}
