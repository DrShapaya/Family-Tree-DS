package ru.drshapaya.androidft2;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.ContextWrapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TreeImportArchiveTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private TreeStore store() {
        Context context=new ContextWrapper(null) {
            @Override public File getFilesDir(){return folder.getRoot();}
            @Override public Context getApplicationContext(){return this;}
        };
        return new TreeStore(context);
    }
    private byte[] zip(String... pairs) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(bytes)) {
            for(int i=0;i<pairs.length;i+=2){z.putNextEntry(new ZipEntry(pairs[i]));z.write(pairs[i+1].getBytes(StandardCharsets.UTF_8));z.closeEntry();}
        }
        return bytes.toByteArray();
    }
    private String tree(){return "0 HEAD\n1 CHAR UTF-8\n0 @I1@ INDI\n1 NAME Тест\n1 OBJE\n2 FILE tree.ged.files\\фото.jpg\n2 _PRIM Y\n0 TRLR\n";}

    @Test public void reloadKeepsBiographyWhitespaceAndLegacyFamilySex() throws Exception {
        TreeStore store=store();String note="\n  First line\n\nLast line  \n";
        TreeState state=GedcomReader.read("0 HEAD\n1 GEDC\n2 VERS 5.5.1\n0 @I@ INDI\n1 NAME Unknown\n"
            +"0 @F@ FAM\n1 WIFE @I@\n0 TRLR\n").state;
        Person person=state.people.values().iterator().next();person.notes=note;
        TreeState restored=store.parse(store.exportText(state));Person saved=restored.people.get(person.id);
        assertEquals(note,saved.notes);assertEquals(PersonGender.FEMALE,saved.gender);
    }

    @Test public void importsArchiveInAnyEntryOrderAndPersistsPhotoBytes() throws Exception {
        TreeStore store=store();
        byte[] zip=zip("bundle/tree.ged.files/фото.jpg","image bytes","bundle/tree.ged",tree());
        TreeImportIO.Result result=TreeImportIO.readZip(new ByteArrayInputStream(zip),store);
        assertEquals(1,result.state.people.size());assertEquals(0,result.missingPhotos);
        Person p=result.state.people.values().iterator().next();assertTrue(store.mediaStore().exists(p.photoMediaId));
        try(java.io.InputStream in=store.mediaStore().open(p.photoMediaId)){assertEquals("image bytes",new String(TreeImportIO.readBytes(in,100),StandardCharsets.UTF_8));}
    }

    @Test public void reportsMissingPortraitWithoutDiscardingPeople() throws Exception {
        TreeImportIO.Result result=TreeImportIO.readZip(new ByteArrayInputStream(zip("tree.ged",tree())),store());
        assertEquals(1,result.state.people.size());assertEquals(1,result.missingPhotos);
        assertTrue(result.notice(false).contains("ZIP"));
    }

    @Test public void looseGedcomWarnsThatItsPhotoFolderMustBeIncluded() throws Exception {
        TreeImportIO.Result result=TreeImportIO.readText(new ByteArrayInputStream(tree().getBytes(StandardCharsets.UTF_8)),store());
        assertEquals(1,result.missingPhotos);
    }

    @Test(expected=java.io.IOException.class) public void refusesAmbiguousTrees() throws Exception {
        TreeImportIO.readZip(new ByteArrayInputStream(zip("a.ged",tree(),"b.ged",tree())),store());
    }
    @Test(expected=java.io.IOException.class) public void rejectsArchiveTraversal() throws Exception {
        TreeImportIO.readZip(new ByteArrayInputStream(zip("tree.ged",tree(),"../bad.jpg","x")),store());
    }

    @Test public void acceptsIdenticalNormalizedTreeAndPhotoEntries() throws Exception {
        TreeImportIO.Result result=TreeImportIO.readZip(new ByteArrayInputStream(zip(
            "tree.ged",tree(),"./tree.ged",tree(),
            "tree.ged.files/фото.jpg","image","tree.ged.files\\фото.jpg","image")),store());
        assertEquals(1,result.state.people.size());assertEquals(0,result.missingPhotos);assertEquals(0,result.ambiguousPhotos);
    }
    @Test public void conflictingPhotoPathsDoNotPreventTreeImportOrAssignWrongPortrait() throws Exception {
        TreeImportIO.Result result=TreeImportIO.readZip(new ByteArrayInputStream(zip(
            "tree.ged",tree(),"tree.ged.files/фото.jpg","first","tree.ged.files\\фото.jpg","other")),store());
        assertEquals(1,result.state.people.size());assertEquals(1,result.missingPhotos);assertEquals(1,result.ambiguousPhotos);
        assertTrue(result.state.people.values().iterator().next().photoMediaId.isEmpty());
        assertTrue(result.notice(false).contains("одинаковым путём"));
    }
    @Test(expected=java.io.IOException.class) public void conflictingTreePayloadsRemainAmbiguous() throws Exception {
        TreeImportIO.readZip(new ByteArrayInputStream(zip("tree.ged",tree(),"./tree.ged",tree().replace("Тест","Другой"))),store());
    }
    @Test public void unrelatedLargeArchiveEntryDoesNotConsumeExpandedImportBudget() throws Exception {
        byte[] data=zip("tree.ged",tree(),"unrelated.bin","unused");
        for(int i=0;i<data.length-46;i++) {
            if(data[i]==0x50 && data[i+1]==0x4b && data[i+2]==1 && data[i+3]==2) {
                int length=(data[i+28]&255)|((data[i+29]&255)<<8);
                String name=new String(data,i+46,length,StandardCharsets.UTF_8);
                if("unrelated.bin".equals(name)) java.nio.ByteBuffer.wrap(data,i+24,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(1024*1024*1024+1);
            }
        }
        assertEquals(1,TreeImportIO.readZip(new ByteArrayInputStream(data),store()).state.people.size());
    }
    @Test public void looseGedcomReportsRecoveredText() throws Exception {
        String text="0 HEAD\n0 @A@ INDI\n1 NAME Anna\n1 NOTE First\nSecond\n0 TRLR";
        TreeImportIO.Result r=TreeImportIO.readText(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),store());
        assertEquals(1,r.recoveredTextLines);assertTrue(r.notice(false).contains("Восстановлены"));
    }
}
