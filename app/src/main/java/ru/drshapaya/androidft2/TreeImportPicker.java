package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.util.*;

/** Extension filtering is done here: Android providers often label GEDCOM as generic binary. */
final class TreeImportPicker {
    static final int REQUEST_FOLDER=42;
    private final MainActivity activity;
    private final MainActivityFiles files;
    private final List<Uri> trail=new ArrayList<>();
    private Uri tree;
    private Dialog dialog;
    private int generation;
    TreeImportPicker(MainActivity activity,MainActivityFiles files) { this.activity=activity;this.files=files; }
    static boolean supports(String name) {
        return name!=null && name.toLowerCase(Locale.ROOT).matches(".*\\.(ftree|tree|json|ged|gedcom|gdz|zip|xml)$");
    }
    void open() {
        String saved=activity.getPreferences(0).getString("import.folder","");
        if(saved.isEmpty()) { chooseSource();return; }
        tree=Uri.parse(saved);trail.clear();trail.add(DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)));show();
    }
    private void chooseSource() {
        close();
        dialog=AppDialog.create(activity,R.drawable.ic_menu_import,"Импорт дерева")
            .message("Выберите файл дерева или папку. В списке папки показываются только поддерживаемые форматы.")
            .choice(R.drawable.ic_menu_file,"Выбрать файл","Открыть один файл дерева",this::chooseFile)
            .choice(R.drawable.ic_menu_folder,"Выбрать папку","Просмотреть папку с деревьями",this::chooseFolder)
            .action("Отмена",false,null);
        dialog.show();
    }
    private void chooseFile() {
        activity.startActivityForResult(fileIntent(),MainActivity.REQ_IMPORT);
    }
    static Intent fileIntent() {
        // Providers may label GEDCOM as BIN or any other generic MIME type.
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }
    void chooseFolder() {
        close();
        dialog=AppDialog.create(activity,R.drawable.ic_menu_folder,"Папка для импорта")
            .message("Выберите папку с деревом. В ней будут показаны только .ftree, .tree, .json, .ged, .gedcom, .gdz, .zip и XML Family Tree DS.")
            .action("Отмена",false,null).action("Выбрать папку",true,()-> {
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                activity.startActivityForResult(intent,REQUEST_FOLDER);
            });
        dialog.show();
    }
    void selected(Intent data) {
        tree=data.getData();if(tree==null)return;
        try { if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
            activity.getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch(SecurityException ignored) { }
        activity.getPreferences(0).edit().putString("import.folder",tree.toString()).apply();
        trail.clear();trail.add(DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)));show();
    }
    void close() { generation++;if(dialog!=null)dialog.dismiss(); }
    private void show() {
        close(); final int request=generation;final Uri folder=trail.get(trail.size()-1);
        dialog=AppDialog.create(activity,R.drawable.ic_menu_folder,"Импорт дерева").progress("Чтение папки…")
            .action("Отмена",false,this::close);
        dialog.setOnCancelListener(d->close());dialog.show();
        new Thread(()-> {
            List<Entry> entries=new ArrayList<>();String error=null;
            try(Cursor cursor=activity.getContentResolver().query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(folder)),
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)) {
                if(cursor==null)throw new java.io.IOException("Папка недоступна");
                while(cursor.moveToNext()) {
                    String name=cursor.getString(1);boolean dir=DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2));
                    if(dir||supports(name))entries.add(new Entry(name,DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)),dir));
                    if(entries.size()>10000)throw new java.io.IOException("Слишком много файлов. Выберите папку поменьше.");
                }
                entries.sort((a,b)->a.dir!=b.dir?(a.dir?-1:1):a.name.compareToIgnoreCase(b.name));
            }catch(Exception e){error="Не удалось прочитать папку. Выберите её заново.";}
            final String failure=error;
            final String folderName=folderName(folder);
            activity.runOnUiThread(()-> {
                if(request!=generation||activity.isFinishing()||activity.isDestroyed())return;
                dialog.dismiss();
                AppDialog browser=AppDialog.create(activity,R.drawable.ic_menu_folder,"Импорт · поддерживаемые файлы")
                    .action(trail.size()>1?"Назад":"Закрыть",false,()-> {
                        if(trail.size()>1){trail.remove(trail.size()-1);show();}else close();
                    }).action("Другая папка",true,this::chooseFolder);
                if(failure!=null || entries.isEmpty()) browser.message(failure!=null?failure:"В этой папке нет поддерживаемых файлов дерева. Выберите другую папку.");
                else {
                    browser.message(folderName);
                    List<AppDialog.Item> rows=new ArrayList<>();
                    for(Entry entry:entries) rows.add(new AppDialog.Item(entry.dir ? R.drawable.ic_menu_folder : R.drawable.ic_menu_file,
                        entry.name,entry.dir ? "Папка" : entry.name.substring(entry.name.lastIndexOf('.')+1).toUpperCase(Locale.ROOT)));
                    browser.items(rows,w -> { Entry e=entries.get(w);if(e.dir){trail.add(e.uri);show();}else files.importFromUri(e.uri); });
                }
                dialog=browser;dialog.setOnCancelListener(d->close());dialog.show();
            });
        },"tree-import-files").start();
    }
    private String folderName(Uri folder) {
        try(Cursor cursor=activity.getContentResolver().query(folder,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)) {
            if(cursor!=null && cursor.moveToFirst()) return cursor.getString(0);
        }catch(Exception ignored){ }
        return AppLanguage.text(activity,"Выбранная папка");
    }
    private static final class Entry { final String name;final Uri uri;final boolean dir;Entry(String n,Uri u,boolean d){name=n;uri=u;dir=d;} }
}
