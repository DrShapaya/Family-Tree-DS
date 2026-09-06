package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.UUID;

final class MainActivityQuickStart {
    private final MainActivity activity;

    MainActivityQuickStart(MainActivity activity) {
        this.activity = activity;
    }

    void open() {
        if (!activity.requireEditingEnabled()) return;
        if (activity.state != null && !activity.state.people.isEmpty()) {
            activity.showStyledConfirmation(
                R.drawable.ic_menu_sparkles,
                "Создать новое дерево?",
                "Текущее дерево будет удалено. Продолжить?",
                "Продолжить",
                false,
                this::openDialog);
            return;
        }
        openDialog();
    }

    private void openDialog() {
        if (!activity.requireEditingEnabled()) return;
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(12), dp(18), dp(18));
        shell.setBackground(activity.panelBg(Color.rgb(250, 252, 253), dp(20), Color.argb(44, 63, 82, 94)));

        View handle = new View(activity);
        handle.setBackground(activity.panelBg(Color.rgb(194, 207, 214), dp(999), Color.TRANSPARENT));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(44), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(10));
        shell.addView(handle, handleParams);

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = caption("НОВОЕ СЕМЕЙНОЕ ДЕРЕВО");
        TextView title = new LocalizedTextView(activity);
        title.setText("Быстрый старт");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(24);
        title.setTypeface(activity.uiBold());
        title.setIncludeFontPadding(false);
        heading.addView(eyebrow, new LinearLayout.LayoutParams(-1, dp(18)));
        heading.addView(title, new LinearLayout.LayoutParams(-1, dp(34)));
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(54), 1));
        header.addView(activity.closeButton(v -> dialog.dismiss()), new LinearLayout.LayoutParams(dp(42), dp(42)));
        shell.addView(header);

        LinearLayout progress = new LinearLayout(activity);
        progress.setOrientation(LinearLayout.HORIZONTAL);
        progress.setGravity(Gravity.CENTER_VERTICAL);
        TextView personStep = step("1", "Вы");
        TextView familyStep = step("2", "Семья");
        View progressLine = new View(activity);
        progress.addView(personStep, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dp(34), dp(2));
        lineParams.setMargins(dp(8), 0, dp(8), 0);
        progress.addView(progressLine, lineParams);
        progress.addView(familyStep, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(54));
        progressParams.setMargins(0, dp(8), 0, dp(10));
        shell.addView(progress, progressParams);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout formHost = new LinearLayout(activity);
        formHost.setOrientation(LinearLayout.VERTICAL);
        formHost.setPadding(0, dp(2), 0, dp(6));
        scroll.addView(formHost);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        EditText selfName = activity.field("Например, Иван Иванов");
        selfName.setSingleLine(true);
        EditText selfYear = activity.field("Например, 1987");
        selfYear.setSingleLine(true);
        selfYear.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText selfPlace = activity.field("Город или место, необязательно");
        selfPlace.setSingleLine(true);
        EditText fatherName = activity.field("Имя папы, необязательно");
        fatherName.setSingleLine(true);
        EditText motherName = activity.field("Имя мамы, необязательно");
        motherName.setSingleLine(true);
        EditText paternalGrandfather = activity.field("Имя дедушки по папе, необязательно");
        paternalGrandfather.setSingleLine(true);
        EditText paternalGrandmother = activity.field("Имя бабушки по папе, необязательно");
        paternalGrandmother.setSingleLine(true);
        EditText maternalGrandfather = activity.field("Имя дедушки по маме, необязательно");
        maternalGrandfather.setSingleLine(true);
        EditText maternalGrandmother = activity.field("Имя бабушки по маме, необязательно");
        maternalGrandmother.setSingleLine(true);
        EditText story = activity.field("Что важно сохранить для семьи?");
        story.setMinLines(4);
        story.setGravity(Gravity.CENTER_VERTICAL);
        story.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout personPage = new LinearLayout(activity);
        personPage.setOrientation(LinearLayout.VERTICAL);
        personPage.addView(intro(
            R.drawable.ic_nav_card,
            "Начнём с вашей карточки",
            "Она станет центром дерева. Остальные сведения можно добавить позже."),
            sectionParams());
        LinearLayout selfCard = section("Основные сведения", "Обязательное поле только одно — имя");
        selfCard.addView(field("Имя", selfName, R.drawable.ic_field_person), activity.formFieldParams());
        LinearLayout selfDetails = new LinearLayout(activity);
        selfDetails.setOrientation(LinearLayout.HORIZONTAL);
        selfDetails.addView(field("Год рождения", selfYear, R.drawable.ic_field_calendar), new LinearLayout.LayoutParams(0, -2, 0.8f));
        selfDetails.addView(field("Место", selfPlace, R.drawable.ic_field_location), spacedInputParams());
        selfCard.addView(selfDetails, activity.formFieldParams());
        personPage.addView(selfCard, sectionParams());

        TextView personHint = note(activity.state != null && activity.state.people.size() > 1
            ? "Будет создано новое дерево. Текущее можно вернуть командой «Отменить» в истории действий."
            : "После создания откроется обычное дерево: карточки можно дополнять, перемещать и связывать.");
        personPage.addView(personHint, sectionParams());

        LinearLayout familyPage = new LinearLayout(activity);
        familyPage.setOrientation(LinearLayout.VERTICAL);
        familyPage.setVisibility(View.GONE);
        familyPage.addView(intro(
            R.drawable.ic_menu_people,
            "Добавьте ближайшую семью",
            "Оставьте поле пустым, если пока не хотите создавать эту карточку."),
            sectionParams());
        LinearLayout parentsCard = section("Родители", "Папа будет слева, мама — справа");
        parentsCard.addView(field("Папа", fatherName, R.drawable.ic_field_person), activity.formFieldParams());
        parentsCard.addView(field("Мама", motherName, R.drawable.ic_field_person), activity.formFieldParams());
        familyPage.addView(parentsCard, sectionParams());
        LinearLayout fatherParentsCard = section("Родители папы", "Будут связаны с карточкой папы");
        fatherParentsCard.addView(field("Дедушка", paternalGrandfather, R.drawable.ic_field_person), activity.formFieldParams());
        fatherParentsCard.addView(field("Бабушка", paternalGrandmother, R.drawable.ic_field_person), activity.formFieldParams());
        fatherParentsCard.setVisibility(View.GONE);
        familyPage.addView(fatherParentsCard, sectionParams());
        LinearLayout motherParentsCard = section("Родители мамы", "Будут связаны с карточкой мамы");
        motherParentsCard.addView(field("Дедушка", maternalGrandfather, R.drawable.ic_field_person), activity.formFieldParams());
        motherParentsCard.addView(field("Бабушка", maternalGrandmother, R.drawable.ic_field_person), activity.formFieldParams());
        motherParentsCard.setVisibility(View.GONE);
        familyPage.addView(motherParentsCard, sectionParams());
        LinearLayout storyCard = section("Первая история", "Необязательно — сохранится в вашей карточке");
        storyCard.addView(field("Семейная заметка", story, R.drawable.ic_field_note), activity.formFieldParams());
        familyPage.addView(storyCard, sectionParams());

        TextView preview = new LocalizedTextView(activity);
        preview.setTextColor(Color.rgb(8, 122, 115));
        preview.setTextSize(11);
        preview.setTypeface(activity.uiBold());
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setPadding(dp(12), dp(8), dp(12), dp(8));
        preview.setBackground(activity.panelBg(Color.rgb(232, 248, 246), dp(10), Color.argb(72, 24, 169, 153)));
        familyPage.addView(preview, sectionParams());

        formHost.addView(personPage, new LinearLayout.LayoutParams(-1, -2));
        formHost.addView(familyPage, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);
        final int[] currentStep = new int[]{0};
        Button back = activity.actionButton("Отмена", null);
        back.setTextSize(14);
        Button primary = activity.actionButton("Продолжить", null);
        primary.setTextSize(14);
        primary.setTextColor(Color.WHITE);
        primary.setBackground(activity.tealGradientBg(dp(11)));
        actions.addView(back, new LinearLayout.LayoutParams(0, dp(56), 0.82f));
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(0, dp(56), 1.18f);
        primaryParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(primary, primaryParams);
        shell.addView(actions);

        final Runnable[] renderStep = new Runnable[1];
        renderStep[0] = () -> {
            boolean family = currentStep[0] == 1;
            personPage.setVisibility(family ? View.GONE : View.VISIBLE);
            familyPage.setVisibility(family ? View.VISIBLE : View.GONE);
            personStep.setBackground(stepBackground(!family));
            familyStep.setBackground(stepBackground(family));
            personStep.setTextColor(Color.rgb(8, 122, 115));
            familyStep.setTextColor(Color.rgb(8, 122, 115));
            progressLine.setBackgroundColor(family
                ? Color.rgb(24, 169, 153)
                : AppThemePalette.stroke(Color.rgb(217, 224, 229)));
            back.setText(family ? "Назад" : "Отмена");
            primary.setText(family ? "Создать дерево" : "Продолжить");
            primary.setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                family ? R.drawable.ic_menu_check : 0,
                0);
            activity.tintDrawables(primary, Color.WHITE);
            preview.setText(previewText(
                activity.text(selfName),
                activity.text(fatherName),
                activity.text(motherName),
                activity.text(paternalGrandfather),
                activity.text(paternalGrandmother),
                activity.text(maternalGrandfather),
                activity.text(maternalGrandmother),
                activity.text(story)));
            scroll.scrollTo(0, 0);
        };
        back.setOnClickListener(v -> {
            if (currentStep[0] == 0) dialog.dismiss();
            else {
                currentStep[0] = 0;
                renderStep[0].run();
            }
        });
        primary.setOnClickListener(v -> {
            if (currentStep[0] == 0) {
                String name = activity.text(selfName).trim();
                if (name.isEmpty()) {
                    selfName.setError("Введите имя");
                    selfName.requestFocus();
                    return;
                }
                currentStep[0] = 1;
                renderStep[0].run();
                return;
            }
            dialog.dismiss();
            createTree(
                activity.text(selfName),
                activity.text(selfYear),
                activity.text(selfPlace),
                activity.text(fatherName),
                activity.text(motherName),
                activity.text(paternalGrandfather),
                activity.text(paternalGrandmother),
                activity.text(maternalGrandfather),
                activity.text(maternalGrandmother),
                activity.text(story));
        });
        TextWatcher previewWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                fatherParentsCard.setVisibility(activity.text(fatherName).trim().isEmpty() ? View.GONE : View.VISIBLE);
                motherParentsCard.setVisibility(activity.text(motherName).trim().isEmpty() ? View.GONE : View.VISIBLE);
                preview.setText(previewText(
                    activity.text(selfName),
                    activity.text(fatherName),
                    activity.text(motherName),
                    activity.text(paternalGrandfather),
                    activity.text(paternalGrandmother),
                    activity.text(maternalGrandfather),
                    activity.text(maternalGrandmother),
                    activity.text(story)));
            }

            @Override public void afterTextChanged(Editable s) {}
        };
        selfName.addTextChangedListener(previewWatcher);
        fatherName.addTextChangedListener(previewWatcher);
        motherName.addTextChangedListener(previewWatcher);
        paternalGrandfather.addTextChangedListener(previewWatcher);
        paternalGrandmother.addTextChangedListener(previewWatcher);
        maternalGrandfather.addTextChangedListener(previewWatcher);
        maternalGrandmother.addTextChangedListener(previewWatcher);
        story.addTextChangedListener(previewWatcher);
        renderStep[0].run();

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
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
            attrs.dimAmount = 0.28f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    private LinearLayout section(String title, String subtitle) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(14), dp(14), dp(14), dp(12));
        section.setBackground(activity.panelBg(Color.WHITE, dp(14), Color.rgb(217, 224, 229)));
        section.setElevation(dp(1));
        TextView heading = new LocalizedTextView(activity);
        heading.setText(title.toUpperCase(Locale.ROOT));
        heading.setTextColor(Color.rgb(28, 34, 38));
        heading.setTextSize(11);
        heading.setTypeface(activity.uiBold());
        heading.setIncludeFontPadding(false);
        section.addView(heading, new LinearLayout.LayoutParams(-1, dp(23)));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = new LocalizedTextView(activity);
            detail.setText(subtitle);
            detail.setTextColor(Color.rgb(101, 113, 122));
            detail.setTextSize(10);
            detail.setIncludeFontPadding(false);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, dp(24));
            detailParams.setMargins(0, 0, 0, dp(6));
            section.addView(detail, detailParams);
        }
        return section;
    }

    private LinearLayout field(String label, EditText edit, int iconRes) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(caption(label), new LinearLayout.LayoutParams(-1, dp(22)));
        styleField(edit, iconRes);
        block.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        return block;
    }

    private void styleField(EditText edit, int iconRes) {
        edit.setMinHeight(dp(52));
        edit.setTextSize(14);
        edit.setPadding(
            dp(14),
            edit.getMinLines() > 1 ? dp(12) : 0,
            dp(14),
            edit.getMinLines() > 1 ? dp(12) : 0);
        edit.setBackground(activity.panelBg(Color.WHITE, dp(10), Color.rgb(217, 224, 229)));
        if (iconRes != 0) {
            edit.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
            edit.setCompoundDrawablePadding(dp(10));
            activity.tintDrawables(edit, Color.rgb(24, 169, 153));
        }
    }

    private TextView caption(String value) {
        TextView caption = new LocalizedTextView(activity);
        caption.setText(value);
        caption.setTextColor(Color.rgb(76, 83, 88));
        caption.setTextSize(11);
        caption.setTypeface(activity.uiBold());
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setIncludeFontPadding(false);
        return caption;
    }

    private TextView step(String number, String label) {
        TextView step = new LocalizedTextView(activity);
        step.setText(number + "  " + label);
        step.setTextSize(12);
        step.setTypeface(activity.uiBold());
        step.setGravity(Gravity.CENTER);
        step.setIncludeFontPadding(false);
        return step;
    }

    private GradientDrawable stepBackground(boolean active) {
        return active
            ? activity.panelBg(Color.WHITE, dp(999), Color.rgb(24, 169, 153))
            : activity.panelBg(Color.rgb(232, 248, 246), dp(999), Color.TRANSPARENT);
    }

    private View intro(int iconRes, String title, String detail) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(activity.panelBg(Color.rgb(232, 248, 246), dp(14), Color.argb(62, 24, 169, 153)));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(activity.uiColor(Color.rgb(8, 122, 115)));
        card.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        TextView introTitle = title(title);
        introTitle.setTextSize(14);
        copy.addView(introTitle, new LinearLayout.LayoutParams(-1, dp(25)));
        TextView sub = new LocalizedTextView(activity);
        sub.setText(detail);
        sub.setTextColor(Color.rgb(76, 87, 96));
        sub.setTextSize(11);
        sub.setMaxLines(2);
        sub.setIncludeFontPadding(false);
        copy.addView(sub, new LinearLayout.LayoutParams(-1, dp(34)));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return card;
    }

    private TextView title(String value) {
        TextView text = new LocalizedTextView(activity);
        text.setText(value);
        text.setTextColor(Color.rgb(28, 34, 38));
        text.setTextSize(13);
        text.setTypeface(activity.uiBold());
        text.setSingleLine(true);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setIncludeFontPadding(false);
        return text;
    }

    private TextView note(String value) {
        TextView note = new LocalizedTextView(activity);
        note.setText(value);
        note.setTextColor(Color.rgb(76, 87, 96));
        note.setTextSize(11);
        note.setGravity(Gravity.CENTER_VERTICAL);
        note.setPadding(dp(14), dp(12), dp(14), dp(12));
        note.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_file, 0, 0, 0);
        note.setCompoundDrawablePadding(dp(10));
        activity.tintDrawables(note, Color.rgb(105, 100, 184));
        note.setBackground(activity.panelBg(Color.rgb(247, 249, 252), dp(12), Color.rgb(217, 224, 229)));
        return note;
    }

    private String previewText(
        String self,
        String father,
        String mother,
        String paternalGrandfather,
        String paternalGrandmother,
        String maternalGrandfather,
        String maternalGrandmother,
        String story
    ) {
        int cards = 1;
        int links = 0;
        boolean hasFather = !empty(father);
        boolean hasMother = !empty(mother);
        if (hasFather) {
            cards++;
            links++;
            int paternalParents = presentCount(paternalGrandfather, paternalGrandmother);
            cards += paternalParents;
            links += paternalParents;
            if (paternalParents == 2) links++;
        }
        if (hasMother) {
            cards++;
            links++;
            int maternalParents = presentCount(maternalGrandfather, maternalGrandmother);
            cards += maternalParents;
            links += maternalParents;
            if (maternalParents == 2) links++;
        }
        if (hasFather && hasMother) links++;
        boolean english = AppLanguage.isEnglish(activity);
        String name = self == null || self.trim().isEmpty()
            ? (english ? "your card" : "ваша карточка")
            : self.trim();
        return english
            ? "Will create: " + name + " · cards: " + cards + " · connections: " + links
                + (empty(story) ? "" : " · 1 story")
            : "Будет создано: " + name + " · карточек: " + cards + " · связей: " + links
                + (empty(story) ? "" : " · 1 история");
    }

    private static int presentCount(String first, String second) {
        return (empty(first) ? 0 : 1) + (empty(second) ? 0 : 1);
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(14));
        return params;
    }

    private LinearLayout.LayoutParams spacedInputParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(6), 0, 0, 0);
        return params;
    }

    private void createTree(
        String selfName,
        String selfYear,
        String selfPlace,
        String parentOne,
        String parentTwo,
        String paternalGrandfather,
        String paternalGrandmother,
        String maternalGrandfather,
        String maternalGrandmother,
        String story
    ) {
        activity.recordUndo("Создано дерево через быстрый старт");
        activity.state.people.clear();
        activity.state.links.clear();
        activity.state.guides.clear();
        activity.state.history.clear();
        activity.state.photoAlbums.clear();
        activity.state.photoAlbumMedia.clear();
        activity.state.photoAlbumFolders.clear();
        activity.state.familyAlbumMedia.clear();
        activity.state.personAlbumMedia.clear();
        activity.state.familyAlbums.clear();
        activity.state.rootId = "";
        activity.state.selectedId = "";
        activity.branchMode = "all";
        activity.branchAnchorId = "";
        activity.pendingBranchMode = "";
        activity.resetTransientCanvasModes(false);
        Person child = activity.state.addPerson(
            selfName.trim().isEmpty()
                ? activity.tr("Новый человек")
                : selfName.trim(),
            4000,
            3000);
        child.bornYear = selfYear.trim();
        child.place = selfPlace.trim();
        if (!story.trim().isEmpty()) {
            Memory memory = new Memory();
            memory.id = "m_" + UUID.randomUUID().toString().replace("-", "");
            memory.title = "Первая история";
            memory.text = story.trim();
            memory.at = String.valueOf(System.currentTimeMillis());
            child.memories.add(memory);
        }
        Person father = null;
        Person mother = null;
        if (!parentOne.trim().isEmpty()) {
            father = activity.state.addPerson(parentOne.trim(), 3740, 2780);
            setGender(father, PersonGender.MALE);
            activity.state.addRelation("parent", father.id, child.id);
        }
        if (!parentTwo.trim().isEmpty()) {
            mother = activity.state.addPerson(parentTwo.trim(), 4260, 2780);
            setGender(mother, PersonGender.FEMALE);
            activity.state.addRelation("parent", mother.id, child.id);
        }
        if (father != null && mother != null) {
            activity.state.addRelation("partner", father.id, mother.id, "right");
        }
        addParents(
            father,
            paternalGrandfather,
            paternalGrandmother,
            3500,
            3900,
            2560);
        addParents(
            mother,
            maternalGrandfather,
            maternalGrandmother,
            4100,
            4500,
            2560);
        activity.state.rootId = child.id;
        activity.state.selectedId = child.id;
        TreeLayoutEngine.rebuildStepwise(activity.state);
        activity.workspaceWidth = TreeLayoutEngine.normalizeSurfaceWidth(activity.state.workspaceWidth);
        activity.workspaceHeight = TreeLayoutEngine.normalizeSurfaceHeight(activity.state.workspaceHeight);
        activity.recordAction(
            "Создано дерево через быстрый старт",
            activity.state.people.size() + " карточек");
        activity.saveToast("Быстрый старт создан");
        activity.bindState();
        activity.treeView.invalidate();
    }

    private void addParents(Person child, String fatherName, String motherName, float fatherX, float motherX, float y) {
        if (child == null) return;
        Person father = null;
        Person mother = null;
        if (!empty(fatherName)) {
            father = activity.state.addPerson(fatherName.trim(), fatherX, y);
            setGender(father, PersonGender.MALE);
            activity.state.addRelation("parent", father.id, child.id);
        }
        if (!empty(motherName)) {
            mother = activity.state.addPerson(motherName.trim(), motherX, y);
            setGender(mother, PersonGender.FEMALE);
            activity.state.addRelation("parent", mother.id, child.id);
        }
        if (father != null && mother != null) {
            activity.state.addRelation("partner", father.id, mother.id, "right");
        }
    }

    private static void setGender(Person person, String gender) {
        if (person == null) return;
        person.gender = gender;
        person.genderManual = true;
    }

    private int dp(int value) {
        return activity.dp(value);
    }
}
