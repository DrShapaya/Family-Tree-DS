package ru.drshapaya.androidft2;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

final class MainActivityEditor {
    private final MainActivity activity;
    private Dialog currentPersonDialog;
    private int currentEditorPage;
    private String currentMemoryPersonId = "";
    private LinearLayout currentMemoryListHost;
    private TextView currentMemoryTab;
    private TextView currentMemoryPanelTitle;
    private EditText currentMemoryTitleInput;
    private EditText currentMemoryTextInput;
    private LinearLayout currentMemoryDraftHost;
    private final List<MemoryAttachment> currentMemoryDraftAttachments = new java.util.ArrayList<>();

    MainActivityEditor(MainActivity activity) {
        this.activity = activity;
    }

    void bindEditor(Person person) {
        if (person == null || activity.nameInput == null) return;
        activity.bindingEditor = true;
        activity.nameInput.setText(person.name);
        activity.bornInput.setText(person.bornYear);
        activity.diedInput.setText(person.diedYear);
        activity.placeInput.setText(person.place);
        activity.notesInput.setText(person.notes);
        activity.bindingEditor = false;
        activity.updateStats();
    }
    void openPersonEditor() {
        Person person = activity.state.selectedPerson();
        if (person == null) {
            toast("Выберите карточку");
            return;
        }
        if (currentPersonDialog != null && currentPersonDialog.isShowing()) currentPersonDialog.dismiss();
        currentMemoryDraftAttachments.clear();
        Dialog dialog = new Dialog(activity);
        currentPersonDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(10), dp(18), dp(14));
        shell.setBackground(panelBg(Color.rgb(250, 252, 253), dp(18), Color.argb(42, 63, 82, 94)));

