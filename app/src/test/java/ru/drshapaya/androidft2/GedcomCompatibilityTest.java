package ru.drshapaya.androidft2;

import static org.junit.Assert.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.Test;

public class GedcomCompatibilityTest {
    @Test public void gedcom7UnescapesOnlyLeadingAtAndGedcom5RoundTripPreservesLiteralPointers() {
        GedcomReader.Result r=GedcomReader.read("0 HEAD\n1 GEDC\n2 VERS 7.0\n0 @I@ INDI\n1 NAME Test\n"
            +"1 NOTE me@example.com\n2 CONT @@me and @I\n2 CONT @@@@@\n0 TRLR\n");
        Person p=r.state.people.values().iterator().next();assertEquals("me@example.com\n@me and @I\n@@@@",p.notes);
        p.notes="@N@\n"+String.join("",java.util.Collections.nCopies(179,"A"))+"@handle me@example.com\n@@@@";
        GedcomReader.Result restored=GedcomReader.read(GedcomWriter.write(r.state));
        assertEquals(p.notes,restored.state.people.values().iterator().next().notes);assertEquals(0,restored.unresolvedReferences);
    }
    @Test public void gedcom7KeepsAnonymousPeopleSharedNotesAndVoidPointers() {
        GedcomReader.Result r=GedcomReader.read("0 HEAD\n1 GEDC\n2 VERS 7.0\n0 INDI\n1 NAME Anonymous\n1 SNOTE @N@\n1 SNOTE @VOID@\n1 FAMC @VOID@\n"
            +"0 @P@ INDI\n1 NAME Parent\n0 @F@ FAM\n1 HUSB @P@\n1 CHIL @VOID@\n0 @N@ SNOTE Shared\n1 CONT Second line\n0 TRLR\n");
        assertEquals(2,r.state.people.size());assertEquals(0,r.unresolvedReferences);assertEquals(0,r.state.links.size());
        assertEquals("Shared\nSecond line",r.state.people.values().iterator().next().notes);
    }
    @Test(expected=IllegalArgumentException.class) public void gedcom5StillRejectsMissingRecordIdentifiers() {
        GedcomReader.read("0 HEAD\n1 GEDC\n2 VERS 5.5.1\n0 INDI\n1 NAME Lost ID\n0 TRLR\n");
    }
    @Test public void gedzipResolvesUriPathsWithoutTreatingPlusAsSpaceOrAllowingTraversal() {
        java.util.Set<String> paths=new LinkedHashSet<>(Arrays.asList("media/CharlotteBrontë.jpg","media/A+B.jpg"));
        assertEquals("media/CharlotteBrontë.jpg",TreeImportIO.findPhoto(paths,"gedcom.ged","media/CharlotteBront%C3%AB.jpg"));
        assertEquals("media/A+B.jpg",TreeImportIO.findPhoto(paths,"gedcom.ged","media/A+B.jpg"));
        assertEquals("",TreeImportIO.findPhoto(paths,"gedcom.ged","%2E%2E/media/A+B.jpg"));
        assertEquals("",TreeImportIO.findPhoto(paths,"gedcom.ged","media/%ZZ.jpg"));
    }
    @Test public void recoversLiteralNewlinesInNotesWithoutLosingPeopleOrRelationships() {
        GedcomReader.Result r=GedcomReader.read("0 HEAD\n0 @A@ INDI\n1 NAME Anna /Test/\n"
            +"1 NOTE Первая строка\nПродолжение без CONT\n2 CONT Следующий абзац\nЕщё одна строка\n"
            +"0 @B@ INDI\n1 NAME Boris /Test/\n0 @F@ FAM\n1 WIFE @A@\n1 CHIL @B@\n0 TRLR\n");
        assertEquals(2,r.state.people.size());assertEquals(1,r.state.links.size());assertEquals(2,r.recoveredTextLines);
        assertEquals("Первая строка\nПродолжение без CONT\nСледующий абзац\nЕщё одна строка",r.state.people.values().iterator().next().notes);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsBrokenRecordsInsteadOfFoldingThemIntoNotes() {
        GedcomReader.read("0 HEAD\n0 @A@ INDI\n1 NOTE Biography\n0 @B@\n0 TRLR\n");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnidentifiedTextOutsideNotes() {
        GedcomReader.read("0 HEAD\n0 @A@ INDI\n1 NAME Anna\nDamaged record\n0 TRLR\n");
    }
    @Test public void supportsLowercaseTagsFromNonstandardExporters() {
        assertEquals("Test Anna",GedcomReader.read("0 head\n0 @A@ indi\n1 name Anna /Test/\n0 trlr").state.people.values().iterator().next().name);
    }
    @Test public void preservesUnicodeLineAndParagraphSeparatorsInsideValidGedcomValues() {
        String note="Первый абзац\u2028Вторая строка\u2029Третий абзац\u0085Последняя строка";
        GedcomReader.Result r=GedcomReader.read("0 HEAD\n0 @A@ INDI\n1 NAME Anna\n1 NOTE "+note+"\n0 TRLR");
        assertEquals(note,r.state.people.values().iterator().next().notes);assertEquals(0,r.recoveredTextLines);
    }
    @Test public void importsGeneryPhotoReferenceAndWholeBiography() {
        GedcomReader.Result r = GedcomReader.read("0 HEAD\n1 CHAR UTF-8\n0 @I1@ INDI\n"
            + "1 NAME Иван /Тестов/\n1 BIRT\n2 DATE 16 APR 1950\n"
            + "1 OBJE\n2 FILE drevo.ged.files\\портрет.jpg\n3 FORM jpg\n2 _PRIM Y\n"
            + "1 NOTE Ро\n2 CONC дился в городе. \n2 CONC Учился.\n2 CONT\n2 CONT Семья.\n0 TRLR\n");
        Person p = r.state.people.values().iterator().next();
        assertEquals("Тестов Иван", p.name);
        assertEquals("Родился в городе. Учился.\n\nСемья.", p.notes);
        assertEquals("1950", p.bornYear);
        assertEquals("drevo.ged.files\\портрет.jpg", r.photos.get(p.id).get(0));
    }

    @Test public void resolvesSharedMediaAndNotesDeclaredAfterPersonAndHonorsPrimaryPhoto() {
        GedcomReader.Result r = GedcomReader.read("0 HEAD\n0 @I1@ INDI\n1 NAME Test\n"
            + "1 OBJE\n2 FILE other.png\n1 OBJE @M1@\n2 _PRIM Y\n1 NOTE @N1@\n"
            + "0 @M1@ OBJE\n1 FILE portrait.jpg\n2 FORM jpg\n0 @N1@ NOTE First\n1 CONT Second\n0 TRLR\n");
        Person p = r.state.people.values().iterator().next();
        assertEquals(Arrays.asList("portrait.jpg", "other.png"), r.photos.get(p.id));
        assertEquals("First\nSecond", p.notes);
        assertEquals(0, r.unresolvedReferences);
    }

    @Test public void reconstructsReciprocalFamilyLinksInEitherRecordOrderWithoutDuplicates() {
        String persons="0 @P@ INDI\n1 NAME Parent\n1 FAMS @F1@\n0 @C@ INDI\n1 NAME Child\n1 FAMC @F1@\n";
        String fam="0 @F1@ FAM\n1 HUSB @P@\n"; // Exporter omitted CHIL but INDI has FAMC.
        for(String body : Arrays.asList(persons+fam,fam+persons)) {
            GedcomReader.Result r=GedcomReader.read("0 HEAD\n"+body+"0 TRLR\n");
            assertEquals(1,r.state.links.size());
            assertEquals("parent",r.state.links.get(0).type);
            assertEquals(0,r.unresolvedReferences);
        }
    }

    @Test public void keepsSiblingsConnectedWhenTheirParentsAreUnknown() {
        GedcomReader.Result r=GedcomReader.read("0 HEAD\n0 @A@ INDI\n1 NAME A\n0 @B@ INDI\n1 NAME B\n"
            +"0 @F@ FAM\n1 CHIL @A@\n1 CHIL @B@\n0 TRLR\n");
        assertEquals(1,r.state.links.size()); assertEquals("sibling",r.state.links.get(0).type);
    }

    @Test public void reportsUnresolvedRecordsAndUnsupportedEmbeddedImages() {
        GedcomReader.Result r=GedcomReader.read("0 HEAD\n0 @A@ INDI\n1 NAME A\n1 FAMC @missing@\n"
            +"1 NOTE @absent@\n1 OBJE\n2 BLOB binary\n0 TRLR\n");
        assertEquals(2,r.unresolvedReferences);assertEquals(1,r.embeddedPhotos);
    }

    @Test public void archiveLookupRespectsGedcomDirectoryWindowsSeparatorsAndUnicode() {
        java.util.Set<String> paths=new LinkedHashSet<>(Arrays.asList("export/drevo.ged.files/Портрет.JPG","other/photo.jpg"));
        assertEquals("export/drevo.ged.files/Портрет.JPG",TreeImportIO.findPhoto(paths,"export/drevo.ged","drevo.ged.files\\портрет.jpg"));
        assertEquals("",TreeImportIO.findPhoto(paths,"export/drevo.ged","../other/photo.jpg"));
        assertEquals("",TreeImportIO.safePath("C:\\users\\secret.jpg"));
        assertEquals("",TreeImportIO.safePath("/secret.jpg"));
        paths.add("export/drevo.ged.files/ПОРТРЕТ.jpg");
        assertEquals("",TreeImportIO.findPhoto(paths,"export/drevo.ged","drevo.ged.files/портрет.jpg"));
    }

    @Test public void decodesUtf8Utf16AndExplicitWindows1251() throws Exception {
        String text="0 HEAD\n1 CHAR UTF-8\n0 @I@ INDI\n1 NAME Иван /Тестов/\n0 TRLR\n";
        assertEquals(text,TreeImportIO.decodeText(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(text,TreeImportIO.decodeText(text.getBytes(StandardCharsets.UTF_16)));
        text=text.replace("UTF-8","WINDOWS-1251");
        assertEquals(text,TreeImportIO.decodeText(text.getBytes(Charset.forName("windows-1251"))));
    }

    @Test(expected=java.io.IOException.class) public void refusesUnknownEncodingInsteadOfCorruptingNames() throws Exception {
        TreeImportIO.decodeText("0 HEAD\n1 CHAR ANSEL\n1 NOTE Иван".getBytes(Charset.forName("windows-1251")));
    }
    @Test public void decodesUnicodeWithoutBomInBothByteOrders() throws Exception {
        String text="0 HEAD\n1 CHAR UNICODE\n0 @I@ INDI\n1 NAME Zoë /Иванова/\n0 TRLR\n";
        assertEquals(text,TreeImportIO.decodeText(text.getBytes(StandardCharsets.UTF_16LE)));
        assertEquals(text,TreeImportIO.decodeText(text.getBytes(StandardCharsets.UTF_16BE)));
    }
    @Test public void decodesExplicitCp1252AndLatin1WithoutDamagingAccents() throws Exception {
        String text="0 HEAD\n1 CHAR cp1252\n0 @I@ INDI\n1 NAME François /Müller/\n1 NOTE € — œ\n0 TRLR\n";
        assertEquals(text,TreeImportIO.decodeText(text.getBytes(Charset.forName("windows-1252"))));
        text="0 HEAD\n1 CHAR ISO-8859-1\n0 @I@ INDI\n1 NAME François /Müller/\n0 TRLR\n";
        assertEquals(text,TreeImportIO.decodeText(text.getBytes(StandardCharsets.ISO_8859_1)));
    }
    @Test public void ansiHonorsExplicitCodePageAndExporterInsteadOfUsingRussianForAllFiles() throws Exception {
        String text="0 HEAD\n1 SOUR FTM\n1 CHAR ANSI\n0 @I@ INDI\n1 NAME François /Müller/\n0 TRLR\n";
        assertEquals(text,TreeImportIO.decodeText(text.getBytes(Charset.forName("windows-1252"))));
        text="0 HEAD\n1 CHAR ANSI\n2 VERS 1251\n0 @I@ INDI\n1 NAME Иван /Тестов/\n0 TRLR\n";
        assertEquals(text,TreeImportIO.decodeText(text.getBytes(Charset.forName("windows-1251"))));
    }
    @Test(expected=java.io.IOException.class) public void refusesTruncatedUtf16InsteadOfReplacingCharacter() throws Exception {
        TreeImportIO.decodeText(new byte[]{(byte)0xff,(byte)0xfe,'0',0,' '});
    }
}
