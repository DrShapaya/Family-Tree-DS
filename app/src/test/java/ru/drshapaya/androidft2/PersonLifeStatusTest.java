package ru.drshapaya.androidft2;

import static org.junit.Assert.*;
import java.io.StringWriter;
import org.junit.Test;

public class PersonLifeStatusTest {
    @Test public void recentBirthWithoutDeathIsLivingAndDeathDateTakesPriority() {
        Person p = new Person("p");
        p.bornYear = "1980";
        assertEquals(PersonLifeStatus.LIVING, PersonLifeStatus.resolve(p));
        p.lifeStatus = PersonLifeStatus.LIVING;
        assertEquals(PersonLifeStatus.LIVING, PersonLifeStatus.resolve(p));
        p.diedMonth = "3";
        assertEquals(PersonLifeStatus.DECEASED, PersonLifeStatus.resolve(p));
        p.diedMonth = "";
        p.died = "  ";
        assertEquals(PersonLifeStatus.LIVING, PersonLifeStatus.resolve(p));
        p.lifeStatus = PersonLifeStatus.DECEASED;
        assertEquals(PersonLifeStatus.DECEASED, PersonLifeStatus.resolve(p));
        assertEquals(PersonLifeStatus.UNKNOWN, PersonLifeStatus.normalize("invalid"));
    }

    @Test public void automaticStatusUsesAgeAndActualBirthdayBoundaries() {
        java.util.Calendar now=new java.util.GregorianCalendar(2026,9,10);
        Person p=new Person("age");
        assertEquals(PersonLifeStatus.UNKNOWN,PersonLifeStatus.resolve(p,now));
        p.bornYear="1926";p.bornMonth="10";p.bornDay="10";
        assertEquals(PersonLifeStatus.LIVING,PersonLifeStatus.resolve(p,now));
        p.bornYear="1925";p.bornDay="11";
        assertEquals(PersonLifeStatus.LIVING,PersonLifeStatus.resolve(p,now));
        p.bornDay="10";assertEquals(PersonLifeStatus.UNKNOWN,PersonLifeStatus.resolve(p,now));
        p.bornYear="1906";assertEquals(PersonLifeStatus.UNKNOWN,PersonLifeStatus.resolve(p,now));
        p.bornYear="1905";p.bornDay="11";assertEquals(PersonLifeStatus.UNKNOWN,PersonLifeStatus.resolve(p,now));
        p.bornDay="10";assertEquals(PersonLifeStatus.DECEASED,PersonLifeStatus.resolve(p,now));
        p.bornYear="2030";assertEquals(PersonLifeStatus.UNKNOWN,PersonLifeStatus.resolve(p,now));
        p.bornYear="";p.bornMonth="";p.bornDay="";p.born="10.10.1905";
        assertEquals(PersonLifeStatus.DECEASED,PersonLifeStatus.resolve(p,now));
        p.born="2020-12-01";assertEquals(PersonLifeStatus.LIVING,PersonLifeStatus.resolve(p,now));
    }

    @Test public void manualChoicesRemainAuthoritativeAndGuessedDeathIsNotRecordedInGedcom() {
        Person p=new Person("p");p.bornYear="1800";p.lifeStatus=PersonLifeStatus.LIVING;
        assertEquals(PersonLifeStatus.LIVING,PersonLifeStatus.resolve(p));
        p.lifeStatus=PersonLifeStatus.UNKNOWN;
        assertEquals(PersonLifeStatus.DECEASED,PersonLifeStatus.resolve(p));
        TreeState tree=new TreeState();tree.people.put(p.id,p);
        assertFalse(GedcomWriter.write(tree).contains("1 DEAT Y"));
        p.lifeStatus=PersonLifeStatus.DECEASED;
        assertTrue(GedcomWriter.write(tree).contains("1 DEAT Y"));
    }

    @Test public void statusAndVisibilitySurviveUndoRedoAndCopy() {
        TreeState before = new TreeState();
        before.people.put("p", new Person("p"));
        TreeState after = TreeStateCopier.copy(before);
        after.people.get("p").lifeStatus = PersonLifeStatus.LIVING;
        after.showLifeStatusBadge = true;
        TreeDeltaCommand command = TreeDeltaCommand.between(before, after, "Статус");
        assertFalse(command.isEmpty());
        command.undo(after);
        assertFalse(after.showLifeStatusBadge);
        assertEquals(PersonLifeStatus.UNKNOWN, after.people.get("p").lifeStatus);
        command.redo(after);
        assertTrue(after.showLifeStatusBadge);
        assertEquals(PersonLifeStatus.LIVING, after.people.get("p").lifeStatus);
    }

    @Test public void xmlAndGedcomPreserveManualStatusWithoutDeathDate() throws Exception {
        TreeState state = new TreeState();
        state.showLifeStatusBadge = true;
        for (String status : new String[]{PersonLifeStatus.LIVING, PersonLifeStatus.DECEASED, PersonLifeStatus.UNKNOWN}) {
            Person p = new Person(status);
            p.name = status;
            p.lifeStatus = status;
            state.people.put(p.id, p);
        }
        state.rootId = PersonLifeStatus.LIVING;
        StringWriter out = new StringWriter();
        TreeReadableExport.xml(state, out, p -> "");
        TreeState xml = TreeXmlReader.read(out.toString());
        assertTrue(xml.showLifeStatusBadge);
        for (Person p : state.people.values()) assertEquals(p.lifeStatus, xml.people.get(p.id).lifeStatus);
        String gedcom = GedcomWriter.write(state);
        assertTrue(gedcom.contains("1 DEAT Y"));
        TreeState imported = GedcomReader.read(gedcom).state;
        for (Person p : imported.people.values()) assertEquals(p.name, p.lifeStatus);
        assertEquals(3, imported.people.size());
    }
}