        View handle = new View(activity);
        handle.setBackground(panelBg(Color.rgb(194, 207, 214), dp(999), Color.TRANSPARENT));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(46), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(12));
        shell.addView(handle, handleParams);

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(2), 0, 0, dp(4));
        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = editorText("КАРТОЧКА ЧЕЛОВЕКА", 10, Color.rgb(76, 83, 88), true);
        TextView title = editorText(person.name.isEmpty() ? "Без имени" : person.name, 20, Color.rgb(28, 34, 38), true);
        title.setSingleLine(false);
        title.setMaxLines(3);
        title.setLineSpacing(dp(2), 1f);
        heading.addView(eyebrow, new LinearLayout.LayoutParams(-1, dp(18)));
        heading.addView(title, new LinearLayout.LayoutParams(-1, -2));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button root = iconButton(R.drawable.ic_menu_target, v -> {
            setRootPerson(person.id);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        rootParams.setMargins(dp(8), 0, 0, 0);
        header.addView(root, rootParams);
        Button close = iconButton(R.drawable.ic_menu_close, v -> dialog.dismiss(), Color.rgb(28, 34, 38));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        closeParams.setMargins(dp(6), 0, 0, 0);
        header.addView(close, closeParams);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
        headerParams.setMargins(0, 0, 0, dp(6));
        shell.addView(header, headerParams);

        LinearLayout tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(panelBg(Color.rgb(235, 241, 244), dp(10), Color.TRANSPARENT));
        TextView profileTab = editorTab(R.drawable.ic_field_person, "Профиль");
        TextView memoryTab = editorTab(R.drawable.ic_editor_archive, "Память · " + person.memories.size());
        currentMemoryTab = memoryTab;
        TextView relationsTab = editorTab(R.drawable.ic_nav_links, "Связи");
        tabs.addView(profileTab, new LinearLayout.LayoutParams(0, dp(46), 1));
        tabs.addView(memoryTab, new LinearLayout.LayoutParams(0, dp(46), 1));
        tabs.addView(relationsTab, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(-1, dp(54));
        tabsParams.setMargins(0, dp(4), 0, dp(12));
        shell.addView(tabs, tabsParams);

        FrameLayout pageHost = new FrameLayout(activity);
        LinearLayout profileForm = editorPage();
        LinearLayout memoryForm = editorPage();
        LinearLayout relationsForm = editorPage();
        ScrollView profileScroll = editorScroll(profileForm);
        ScrollView memoryScroll = editorScroll(memoryForm);
        ScrollView relationsScroll = editorScroll(relationsForm);
        pageHost.addView(profileScroll, new FrameLayout.LayoutParams(-1, -1));
        pageHost.addView(memoryScroll, new FrameLayout.LayoutParams(-1, -1));
        pageHost.addView(relationsScroll, new FrameLayout.LayoutParams(-1, -1));
        shell.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1));

        View[] pages = {profileScroll, memoryScroll, relationsScroll};
        TextView[] tabViews = {profileTab, memoryTab, relationsTab};
        profileTab.setOnClickListener(v -> selectEditorPage(0, pages, tabViews));
        memoryTab.setOnClickListener(v -> selectEditorPage(1, pages, tabViews));
        relationsTab.setOnClickListener(v -> selectEditorPage(2, pages, tabViews));

        LinearLayout footer = new LinearLayout(activity);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setPadding(dp(4), dp(10), 0, 0);
        TextView saveState = editorText("✓  Изменения сохраняются автоматически", 11, Color.rgb(44, 118, 109), true);
        footer.addView(saveState, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button done = actionButton("Готово", v -> dialog.dismiss());
        done.setTextColor(Color.WHITE);
        done.setTextSize(14);
        done.setBackground(tealGradientBg(dp(10)));
        footer.addView(done, new LinearLayout.LayoutParams(dp(122), dp(48)));
        shell.addView(footer, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout photoEditor = new LinearLayout(activity);
        photoEditor.setOrientation(LinearLayout.VERTICAL);
        photoEditor.setPadding(dp(14), dp(14), dp(14), dp(14));
        photoEditor.setBackground(panelBg(Color.rgb(235, 249, 247), dp(14), Color.argb(82, 24, 169, 153)));
        LinearLayout photoTop = new LinearLayout(activity);
        photoTop.setOrientation(LinearLayout.HORIZONTAL);
        photoTop.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout avatar = new FrameLayout(activity);
        avatar.setBackground(ovalBg(person.color, Color.WHITE, 3));
        avatar.setClipToOutline(true);
        TextView initials = editorText(initials(person.name), 24, Color.WHITE, true);
        initials.setGravity(Gravity.CENTER);
        avatar.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        ImageView photo = new ImageView(activity);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        boolean hasPhoto = person.photoMediaId != null && !person.photoMediaId.isEmpty()
            && activity.store.mediaStore().exists(person.photoMediaId)
            || person.photo != null && !person.photo.isEmpty();
        Bitmap personPhoto = person.photoMediaId != null && !person.photoMediaId.isEmpty()
            ? activity.store.mediaStore().decodeBitmap(person.photoMediaId, 512)
            : bitmapFromDataUrl(person.photo);
        if (personPhoto != null) {
            photo.setImageBitmap(personPhoto);
            initials.setVisibility(View.GONE);
        }
        avatar.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        avatar.setOnClickListener(v -> {
            if (person.photoMediaId != null && !person.photoMediaId.isEmpty()) {
                activity.showMediaPhotoPreview(person.photoMediaId);
            } else if (person.photo != null && !person.photo.isEmpty()) {
                showPhotoPreview(person.photo);
            }
            else {
                activity.pendingPhotoPersonId = person.id;
                openPhotoPicker();
            }
        });
        photoTop.addView(avatar, new LinearLayout.LayoutParams(dp(92), dp(92)));

        LinearLayout photoActions = new LinearLayout(activity);
        photoActions.setOrientation(LinearLayout.VERTICAL);
        photoActions.addView(editorText("Фото профиля", 14, Color.rgb(28, 34, 38), true), new LinearLayout.LayoutParams(-1, dp(24)));
        TextView photoHint = editorText(hasPhoto ? "Снимок виден на дереве и в карточке" : "Добавьте снимок для дерева и карточки", 11, Color.rgb(101, 113, 122), false);
        photoActions.addView(photoHint, new LinearLayout.LayoutParams(-1, dp(22)));
        LinearLayout photoButtons = new LinearLayout(activity);
        photoButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button loadPhoto = actionButton(hasPhoto ? "Заменить фото" : "Загрузить фото", v -> {
            activity.pendingPhotoPersonId = person.id;
            openPhotoPicker();
        });
        loadPhoto.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_image, 0, 0, 0);
        loadPhoto.setCompoundDrawablePadding(dp(7));
        loadPhoto.setSingleLine(true);
        loadPhoto.setTextSize(12);
        tintDrawables(loadPhoto, Color.rgb(8, 122, 115));
        photoButtons.addView(loadPhoto, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button removePhoto = actionButton("Убрать", v -> {
            if (activity.editLocked
                || (person.photoMediaId == null || person.photoMediaId.isEmpty())
                && (person.photo == null || person.photo.isEmpty())) return;
            recordUndo("Удалено фото", person.name);
            person.photoMediaId = "";
            person.photo = "";
            photo.setImageDrawable(null);
            initials.setVisibility(View.VISIBLE);
            saveOnly();
            activity.treeView.invalidate();
        });
        removePhoto.setTextColor(Color.rgb(197, 83, 75));
        removePhoto.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_trash, 0, 0, 0);
        removePhoto.setCompoundDrawablePadding(dp(6));
        removePhoto.setSingleLine(true);
        removePhoto.setTextSize(12);
        tintDrawables(removePhoto, Color.rgb(197, 83, 75));
        removePhoto.setBackground(panelBg(Color.WHITE, dp(10), Color.argb(72, 197, 83, 75)));
        LinearLayout.LayoutParams removePhotoParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        removePhotoParams.setMargins(dp(8), 0, 0, 0);
        photoButtons.addView(removePhoto, removePhotoParams);
        LinearLayout.LayoutParams photoActionsParams = new LinearLayout.LayoutParams(0, -2, 1);
        photoActionsParams.setMargins(dp(14), 0, 0, 0);
        photoTop.addView(photoActions, photoActionsParams);
        photoEditor.addView(photoTop, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams photoButtonsParams = new LinearLayout.LayoutParams(-1, dp(46));
        photoButtonsParams.setMargins(0, dp(10), 0, 0);
        photoEditor.addView(photoButtons, photoButtonsParams);
        profileForm.addView(photoEditor, editorBlockParams());

        LinearLayout basicHeading = new LinearLayout(activity);
        basicHeading.setOrientation(LinearLayout.HORIZONTAL);
        basicHeading.setGravity(Gravity.CENTER_VERTICAL);
        basicHeading.addView(editorSectionHeading(
            R.drawable.ic_field_note,
            "ОСНОВНОЕ",
            "Имя, даты и места"), new LinearLayout.LayoutParams(0, -2, 1));
        TextView genderChip = genderChip(person);
        genderChip.setOnClickListener(v -> chooseGender(person, genderChip));
        LinearLayout.LayoutParams genderChipParams = new LinearLayout.LayoutParams(dp(88), dp(42));
        genderChipParams.setMargins(dp(10), 0, 0, 0);
        basicHeading.addView(genderChip, genderChipParams);
        profileForm.addView(basicHeading, editorBlockParams());

        EditText name = field("");
        name.setSingleLine(true);
        name.setText(person.name);
        styleEditorField(name, R.drawable.ic_field_person);
        profileForm.addView(labeledEditor("Имя", name), editorBlockParams());

        TextView bornSection = editorText("Родился/родилась", 13, Color.rgb(76, 83, 88), true);
        profileForm.addView(bornSection, editorCaptionParams());
        EditText bornDay = numericField("ДД", person.bornDay, 2);
        EditText bornMonth = numericField("ММ", person.bornMonth, 2);
        EditText bornYear = numericField("ГГГГ", person.bornYear, 4);
        profileForm.addView(dateEditorRow(bornDay, bornMonth, bornYear), editorBlockParams());

        TextView ageValue = editorText(editorAgeLabel(person), 13, Color.rgb(28, 34, 38), true);
        TextView birthdayValue = editorText(editorBirthdayLabel(person), 13, Color.rgb(28, 34, 38), true);
        LinearLayout agePanel = new LinearLayout(activity);
        agePanel.setOrientation(LinearLayout.HORIZONTAL);
        agePanel.addView(editorMetric("ВОЗРАСТ", ageValue), new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams birthdayParams = new LinearLayout.LayoutParams(0, -1, 1);
        birthdayParams.setMargins(dp(8), 0, 0, 0);
        agePanel.addView(editorMetric("ДО ДНЯ РОЖДЕНИЯ", birthdayValue), birthdayParams);
        profileForm.addView(agePanel, editorMetricParams());

        TextView diedSection = editorText("Ушёл/ушла", 13, Color.rgb(76, 83, 88), true);
        profileForm.addView(diedSection, editorCaptionParams());
        EditText diedDay = numericField("ДД", person.diedDay, 2);
        EditText diedMonth = numericField("ММ", person.diedMonth, 2);
        EditText diedYear = numericField("ГГГГ", person.diedYear, 4);
        profileForm.addView(dateEditorRow(diedDay, diedMonth, diedYear), editorBlockParams());

        EditText place = field("город, страна");
        place.setSingleLine(true);
        place.setText(person.place);
        styleEditorField(place, R.drawable.ic_field_location);
        profileForm.addView(labeledEditor("Место", place), editorBlockParams());

        EditText notes = field("Добавить заметку…");
        notes.setMinLines(2);
        notes.setGravity(Gravity.TOP);
        styleEditorField(notes, R.drawable.ic_field_note);
        notes.setPadding(dp(14), dp(12), dp(14), dp(12));
        notes.setText(person.notes);
        profileForm.addView(labeledEditor("Заметки", notes), editorBlockParams());

        memoryForm.addView(editorSectionHeading(
            "АРХИВ ПАМЯТИ",
            person.memories.isEmpty()
                ? "Сохраните первую семейную историю"
                : memoryCountLabel(person.memories.size()) + " в карточке"), editorBlockParams());
        LinearLayout memoryPanel = editorSectionPanel("ЗАПИСИ И ФАЙЛЫ · " + person.memories.size());
        currentMemoryPanelTitle = (TextView) memoryPanel.getChildAt(0);
        LinearLayout memoryListHost = new LinearLayout(activity);
        memoryListHost.setOrientation(LinearLayout.VERTICAL);
        renderMemoryList(memoryListHost, person);
        memoryPanel.addView(memoryListHost, new LinearLayout.LayoutParams(-1, -2));
        currentMemoryPersonId = person.id;
        currentMemoryListHost = memoryListHost;
        EditText memoryTitle = field("Название: фото, история, документ");
        currentMemoryTitleInput = memoryTitle;
        memoryTitle.setSingleLine(true);
        styleEditorField(memoryTitle, 0);
        EditText memoryText = field("Текст воспоминания, подпись к фото или источник");
        currentMemoryTextInput = memoryText;
        memoryText.setMinLines(3);
        memoryText.setGravity(Gravity.TOP);
        styleEditorField(memoryText, 0);
        memoryText.setPadding(dp(14), dp(12), dp(14), dp(12));
        memoryPanel.addView(memoryTitle, formFieldParams());
        memoryPanel.addView(memoryText, formFieldParams());
        LinearLayout memoryDraftHost = new LinearLayout(activity);
        memoryDraftHost.setOrientation(LinearLayout.VERTICAL);
        memoryDraftHost.setVisibility(View.GONE);
        currentMemoryDraftHost = memoryDraftHost;
        memoryPanel.addView(memoryDraftHost, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout memoryActions = new LinearLayout(activity);
        memoryActions.setOrientation(LinearLayout.HORIZONTAL);
        Button memoryFile = actionButton("Файл", v -> {
            if (activity.editLocked) return;
            activity.pendingMemoryPersonId = person.id;
            openMemoryFilePicker();
        });
        memoryFile.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_upload, 0, 0, 0);
        memoryFile.setCompoundDrawablePadding(dp(7));
        tintDrawables(memoryFile, Color.rgb(8, 122, 115));
        memoryActions.addView(memoryFile, new LinearLayout.LayoutParams(0, dp(48), 0.72f));
        Button addMemory = actionButton("Сохранить запись", v -> {
            if (activity.editLocked
                || (text(memoryTitle).isEmpty()
                    && text(memoryText).isEmpty()
                    && currentMemoryDraftAttachments.isEmpty())) return;
            recordUndo("Сохранена запись памяти", person.name);
            Memory memory = new Memory();
            memory.id = "m_" + java.util.UUID.randomUUID().toString().replace("-", "");
            memory.title = text(memoryTitle).isEmpty() ? "Воспоминание" : text(memoryTitle);
            memory.text = text(memoryText);
            memory.at = String.valueOf(System.currentTimeMillis());
            memory.attachments.addAll(currentMemoryDraftAttachments);
            syncLegacyAttachment(memory);
            person.memories.add(0, memory);
            currentMemoryDraftAttachments.clear();
            renderMemoryDraft();
            saveToast("Запись сохранена");
            refreshMemorySection(person.id);
        });
        addMemory.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_note_add, 0, 0, 0);
        addMemory.setCompoundDrawablePadding(dp(7));
        addMemory.setTextSize(11);
        tintDrawables(addMemory, Color.rgb(8, 122, 115));
        LinearLayout.LayoutParams addMemoryParams = new LinearLayout.LayoutParams(0, dp(48), 1.28f);
        addMemoryParams.setMargins(dp(8), 0, 0, 0);
        memoryActions.addView(addMemory, addMemoryParams);
        memoryPanel.addView(memoryActions, formFieldParams());
        memoryForm.addView(memoryPanel, editorBlockParams());

        profileForm.addView(editorSectionHeading("ОФОРМЛЕНИЕ", "Цвет и положение на дереве"), editorBlockParams());
        LinearLayout colorPanel = editorSectionPanel("ЦВЕТ КАРТОЧКИ");
        TextView colorHint = editorText(
            "Передвигайте ползунок — выбранный цвет сразу появится на карточке.",
            11,
            Color.rgb(101, 113, 122),
            false);
        colorPanel.addView(colorHint, new LinearLayout.LayoutParams(-1, dp(34)));

        LinearLayout colorControl = new LinearLayout(activity);
        colorControl.setOrientation(LinearLayout.HORIZONTAL);
        colorControl.setGravity(Gravity.CENTER_VERTICAL);
        HueSliderView cardColorSlider = new HueSliderView(activity);
        cardColorSlider.setColor(TreeState.displayColor(person, activity.state.people.size()));
        TextView cardColorPreview = new TextView(activity);
        cardColorPreview.setContentDescription("Выбранный цвет карточки");
        cardColorPreview.setBackground(panelBg(cardColorSlider.color(), dp(999), Color.argb(72, 28, 34, 38)));
        colorControl.addView(cardColorSlider, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams colorPreviewParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        colorPreviewParams.setMargins(dp(8), 0, 0, 0);
        colorControl.addView(cardColorPreview, colorPreviewParams);
        colorPanel.addView(colorControl, formFieldParams());

        final boolean[] colorUndoRecorded = {false};
        cardColorSlider.setListener((color, fromUser) -> {
            if (activity.editLocked || !fromUser) return;
            if (!colorUndoRecorded[0]) {
                recordUndo("Изменён цвет карточки", person.name);
                colorUndoRecorded[0] = true;
            }
            person.colorMode = "manual";
            person.manualColor = TreeState.colorString(color);
            person.color = color;
            cardColorPreview.setBackground(panelBg(color, dp(999), Color.argb(72, 28, 34, 38)));
            avatar.setBackground(ovalBg(color, Color.WHITE, 3));
            saveOnly();
            activity.treeView.invalidate();
        });

        Button automaticColor = actionButton("Вернуть цвет по ФИО", v -> {
            if (activity.editLocked) return;
            recordUndo("Восстановлен цвет по ФИО", person.name);
            person.colorMode = "auto-name";
            person.color = TreeState.displayColor(person, activity.state.people.size());
            cardColorSlider.setColor(person.color);
            cardColorPreview.setBackground(panelBg(person.color, dp(999), Color.argb(72, 28, 34, 38)));
            avatar.setBackground(ovalBg(person.color, Color.WHITE, 3));
            saveOnly();
            activity.treeView.invalidate();
        });
        colorPanel.addView(automaticColor, formFieldParams());
        profileForm.addView(colorPanel, editorBlockParams());

        CheckBox pinned = new CheckBox(activity);
        pinned.setText("Закрепить карточку");
        pinned.setTextSize(13);
        pinned.setTypeface(uiBold());
        pinned.setTextColor(Color.rgb(28, 34, 38));
        pinned.setChecked(person.pinned);
        pinned.setPadding(dp(10), 0, dp(10), 0);
        pinned.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        profileForm.addView(pinned, editorSwitchParams());

        relationsForm.addView(editorSectionHeading(
            "СЕМЬЯ И СВЯЗИ",
            "Переходите к родственникам прямо из карточки"), editorBlockParams());
        LinearLayout relationActions = editorSectionPanel("ДОБАВИТЬ В СЕМЬЮ");
        LinearLayout relationActionsTop = new LinearLayout(activity);
        relationActionsTop.setOrientation(LinearLayout.HORIZONTAL);
        relationActionsTop.addView(editorQuickAction(
            R.drawable.ic_menu_ancestors,
            "Родителей",
            dialog,
            "add-parents-2"), new LinearLayout.LayoutParams(0, dp(64), 1));
        LinearLayout.LayoutParams relationActionEnd = new LinearLayout.LayoutParams(0, dp(64), 1);
        relationActionEnd.setMargins(dp(8), 0, 0, 0);
        relationActionsTop.addView(editorQuickAction(
            R.drawable.ic_menu_heart,
            "Партнёра",
            dialog,
            "add-partner"), relationActionEnd);
        relationActions.addView(relationActionsTop, formFieldParams());
        LinearLayout relationActionsBottom = new LinearLayout(activity);
        relationActionsBottom.setOrientation(LinearLayout.HORIZONTAL);
        relationActionsBottom.addView(editorQuickAction(
            R.drawable.ic_menu_child,
            "Ребёнка",
            dialog,
            "add-children-1"), new LinearLayout.LayoutParams(0, dp(64), 1));
        LinearLayout.LayoutParams relationActionBottomEnd = new LinearLayout.LayoutParams(0, dp(64), 1);
        relationActionBottomEnd.setMargins(dp(8), 0, 0, 0);
        relationActionsBottom.addView(editorQuickAction(
            R.drawable.ic_menu_people,
            "Брата / сестру",
            dialog,
            "add-sibling"), relationActionBottomEnd);
        relationActions.addView(relationActionsBottom, formFieldParams());
        relationsForm.addView(relationActions, editorBlockParams());

        LinearLayout kinshipPanel = editorSectionPanel("СВЯЗИ");
        addKinshipRows(kinshipPanel, person, dialog);
        relationsForm.addView(kinshipPanel, editorBlockParams());

        LinearLayout validationPanel = editorSectionPanel("ЧТО МОЖНО ДОПОЛНИТЬ");
        addPersonValidation(validationPanel, person);
        relationsForm.addView(validationPanel, editorBlockParams());

        Button delete = actionButton("Удалить человека", v -> {
            dialog.dismiss();
            confirmDelete();
        });
        delete.setTextColor(Color.rgb(197, 83, 75));
        delete.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_trash, 0, 0, 0);
        delete.setCompoundDrawablePadding(dp(8));
        tintDrawables(delete, Color.rgb(197, 83, 75));
        delete.setBackground(panelBg(Color.rgb(255, 247, 244), dp(8), Color.argb(72, 197, 83, 75)));
        profileForm.addView(editorSectionHeading("ОПАСНАЯ ЗОНА", "Удаление человека нельзя отменить после закрытия приложения"), editorBlockParams());
        profileForm.addView(delete, new LinearLayout.LayoutParams(-1, dp(48)));

        final boolean[] undoRecorded = {false};
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (activity.editLocked) return;
                if (!undoRecorded[0]) {
                    recordUndo("Изменена карточка", person.name.isEmpty() ? "Без имени" : person.name);
                    undoRecorded[0] = true;
                }
                person.name = text(name);
                if (!person.genderManual) {
                    person.gender = PersonGender.infer(person.name);
                    updateGenderChip(genderChip, person);
                }
                person.bornDay = datePart(text(bornDay), 31);
                person.bornMonth = datePart(text(bornMonth), 12);
                person.bornYear = yearPart(text(bornYear));
                person.diedDay = datePart(text(diedDay), 31);
                person.diedMonth = datePart(text(diedMonth), 12);
                person.diedYear = yearPart(text(diedYear));
                person.born = humanDate(person.bornDay, person.bornMonth, person.bornYear);
                person.died = humanDate(person.diedDay, person.diedMonth, person.diedYear);
                person.place = text(place);
                person.notes = text(notes);
                person.color = TreeState.displayColor(person, activity.state.people.size());
                if (!"manual".equals(person.colorMode)) {
                    cardColorSlider.setColor(person.color);
                    cardColorPreview.setBackground(panelBg(person.color, dp(999), Color.argb(72, 28, 34, 38)));
                }
                title.setText(person.name.isEmpty() ? "Без имени" : person.name);
                initials.setText(initials(person.name));
                avatar.setBackground(ovalBg(person.color, Color.WHITE, 3));
                ageValue.setText(editorAgeLabel(person));
                birthdayValue.setText(editorBirthdayLabel(person));
                saveOnly();
                activity.treeView.invalidate();
                updateStats();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        name.addTextChangedListener(watcher);
        bornDay.addTextChangedListener(watcher);
        bornMonth.addTextChangedListener(watcher);
        bornYear.addTextChangedListener(watcher);
        diedDay.addTextChangedListener(watcher);
        diedMonth.addTextChangedListener(watcher);
        diedYear.addTextChangedListener(watcher);
        place.addTextChangedListener(watcher);
        notes.addTextChangedListener(watcher);
        pinned.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (activity.editLocked || person.pinned == isChecked) return;
            recordUndo(isChecked ? "Закреплена карточка: " + person.name : "Откреплена карточка: " + person.name);
            person.pinned = isChecked;
            saveOnly();
            activity.treeView.invalidate();
        });

        selectEditorPage(Math.max(0, Math.min(2, currentEditorPage)), pages, tabViews);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(d -> {
            if (currentPersonDialog == dialog) {
                currentPersonDialog = null;
                currentMemoryPersonId = "";
                currentMemoryListHost = null;
                currentMemoryTab = null;
                currentMemoryPanelTitle = null;
                currentMemoryTitleInput = null;
                currentMemoryTextInput = null;
                currentMemoryDraftHost = null;
                currentMemoryDraftAttachments.clear();
            }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
            attrs.height = Math.min(
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.92f),
                dp(900));
            attrs.dimAmount = 0.18f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    void refreshOpenPersonEditor() {
        if (currentPersonDialog != null && currentPersonDialog.isShowing()) openPersonEditor();
    }

    void addMemoryDraftAttachments(
        String personId,
        List<MemoryAttachment> attachments,
        int failedCount
    ) {
        if (currentPersonDialog == null
            || !currentPersonDialog.isShowing()
            || !currentMemoryPersonId.equals(personId)
            || attachments == null
            || attachments.isEmpty()) return;
        int before = currentMemoryDraftAttachments.size();
        for (MemoryAttachment attachment : attachments) {
            if (attachment == null || currentMemoryDraftAttachments.size() >= 24) break;
            currentMemoryDraftAttachments.add(attachment);
        }
        int added = currentMemoryDraftAttachments.size() - before;
        renderMemoryDraft();
        String message = added == 1
            ? "Файл прикреплён к черновику"
            : "Файлы прикреплены к черновику: " + added;
        if (added < attachments.size()) message += " · максимум 24";
        if (failedCount > 0) message += " · не удалось: " + failedCount;
        toast(message);
    }

    private void renderMemoryDraft() {
        if (currentMemoryDraftHost == null) return;
        currentMemoryDraftHost.removeAllViews();
        if (currentMemoryDraftAttachments.isEmpty()) {
            currentMemoryDraftHost.setVisibility(View.GONE);
            return;
        }
        currentMemoryDraftHost.setVisibility(View.VISIBLE);
        TextView heading = editorText(
            "ВЛОЖЕНИЯ К НОВОЙ ЗАПИСИ · " + currentMemoryDraftAttachments.size(),
            10,
            Color.rgb(8, 122, 115),
            true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        currentMemoryDraftHost.addView(heading, new LinearLayout.LayoutParams(-1, dp(28)));
        for (MemoryAttachment attachment : new java.util.ArrayList<>(currentMemoryDraftAttachments)) {
            LinearLayout pill = new LinearLayout(activity);
            pill.setOrientation(LinearLayout.HORIZONTAL);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(dp(10), 0, dp(5), 0);
            pill.setBackground(panelBg(Color.rgb(232, 248, 246), dp(999), Color.argb(70, 8, 122, 115)));

            TextView name = editorText(
                attachment.filename == null || attachment.filename.trim().isEmpty() ? "Файл" : attachment.filename.trim(),
                11,
                Color.rgb(43, 56, 64),
                true);
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setSingleLine(true);
            name.setCompoundDrawablesWithIntrinsicBounds(attachmentIcon(attachment), 0, 0, 0);
            name.setCompoundDrawablePadding(dp(7));
            tintDrawables(name, Color.rgb(8, 122, 115));
            name.setOnClickListener(v -> openAttachment(attachment));
            pill.addView(name, new LinearLayout.LayoutParams(0, -1, 1));

            TextView close = attachmentCloseButton();
            close.setContentDescription("Убрать файл из черновика");
            close.setOnClickListener(v -> {
                currentMemoryDraftAttachments.remove(attachment);
                renderMemoryDraft();
            });
            pill.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
            currentMemoryDraftHost.addView(pill, attachmentPillParams());
        }
    }

    void refreshMemorySection(String personId) {
        refreshMemorySection(personId, true);
    }

    private void refreshMemorySection(String personId, boolean clearDraft) {
        if (currentPersonDialog == null
            || !currentPersonDialog.isShowing()
            || currentMemoryListHost == null
            || !currentMemoryPersonId.equals(personId)) return;
        Person person = activity.state.people.get(personId);
        if (person == null) return;
        currentMemoryListHost.animate().cancel();
        currentMemoryListHost.animate()
            .alpha(0f)
            .setDuration(90)
            .withEndAction(() -> {
                if (currentMemoryListHost == null || !currentMemoryPersonId.equals(personId)) return;
                renderMemoryList(currentMemoryListHost, person);
                currentMemoryListHost.setAlpha(0f);
                currentMemoryListHost.animate().alpha(1f).setDuration(150).start();
            })
            .start();
        if (currentMemoryTab != null) currentMemoryTab.setText("Память · " + person.memories.size());
        if (currentMemoryPanelTitle != null) {
            currentMemoryPanelTitle.setText("ЗАПИСИ И ФАЙЛЫ · " + person.memories.size());
        }
        if (clearDraft) {
            if (currentMemoryTitleInput != null) currentMemoryTitleInput.setText("");
            if (currentMemoryTextInput != null) currentMemoryTextInput.setText("");
        }
    }

    private TextView editorTab(int iconRes, String label) {
        TextView tab = editorText(label, 12, Color.rgb(76, 83, 88), true);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(6), 0, dp(6), 0);
        tab.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        tab.setCompoundDrawablePadding(dp(7));
        tintDrawables(tab, Color.rgb(76, 83, 88));
        return tab;
    }

    private LinearLayout editorPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(6), 0, dp(16));
        return page;
    }

    private ScrollView editorScroll(LinearLayout page) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    private void selectEditorPage(int index, View[] pages, TextView[] tabs) {
        currentEditorPage = index;
        for (int i = 0; i < pages.length; i++) {
            boolean active = i == index;
            pages[i].setVisibility(active ? View.VISIBLE : View.GONE);
            int tabColor = active ? Color.rgb(8, 122, 115) : Color.rgb(76, 83, 88);
            tabs[i].setTextColor(tabColor);
            tintDrawables(tabs[i], tabColor);
            tabs[i].setBackground(active
                ? panelBg(Color.WHITE, dp(8), Color.argb(72, 24, 169, 153))
                : new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private LinearLayout editorSectionHeading(String title, String detail) {
        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(2), dp(2), dp(2), dp(2));
        heading.addView(editorText(title, 11, Color.rgb(8, 122, 115), true), new LinearLayout.LayoutParams(-1, dp(22)));
        TextView description = editorText(detail, 11, Color.rgb(101, 113, 122), false);
        description.setSingleLine(false);
        description.setMaxLines(2);
        heading.addView(description, new LinearLayout.LayoutParams(-1, dp(34)));
        return heading;
    }

    private LinearLayout editorSectionHeading(int iconRes, String title, String detail) {
        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconPlate = new FrameLayout(activity);
        iconPlate.setBackground(panelBg(Color.rgb(232, 248, 246), dp(10), Color.argb(62, 24, 169, 153)));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.rgb(8, 122, 115));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
        iconPlate.addView(icon, iconParams);
        heading.addView(iconPlate, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
        copy.addView(editorText(title, 12, Color.rgb(8, 122, 115), true), new LinearLayout.LayoutParams(-1, dp(23)));
        copy.addView(editorText(detail, 11, Color.rgb(101, 113, 122), false), new LinearLayout.LayoutParams(-1, dp(21)));
        heading.addView(copy, new LinearLayout.LayoutParams(0, dp(46), 1));
        return heading;
    }

    private TextView genderChip(Person person) {
        TextView chip = editorText("", 12, Color.rgb(8, 122, 115), true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), 0, dp(10), 0);
        chip.setBackground(panelBg(Color.rgb(232, 248, 246), dp(999), Color.argb(82, 24, 169, 153)));
        updateGenderChip(chip, person);
        return chip;
    }

    private void updateGenderChip(TextView chip, Person person) {
        if (chip == null) return;
        chip.setText("Пол: " + PersonGender.shortLabel(person));
        chip.setContentDescription("Пол: " + PersonGender.shortLabel(person) + ". Нажмите, чтобы изменить");
    }

    private void chooseGender(Person person, TextView chip) {
        if (person == null || activity.editLocked) return;
        Dialog picker = new Dialog(activity);
        picker.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(18), dp(18), dp(16));
        shell.setBackground(panelBg(Color.WHITE, dp(18), Color.argb(56, 63, 82, 94)));

        TextView eyebrow = editorText("КАРТОЧКА ЧЕЛОВЕКА", 10, Color.rgb(8, 122, 115), true);
        eyebrow.setGravity(Gravity.CENTER);
        shell.addView(eyebrow, new LinearLayout.LayoutParams(-1, dp(20)));
        TextView title = editorText("Выберите пол", 21, Color.rgb(28, 34, 38), true);
        title.setGravity(Gravity.CENTER);
        shell.addView(title, new LinearLayout.LayoutParams(-1, dp(34)));
        TextView detail = editorText(
            "Он используется только для точных названий родства",
            12,
            Color.rgb(101, 113, 122),
            false);
        detail.setGravity(Gravity.CENTER);
        detail.setSingleLine(false);
        detail.setMaxLines(2);
        shell.addView(detail, new LinearLayout.LayoutParams(-1, dp(44)));

        String current = PersonGender.resolve(person);
        shell.addView(genderChoice(
            "М",
            "Мужской",
            "Отец, сын, брат, дедушка",
            person.genderManual && PersonGender.MALE.equals(current),
            () -> {
                picker.dismiss();
                confirmGenderChange(person, chip, PersonGender.MALE, true);
            }), genderChoiceParams());
        shell.addView(genderChoice(
            "Ж",
            "Женский",
            "Мать, дочь, сестра, бабушка",
            person.genderManual && PersonGender.FEMALE.equals(current),
            () -> {
                picker.dismiss();
                confirmGenderChange(person, chip, PersonGender.FEMALE, true);
            }), genderChoiceParams());
        String detected = PersonGender.MALE.equals(PersonGender.infer(person.name))
            ? "Сейчас определяется как мужской"
            : PersonGender.FEMALE.equals(PersonGender.infer(person.name))
                ? "Сейчас определяется как женский"
                : "Уточнится после заполнения имени";
        shell.addView(genderChoice(
            "А",
            "Автоматически",
            detected,
            !person.genderManual,
            () -> {
                picker.dismiss();
                confirmGenderChange(person, chip, PersonGender.infer(person.name), false);
            }), genderChoiceParams());

        Button cancel = actionButton("Отмена", v -> picker.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, dp(46));
        cancelParams.setMargins(0, dp(4), 0, 0);
        shell.addView(cancel, cancelParams);

        picker.setContentView(shell);
        picker.setCanceledOnTouchOutside(true);
        Window window = picker.getWindow();
        if (window != null) {
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(28), dp(480));
            shell.setMinimumWidth(width);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.28f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
        picker.show();
    }

    private View genderChoice(String mark, String title, String detail, boolean selected, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(selected
            ? panelBg(Color.rgb(232, 248, 246), dp(12), Color.rgb(24, 169, 153))
            : panelBg(Color.rgb(250, 252, 253), dp(12), Color.rgb(217, 224, 229)));

        TextView badge = editorText(mark, 16, selected ? Color.WHITE : Color.rgb(8, 122, 115), true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(panelBg(
            selected ? Color.rgb(24, 169, 153) : Color.rgb(232, 248, 246),
            dp(999),
            Color.argb(72, 24, 169, 153)));
        row.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(editorText(title, 14, Color.rgb(28, 34, 38), true), new LinearLayout.LayoutParams(-1, dp(24)));
        copy.addView(editorText(detail, 11, Color.rgb(101, 113, 122), false), new LinearLayout.LayoutParams(-1, dp(22)));
        row.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));

        TextView state = editorText(selected ? "✓" : "›", selected ? 17 : 22, Color.rgb(8, 122, 115), true);
        state.setGravity(Gravity.CENTER);
        row.addView(state, new LinearLayout.LayoutParams(dp(36), -1));
        row.setOnClickListener(v -> action.run());
        row.setClickable(true);
        return row;
    }

    private LinearLayout.LayoutParams genderChoiceParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(68));
        params.setMargins(0, 0, 0, dp(9));
        return params;
    }

    private void confirmGenderChange(Person person, TextView chip, String resolved, boolean manual) {
        if (resolved.equals(PersonGender.resolve(person)) && manual == person.genderManual) return;
        String target = PersonGender.MALE.equals(resolved)
            ? "мужской"
            : PersonGender.FEMALE.equals(resolved) ? "женский" : "не определён";
        activity.showStyledConfirmation(
            R.drawable.ic_field_person,
            "Изменить пол?",
            manual
                ? "Будет установлен " + target + " пол. Это уточнит названия родственных связей."
                : "Пол будет автоматически определяться по имени и отчеству.",
            "Изменить",
            false,
            () -> {
                recordUndo("Изменён пол", person.name.isEmpty() ? "Без имени" : person.name);
                person.genderManual = manual;
                person.gender = resolved;
                saveOnly();
                updateGenderChip(chip, person);
                activity.toast("Пол обновлён");
            });
    }

    private View editorQuickAction(int iconRes, String label, Dialog dialog, String action) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(10), dp(8), dp(10), dp(8));
        tile.setBackground(panelBg(Color.rgb(232, 248, 246), dp(9), Color.argb(72, 24, 169, 153)));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.rgb(8, 122, 115));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(27), dp(27)));
        TextView text = editorText(label, 12, Color.rgb(28, 34, 38), true);
        text.setPadding(dp(9), 0, 0, 0);
        text.setSingleLine(true);
        tile.addView(text, new LinearLayout.LayoutParams(0, -1, 1));
        tile.setOnClickListener(v -> {
            dialog.dismiss();
            activity.addRelationAction(action);
        });
        return tile;
    }

    private TextView editorText(String value, int sizeSp, int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(bold ? uiBold() : ui());
        text.setIncludeFontPadding(false);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private LinearLayout labeledEditor(String label, View editor) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView caption = editorText(label, 12, Color.rgb(76, 83, 88), true);
        block.addView(caption, new LinearLayout.LayoutParams(-1, dp(24)));
        block.addView(editor, new LinearLayout.LayoutParams(-1, -2));
        return block;
    }

    private EditText numericField(String hint, String value, int maxLength) {
        EditText edit = field(hint);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(maxLength)});
        edit.setText(value);
        styleEditorField(edit, R.drawable.ic_field_calendar);
        return edit;
    }

    private void styleEditorField(EditText edit, int iconRes) {
        edit.setMinHeight(dp(50));
        edit.setTextSize(14);
        edit.setBackground(panelBg(Color.WHITE, dp(10), Color.rgb(217, 224, 229)));
        edit.setPadding(dp(14), 0, dp(14), 0);
        if (iconRes != 0) {
            edit.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
            edit.setCompoundDrawablePadding(dp(9));
            tintDrawables(edit, Color.rgb(8, 122, 115));
        }
    }

    private LinearLayout dateEditorRow(EditText day, EditText month, EditText year) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(labeledEditor("День", day), new LinearLayout.LayoutParams(0, -2, 0.82f));
        LinearLayout.LayoutParams monthParams = new LinearLayout.LayoutParams(0, -2, 0.82f);
        monthParams.setMargins(dp(8), 0, 0, 0);
        row.addView(labeledEditor("Месяц", month), monthParams);
        LinearLayout.LayoutParams yearParams = new LinearLayout.LayoutParams(0, -2, 1.15f);
        yearParams.setMargins(dp(8), 0, 0, 0);
        row.addView(labeledEditor("Год", year), yearParams);
        return row;
    }

    private LinearLayout editorMetric(String caption, TextView value) {
        LinearLayout metric = new LinearLayout(activity);
        metric.setOrientation(LinearLayout.HORIZONTAL);
        metric.setGravity(Gravity.CENTER_VERTICAL);
        metric.setPadding(dp(10), dp(7), dp(10), dp(7));
        metric.setBackground(panelBg(Color.WHITE, dp(10), Color.rgb(217, 224, 229)));

        FrameLayout iconPlate = new FrameLayout(activity);
        iconPlate.setBackground(ovalBg(Color.rgb(232, 248, 246), Color.TRANSPARENT, 0));
        ImageView icon = new ImageView(activity);
        icon.setImageResource("ВОЗРАСТ".equals(caption) ? R.drawable.ic_field_person : R.drawable.ic_field_calendar);
        icon.setColorFilter(Color.rgb(8, 122, 115));
        iconPlate.addView(icon, new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER));
        metric.addView(iconPlate, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(9), 0, 0, 0);
        copy.addView(editorText(caption, 9, Color.rgb(101, 113, 122), true), new LinearLayout.LayoutParams(-1, dp(18)));
        copy.addView(value, new LinearLayout.LayoutParams(-1, 0, 1));
        metric.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        return metric;
    }

    private LinearLayout editorSectionPanel(String title) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setElevation(dp(1));
        panel.setBackground(panelBg(Color.WHITE, dp(12), Color.rgb(217, 224, 229)));
        panel.addView(editorText(title, 12, Color.rgb(28, 34, 38), true), new LinearLayout.LayoutParams(-1, dp(28)));
        return panel;
    }

    private TextView editorEmpty(String value) {
        TextView empty = editorText(value, 12, Color.rgb(101, 113, 122), false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(10), dp(8), dp(10), dp(8));
        empty.setBackground(panelBg(Color.rgb(248, 251, 252), dp(8), Color.rgb(217, 224, 229)));
        return empty;
    }

    private View editorMemoryEmpty(String value) {
        LinearLayout empty = new LinearLayout(activity);
        empty.setOrientation(LinearLayout.HORIZONTAL);
        empty.setGravity(Gravity.CENTER_VERTICAL);
        empty.setPadding(dp(18), dp(12), dp(18), dp(12));
        empty.setBackground(panelBg(Color.rgb(235, 249, 247), dp(12), Color.argb(52, 24, 169, 153)));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_menu_add_memory);
        icon.setColorFilter(Color.rgb(8, 122, 115));
        empty.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView text = editorText(value, 12, Color.rgb(76, 87, 96), false);
        text.setPadding(dp(14), 0, 0, 0);
        text.setSingleLine(false);
        text.setMaxLines(2);
        empty.addView(text, new LinearLayout.LayoutParams(0, dp(54), 1));
        return empty;
    }

    private GradientDrawable ovalBg(int color, int strokeColor, int strokeDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        bg.setStroke(dp(strokeDp), strokeColor);
        return bg;
    }

    private LinearLayout.LayoutParams editorBlockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(14));
        return params;
    }

    private LinearLayout.LayoutParams editorCaptionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(24));
        params.setMargins(0, 0, 0, dp(4));
        return params;
    }

    private LinearLayout.LayoutParams editorMetricParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(64));
        params.setMargins(0, 0, 0, dp(14));
        return params;
    }

    private LinearLayout.LayoutParams editorSwitchParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, 0, 0, dp(14));
        return params;
    }

    private String editorAgeLabel(Person person) {
        int bornYear = intValue(person.bornYear);
        if (bornYear <= 0) return "нет даты рождения";
        Calendar target = Calendar.getInstance();
        int diedYear = intValue(person.diedYear);
        if (diedYear > 0) {
            target.set(
                diedYear,
                Math.max(0, intValue(person.diedMonth) - 1),
                Math.max(1, intValue(person.diedDay)));
        }
        int age = target.get(Calendar.YEAR) - bornYear;
        int bornMonth = intValue(person.bornMonth);
        int bornDay = intValue(person.bornDay);
        if (bornMonth > 0 && bornDay > 0) {
            int targetMonth = target.get(Calendar.MONTH) + 1;
            int targetDay = target.get(Calendar.DAY_OF_MONTH);
            if (targetMonth < bornMonth || (targetMonth == bornMonth && targetDay < bornDay)) age--;
        }
        if (age < 0) return "нет даты рождения";
        return age + " " + pluralRu(age, "год", "года", "лет");
    }

    private String editorBirthdayLabel(Person person) {
        int day = intValue(person.bornDay);
        int month = intValue(person.bornMonth);
        if (day <= 0 || month <= 0) return "укажите день и месяц";
        Calendar today = Calendar.getInstance();
        clearClock(today);
        Calendar next = Calendar.getInstance();
        clearClock(next);
        next.set(Calendar.MONTH, month - 1);
        next.set(Calendar.DAY_OF_MONTH, day);
        if (next.before(today)) next.add(Calendar.YEAR, 1);
        int days = Math.round((next.getTimeInMillis() - today.getTimeInMillis()) / 86400000f);
        if (days == 0) return "сегодня";
        if (days == 1) return "завтра";
        return days + " " + pluralRu(days, "день", "дня", "дней");
    }

    private void clearClock(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private int intValue(String value) {
        try {
            return Integer.parseInt(value == null || value.isEmpty() ? "0" : value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String pluralRu(int number, String one, String few, String many) {
        int value = Math.abs(number) % 100;
        int last = value % 10;
        if (value > 10 && value < 20) return many;
        if (last > 1 && last < 5) return few;
        if (last == 1) return one;
        return many;
    }

    private String memoryCountLabel(int count) {
        return count + " " + pluralRu(count, "запись", "записи", "записей");
    }

    private String initials(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) return "?";
        String[] parts = value.split("\\s+");
        String first = parts[0].isEmpty() ? "" : parts[0].substring(0, 1);
        String second = parts.length > 1 && !parts[1].isEmpty() ? parts[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }

    private void addKinshipRows(LinearLayout panel, Person person, Dialog dialog) {
        java.util.List<String> parents = new java.util.ArrayList<>();
        java.util.List<String> children = new java.util.ArrayList<>();
        java.util.LinkedHashSet<String> partners = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> siblings = new java.util.LinkedHashSet<>();
        for (Relation link : activity.state.links) {
            if ("parent".equals(link.type)) {
                if (person.id.equals(link.to)) parents.add(link.from);
                if (person.id.equals(link.from)) children.add(link.to);
            } else if ("partner".equals(link.type) || "family".equals(link.type)) {
                if (person.id.equals(link.from)) partners.add(link.to);
                if (person.id.equals(link.to)) partners.add(link.from);
            } else if ("sibling".equals(link.type)) {
                if (person.id.equals(link.from)) siblings.add(link.to);
                if (person.id.equals(link.to)) siblings.add(link.from);
            }
        }
        java.util.Set<String> parentSet = new java.util.HashSet<>(parents);
        if (!parentSet.isEmpty()) {
            for (Relation link : activity.state.links) {
                if ("parent".equals(link.type) && parentSet.contains(link.from) && !person.id.equals(link.to)) siblings.add(link.to);
            }
        }
        panel.addView(kinshipRow("Родители", parents, dialog), formFieldParams());
        panel.addView(kinshipRow("Партнёры", new java.util.ArrayList<>(partners), dialog), formFieldParams());
        panel.addView(kinshipRow("Дети", children, dialog), formFieldParams());
        panel.addView(kinshipRow("Братья/сёстры", new java.util.ArrayList<>(siblings), dialog), formFieldParams());
    }

    private View kinshipRow(String title, java.util.List<String> ids, Dialog dialog) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(7), dp(10), dp(7));
        row.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        TextView heading = editorText(title + "  " + ids.size(), 12, Color.rgb(28, 34, 38), true);
        row.addView(heading, new LinearLayout.LayoutParams(-1, dp(24)));
        if (ids.isEmpty()) {
            row.addView(editorText("Нет связей", 11, Color.rgb(101, 113, 122), false), new LinearLayout.LayoutParams(-1, dp(24)));
        } else {
            for (String id : ids) {
                Person relative = activity.state.people.get(id);
                if (relative == null) continue;
                TextView link = editorText(relative.name.isEmpty() ? "Без имени" : relative.name, 12, Color.rgb(8, 122, 115), true);
                link.setPadding(dp(8), 0, dp(8), 0);
                link.setBackground(panelBg(Color.rgb(232, 248, 246), dp(6), Color.argb(72, 24, 169, 153)));
                link.setOnClickListener(v -> {
                    activity.state.selectedId = relative.id;
                    saveOnly();
                    dialog.dismiss();
                    activity.showPanel("");
                    bindState();
                    activity.treeView.post(() -> activity.treeView.focusPerson(relative.id));
                    activity.toast("Выбрана карточка: " + (relative.name.isEmpty() ? "Без имени" : relative.name));
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(36));
                params.setMargins(0, 0, 0, dp(5));
                row.addView(link, params);
            }
        }
        return row;
    }

    private void addPersonValidation(LinearLayout panel, Person person) {
        java.util.List<String> tips = new java.util.ArrayList<>();
        if ((person.photoMediaId == null || person.photoMediaId.isEmpty())
            && (person.photo == null || person.photo.isEmpty())) tips.add("Добавьте фото");
        if (person.bornYear.isEmpty()) tips.add("Уточните год рождения");
        if (person.memories.isEmpty()) tips.add("Сохраните первую историю");
        if (parentIdsOf(person.id).isEmpty()) tips.add("Добавьте родителей");
        if (person.place.isEmpty()) tips.add("Добавьте место");
        if (tips.isEmpty()) {
            panel.addView(editorEmpty("Подсказок нет"), editorBlockParams());
            return;
        }
        for (String tip : tips) {
            TextView item = editorText(tip, 12, Color.rgb(28, 34, 38), true);
            item.setPadding(dp(10), 0, dp(10), 0);
            item.setBackground(panelBg(Color.rgb(248, 251, 252), dp(8), Color.rgb(217, 224, 229)));
            panel.addView(item, formFieldParams());
        }
    }

    private void savePersonEditor(Person person, String name, String bornDay, String bornMonth, String bornYear, String diedDay, String diedMonth, String diedYear, String place, String notes, String manualColor, boolean pinned, String memoryTitle, String memoryText) {
        if (person == null || activity.editLocked) return;
        recordUndo("Изменена карточка", person.name.isEmpty() ? "Без имени" : person.name);
        person.name = name.trim().isEmpty() ? "Без имени" : name.trim();
        if (!person.genderManual) person.gender = PersonGender.infer(person.name);
        person.bornDay = datePart(bornDay, 31);
        person.bornMonth = datePart(bornMonth, 12);
        person.bornYear = yearPart(bornYear);
        person.diedDay = datePart(diedDay, 31);
        person.diedMonth = datePart(diedMonth, 12);
        person.diedYear = yearPart(diedYear);
        person.born = humanDate(person.bornDay, person.bornMonth, person.bornYear);
        person.died = humanDate(person.diedDay, person.diedMonth, person.diedYear);
        person.place = place.trim();
        person.notes = notes.trim();
        person.pinned = pinned;
        if (!manualColor.trim().isEmpty()) {
            person.colorMode = "manual";
            person.manualColor = TreeState.colorString(TreeState.parseColor(manualColor.trim(), TreeState.displayColor(person, activity.state.people.size())));
        }
        person.color = TreeState.displayColor(person, activity.state.people.size());
        saveToast("Карточка сохранена");
        bindState();
        activity.treeView.invalidate();
    }

    private void renderMemoryList(LinearLayout host, Person person) {
        host.removeAllViews();
        if (person.memories.isEmpty()) {
            host.addView(editorMemoryEmpty(
                "Добавьте фото, документ или семейную историю"), editorBlockParams());
            return;
        }
        addMemoryList(host, person);
    }

    private void addMemoryList(LinearLayout form, Person person) {
        for (Memory memory : person.memories) {
            List<MemoryAttachment> attachments = memoryAttachments(memory);
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(8), dp(10));
            card.setBackground(panelBg(Color.WHITE, dp(12), Color.rgb(217, 224, 229)));

            LinearLayout header = new LinearLayout(activity);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setOrientation(LinearLayout.HORIZONTAL);

            TextView title = new TextView(activity);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setText(memory.title == null || memory.title.trim().isEmpty() ? "Воспоминание" : memory.title.trim());
            title.setTextColor(Color.rgb(28, 34, 38));
            title.setTextSize(14);
            title.setTypeface(uiBold());
            title.setMaxLines(2);
            title.setOnClickListener(v -> openMemory(person, memory));
            header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));

            TextView delete = attachmentCloseButton();
            delete.setContentDescription("Удалить запись");
            delete.setOnClickListener(v -> confirmRemoveMemory(person.id, memory.id));
            header.addView(delete, new LinearLayout.LayoutParams(dp(38), dp(38)));
            card.addView(header, new LinearLayout.LayoutParams(-1, -2));

            if (memory.text != null && !memory.text.trim().isEmpty()) {
                TextView description = editorText(memory.text.trim(), 12, Color.rgb(83, 94, 103), false);
                description.setMaxLines(3);
                description.setLineSpacing(dp(2), 1f);
                description.setOnClickListener(v -> openMemory(person, memory));
                LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
                descriptionParams.setMargins(0, 0, dp(6), dp(8));
                card.addView(description, descriptionParams);
            }

            if (!attachments.isEmpty()) {
                TextView summary = editorText(
                    "Открыть запись · " + attachmentSummary(attachments),
                    11,
                    Color.rgb(8, 122, 115),
                    true);
                summary.setGravity(Gravity.CENTER_VERTICAL);
                summary.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_eye, 0, 0, 0);
                summary.setCompoundDrawablePadding(dp(7));
                tintDrawables(summary, Color.rgb(8, 122, 115));
                summary.setPadding(dp(10), 0, dp(10), 0);
                summary.setBackground(panelBg(Color.rgb(232, 248, 246), dp(999), Color.argb(70, 8, 122, 115)));
                summary.setOnClickListener(v -> showMemoryDetails(person, memory));
                LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, dp(36));
                summaryParams.setMargins(0, 0, dp(4), 0);
                card.addView(summary, summaryParams);
            }
            form.addView(card, formFieldParams());
        }
    }

    private TextView attachmentCloseButton() {
        TextView close = new TextView(activity);
        close.setText("×");
        close.setGravity(Gravity.CENTER);
        close.setTextColor(Color.rgb(197, 83, 75));
        close.setTextSize(19);
        close.setTypeface(uiBold());
        close.setBackground(panelBg(Color.rgb(255, 247, 244), dp(999), Color.argb(72, 197, 83, 75)));
        return close;
    }

    private LinearLayout.LayoutParams attachmentPillParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(40));
        params.setMargins(0, 0, dp(4), dp(6));
        return params;
    }

    private int attachmentIcon(MemoryAttachment attachment) {
        return "photo".equals(attachment.type) ? R.drawable.ic_menu_image : R.drawable.ic_menu_file;
    }

    private List<MemoryAttachment> memoryAttachments(Memory memory) {
        if (memory.attachments.isEmpty() && memory.data != null && !memory.data.isEmpty()) {
            MemoryAttachment attachment = new MemoryAttachment();
            attachment.id = "a_" + java.util.UUID.randomUUID().toString().replace("-", "");
            attachment.filename = memory.filename == null || memory.filename.isEmpty() ? "Файл" : memory.filename;
            attachment.mimeType = memory.mimeType == null || memory.mimeType.isEmpty()
                ? "application/octet-stream"
                : memory.mimeType;
            attachment.type = memory.type == null || memory.type.isEmpty() ? "document" : memory.type;
            attachment.data = memory.data;
            memory.attachments.add(attachment);
        }
        return memory.attachments;
    }

    private String attachmentSummary(List<MemoryAttachment> attachments) {
        int photos = 0;
        int documents = 0;
        int media = 0;
        for (MemoryAttachment attachment : attachments) {
            if ("photo".equals(attachment.type)) photos++;
            else if ("audio".equals(attachment.type) || "video".equals(attachment.type)) media++;
            else documents++;
        }
        StringBuilder summary = new StringBuilder();
        summary.append(attachments.size()).append(" ").append(fileCountLabel(attachments.size()));
        if (photos > 0) summary.append(" · фото: ").append(photos);
        if (documents > 0) summary.append(" · документов: ").append(documents);
        if (media > 0) summary.append(" · медиа: ").append(media);
        return summary.toString();
    }

    private String fileCountLabel(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod100 >= 11 && mod100 <= 14) return "файлов";
        if (mod10 == 1) return "файл";
        if (mod10 >= 2 && mod10 <= 4) return "файла";
        return "файлов";
    }

    private String memoryTypeLabel(String type) {
        if ("photo".equals(type)) return "Фото";
        if ("document".equals(type)) return "Документ";
        if ("audio".equals(type)) return "Аудио";
        if ("video".equals(type)) return "Видео";
        if ("source".equals(type)) return "Источник";
        return "История";
    }

    private void openMemory(Person person, Memory memory) {
        if (memory == null) return;
        if (!memoryAttachments(memory).isEmpty()) showMemoryDetails(person, memory);
        else showHelp(
            memory.title == null || memory.title.isEmpty() ? "Воспоминание" : memory.title,
            memory.text == null || memory.text.trim().isEmpty() ? "Описание пока не добавлено." : memory.text);
    }

    private void showMemoryDetails(Person person, Memory memory) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(16), dp(18), dp(16));
        shell.setBackground(panelBg(Color.WHITE, dp(18), Color.argb(56, 63, 82, 94)));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_editor_archive);
        icon.setColorFilter(Color.rgb(8, 122, 115));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(panelBg(Color.rgb(232, 248, 246), dp(999), Color.TRANSPARENT));
        top.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = editorText(
            memory.title == null || memory.title.trim().isEmpty() ? "Воспоминание" : memory.title.trim(),
            18,
            Color.rgb(28, 34, 38),
            true);
        title.setMaxLines(2);
        heading.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView count = editorText(attachmentSummary(memoryAttachments(memory)), 11, Color.rgb(8, 122, 115), true);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
        countParams.setMargins(0, dp(3), 0, 0);
        heading.addView(count, countParams);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1);
        headingParams.setMargins(dp(11), 0, dp(8), 0);
        top.addView(heading, headingParams);
        top.addView(iconButton(R.drawable.ic_menu_close, v -> dialog.dismiss()), new LinearLayout.LayoutParams(dp(40), dp(40)));
        shell.addView(top);

        if (memory.text != null && !memory.text.trim().isEmpty()) {
            TextView description = editorText(memory.text.trim(), 13, Color.rgb(76, 87, 96), false);
            description.setLineSpacing(dp(2), 1f);
            description.setPadding(dp(12), dp(10), dp(12), dp(10));
            description.setBackground(panelBg(Color.rgb(247, 250, 251), dp(10), Color.rgb(220, 228, 232)));
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.setMargins(0, dp(14), 0, dp(10));
            shell.addView(description, descriptionParams);
        }

        LinearLayout files = new LinearLayout(activity);
        files.setOrientation(LinearLayout.VERTICAL);
        for (MemoryAttachment attachment : memoryAttachments(memory)) {
            files.addView(
                memoryAttachmentPreviewRow(person, memory, attachment, files, count, dialog),
                formFieldParams());
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(files);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = editorText("Нажмите на файл или фотографию, чтобы открыть", 11, Color.rgb(101, 113, 122), false);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, dp(34));
        hintParams.setMargins(0, dp(8), 0, 0);
        shell.addView(hint, hintParams);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(28), dp(520));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = Math.min(
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.78f),
                dp(720));
            attrs.dimAmount = 0.32f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    private View memoryAttachmentPreviewRow(
        Person person,
        Memory memory,
        MemoryAttachment attachment,
        LinearLayout files,
        TextView count,
        Dialog dialog
    ) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(10), dp(6));
        row.setBackground(panelBg(Color.rgb(248, 251, 252), dp(12), Color.rgb(214, 224, 229)));

        ImageView preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setPadding(dp(8), dp(8), dp(8), dp(8));
        boolean imageAttachment = (attachment.mimeType != null
            && attachment.mimeType.startsWith("image/"))
            || "photo".equals(attachment.type);
        Bitmap bitmap = imageAttachment
            && attachment.mediaId != null
            && !attachment.mediaId.isEmpty()
                ? activity.store.mediaStore().decodeBitmap(attachment.mediaId, 256)
                : attachment.data != null && attachment.data.startsWith("data:image/")
                    ? bitmapFromDataUrl(attachment.data)
                    : null;
        if (bitmap != null) {
            preview.setImageBitmap(bitmap);
            preview.setPadding(0, 0, 0, 0);
        } else {
            preview.setImageResource(attachmentIcon(attachment));
            preview.setColorFilter(Color.rgb(8, 122, 115));
            preview.setBackground(panelBg(Color.rgb(232, 248, 246), dp(10), Color.TRANSPARENT));
        }
        row.addView(preview, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = editorText(
            attachment.filename == null || attachment.filename.trim().isEmpty() ? "Файл" : attachment.filename.trim(),
            12,
            Color.rgb(28, 34, 38),
            true);
        name.setSingleLine(true);
        labels.addView(name, new LinearLayout.LayoutParams(-1, dp(25)));
        TextView type = editorText(memoryTypeLabel(attachment.type) + " · открыть", 10, Color.rgb(8, 122, 115), true);
        labels.addView(type, new LinearLayout.LayoutParams(-1, dp(21)));
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelsParams.setMargins(dp(10), 0, 0, 0);
        row.addView(labels, labelsParams);

        TextView close = attachmentCloseButton();
        close.setContentDescription("Удалить файл " + attachment.filename);
        close.setOnClickListener(v -> confirmRemoveAttachment(
            person.id,
            memory.id,
            attachment.id,
            () -> row.animate()
                .alpha(0f)
                .scaleY(0.72f)
                .setDuration(150)
                .withEndAction(() -> {
                    files.removeView(row);
                    List<MemoryAttachment> remaining = memoryAttachments(memory);
                    if (remaining.isEmpty()) dialog.dismiss();
                    else count.setText(attachmentSummary(remaining));
                })
                .start()));
        row.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        row.setOnClickListener(v -> openAttachment(attachment));
        return row;
    }

    private void openAttachment(MemoryAttachment attachment) {
        if (attachment == null
            || (attachment.mediaId == null || attachment.mediaId.isEmpty())
            && (attachment.data == null || attachment.data.isEmpty())) {
            toast("Файл не удалось открыть");
            return;
        }
        boolean imageAttachment = attachment.mimeType != null
            && attachment.mimeType.startsWith("image/")
            || "photo".equals(attachment.type);
        if (imageAttachment && attachment.mediaId != null && !attachment.mediaId.isEmpty()) {
            activity.showMediaPhotoPreview(attachment.mediaId);
            return;
        }
        if (attachment.data != null && attachment.data.startsWith("data:image/")) {
            showPhotoPreview(attachment.data);
            return;
        }
        try {
            File directory = new File(activity.getCacheDir(), "shared");
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("не создана временная папка");
            String filename = safeAttachmentFilename(attachment.filename);
            File file = new File(directory, filename);
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (attachment.mediaId != null && !attachment.mediaId.isEmpty()) {
                    activity.store.mediaStore().copyTo(attachment.mediaId, output);
                } else {
                    int comma = attachment.data.indexOf(',');
                    if (comma < 0 || comma + 1 >= attachment.data.length()) {
                        throw new IllegalStateException("повреждённые данные");
                    }
                    byte[] bytes = Base64.decode(
                        attachment.data.substring(comma + 1),
                        Base64.DEFAULT);
                    output.write(bytes);
                }
            }
            Uri uri = TreeShareProvider.uriFor(file.getName());
            String mime = attachment.mimeType == null || attachment.mimeType.trim().isEmpty()
                ? "application/octet-stream"
                : attachment.mimeType;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(android.content.ClipData.newRawUri(file.getName(), uri));
            activity.startActivity(Intent.createChooser(intent, "Открыть файл"));
        } catch (Exception error) {
            toast("Не удалось открыть файл: " + error.getMessage());
        }
    }

    private String safeAttachmentFilename(String value) {
        String name = value == null ? "" : value.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        if (name.isEmpty()) name = "attachment";
        if (name.length() > 140) name = name.substring(name.length() - 140);
        return Integer.toHexString(name.hashCode()) + "-" + name;
    }

    private void confirmRemoveMemory(String personId, String memoryId) {
        if (activity.editLocked) return;
        Person person = activity.state.people.get(personId);
        Memory memory = null;
        if (person != null) {
            for (Memory item : person.memories) {
                if (memoryId.equals(item.id)) {
                    memory = item;
                    break;
                }
            }
        }
        String title = memory == null || memory.title == null || memory.title.trim().isEmpty()
            ? "Эта запись"
            : "«" + memory.title.trim() + "»";
        activity.showStyledConfirmation(
            R.drawable.ic_menu_trash,
            "Удалить запись из памяти?",
            title + " будет удалена из карточки. Остальные воспоминания и файлы сохранятся.",
            "Удалить",
            true,
            () -> removeMemory(personId, memoryId));
    }

    private void removeMemory(String personId, String memoryId) {
        Person person = activity.state.people.get(personId);
        if (person == null) return;
        recordUndo("Удалена запись памяти", person.name.isEmpty() ? "Без имени" : person.name);
        person.memories.removeIf(memory -> memoryId.equals(memory.id));
        saveToast("Запись удалена");
        bindState();
        activity.treeView.invalidate();
        refreshMemorySection(personId, false);
    }

    private void confirmRemoveAttachment(
        String personId,
        String memoryId,
        String attachmentId,
        Runnable afterRemoval
    ) {
        if (activity.editLocked) return;
        Person person = activity.state.people.get(personId);
        MemoryAttachment target = null;
        if (person != null) {
            for (Memory memory : person.memories) {
                if (!memoryId.equals(memory.id)) continue;
                for (MemoryAttachment attachment : memoryAttachments(memory)) {
                    if (attachmentId.equals(attachment.id)) {
                        target = attachment;
                        break;
                    }
                }
            }
        }
        String filename = target == null || target.filename == null || target.filename.trim().isEmpty()
            ? "Этот файл"
            : "«" + target.filename.trim() + "»";
        activity.showStyledConfirmation(
            R.drawable.ic_menu_trash,
            "Удалить файл из записи?",
            filename + " будет удалён. Название, подпись и остальные вложения записи сохранятся.",
            "Удалить файл",
            true,
            () -> removeAttachment(personId, memoryId, attachmentId, afterRemoval));
    }

    private void removeAttachment(
        String personId,
        String memoryId,
        String attachmentId,
        Runnable afterRemoval
    ) {
        Person person = activity.state.people.get(personId);
        if (person == null) return;
        for (Memory memory : person.memories) {
            if (!memoryId.equals(memory.id)) continue;
            recordUndo("Удалён файл из записи", person.name.isEmpty() ? "Без имени" : person.name);
            memoryAttachments(memory).removeIf(attachment -> attachmentId.equals(attachment.id));
            syncLegacyAttachment(memory);
            saveToast("Файл удалён из записи");
            bindState();
            activity.treeView.invalidate();
            refreshMemorySection(personId, false);
            if (afterRemoval != null) afterRemoval.run();
            return;
        }
    }

    private void syncLegacyAttachment(Memory memory) {
        if (memory.attachments.isEmpty()) {
            memory.filename = "";
            memory.mimeType = "";
            memory.data = "";
            memory.type = "story";
            return;
        }
        MemoryAttachment first = memory.attachments.get(0);
        memory.filename = first.filename;
        memory.mimeType = first.mimeType;
        memory.data = first.mediaId == null || first.mediaId.isEmpty() ? first.data : "";
        memory.type = memory.attachments.size() == 1 ? first.type : "document";
    }
    void updateSelectedFromEditor() {
        Person person = activity.state.selectedPerson();
        if (person == null) return;
        person.name = text(activity.nameInput);
        if (!person.genderManual) person.gender = PersonGender.infer(person.name);
        person.bornYear = text(activity.bornInput);
        person.diedYear = text(activity.diedInput);
        person.place = text(activity.placeInput);
        person.notes = text(activity.notesInput);
        person.color = TreeState.displayColor(person, activity.state.people.size());
        saveOnly();
        activity.treeView.invalidate();
        updateStats();
    }

    private int dp(int value) { return activity.dp(value); }
    private android.graphics.Typeface ui() { return activity.ui(); }
    private android.graphics.Typeface uiBold() { return activity.uiBold(); }
    private android.graphics.drawable.GradientDrawable panelBg(int color, int radius, int stroke) { return activity.panelBg(color, radius, stroke); }
    private android.graphics.drawable.GradientDrawable tealGradientBg(int radius) { return activity.tealGradientBg(radius); }
    private android.widget.Button iconButton(int iconRes, android.view.View.OnClickListener listener) { return activity.iconButton(iconRes, listener); }
    private android.widget.Button iconButton(int iconRes, android.view.View.OnClickListener listener, int textColor) { return activity.iconButton(iconRes, listener, textColor); }
    private android.widget.Button actionButton(String text, android.view.View.OnClickListener listener) { return activity.actionButton(text, listener); }
    private android.widget.EditText field(String hint) { return activity.field(hint); }
    private android.widget.LinearLayout.LayoutParams formFieldParams() { return activity.formFieldParams(); }
    private android.widget.LinearLayout.LayoutParams spacedButtonParams() { return activity.spacedButtonParams(); }
    private String text(android.widget.EditText editText) { return activity.text(editText); }
    private String datePart(String value, int max) { return activity.datePart(value, max); }
    private String yearPart(String value) { return activity.yearPart(value); }
    private String humanDate(String day, String month, String year) { return activity.humanDate(day, month, year); }
    private android.graphics.Bitmap bitmapFromDataUrl(String dataUrl) { return activity.bitmapFromDataUrl(dataUrl); }
    private void showPhotoPreview(String dataUrl) { activity.showPhotoPreview(dataUrl); }
    private void openPhotoPicker() { activity.openPhotoPicker(); }
    private void openMemoryFilePicker() { activity.openMemoryFilePicker(); }
    private void recordUndo(String label, String detail) { activity.recordUndo(label, detail); }
    private void recordUndo(String label) { activity.recordUndo(label); }
    private void saveOnly() { activity.saveOnly(); }
    private void saveToast() { activity.saveToast(); }
    private void saveToast(String message) { activity.saveToast(message); }
    private void bindState() { activity.bindState(); }
    private void updateStats() { activity.updateStats(); }
    private void setRootPerson(String personId) { activity.setRootPerson(personId); }
    private java.util.List<String> parentIdsOf(String childId) { return activity.parentIdsOf(childId); }
    private void confirmDelete() { activity.confirmDelete(); }
    private void showHelp(String title, String text) { activity.showHelp(title, text); }
    private void tintDrawables(android.widget.TextView view, int color) { activity.tintDrawables(view, color); }
    private void toast(String message) { activity.toast(message); }
}
