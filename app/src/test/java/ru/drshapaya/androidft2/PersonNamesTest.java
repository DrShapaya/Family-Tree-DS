package ru.drshapaya.androidft2;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.StringWriter;

public class PersonNamesTest {
    private Person person() {
        Person p = new Person("a");
        PersonNames.set(p,"Иванова","Анна","Петровна");
        p.maidenName="Соколова"; p.maidenNameEnabled=true;
        return p;
    }
    private TreeState tree(Person p) {
        TreeState state = new TreeState(); state.people.put(p.id,p); state.rootId=p.id; state.selectedId=p.id; p.x=0; p.y=0; return state;
    }
    @Test public void inheritanceAndOverridesChangeOnlyVisibleSurname() {
        Person p=person();
        assertEquals("Иванова Анна Петровна",PersonNames.display(p,false));
        assertEquals("Соколова Анна Петровна",PersonNames.display(p,true));
        p.surnameDisplay="current";
        assertEquals("Иванова Анна Петровна",PersonNames.display(p,true));
        p.surnameDisplay="maiden";
        assertEquals("Соколова Анна Петровна",PersonNames.display(p,false));
        assertEquals("Иванова Анна Петровна",p.name); assertEquals("Иванова",p.surname);
    }
    @Test public void hiddenOrMissingMaidenSurnameFallsBackWithoutLosingIt() {
        Person p=person(); p.maidenNameEnabled=false; p.surnameDisplay="maiden";
        assertEquals(p.name,PersonNames.display(p,true)); assertEquals("Соколова",p.maidenName);
        p.maidenNameEnabled=true; p.maidenName="  "; assertEquals(p.name,PersonNames.display(p,true));
    }
    @Test public void legacySurnameFirstNamesKeepEveryWordAndSingleNamesRemainUsable() {
        Person p=new Person("a");p.name="Иванова Анна Мария Петровна";
        String[] parts=PersonNames.parts(p); assertEquals(p.name,PersonNames.join(parts[0],parts[1],parts[2]));
        p.name="Анна";parts=PersonNames.parts(p); assertEquals("",parts[0]);assertEquals("Анна",parts[1]);
    }
    @Test public void undoDetectsMaidenChangesEvenWhenFullNameDoesNotChange() {
        TreeState before=tree(person()), after=TreeStateCopier.copy(before);
        after.people.get("a").maidenName="Орлова";after.people.get("a").surnameDisplay="current";
        after.useMaidenNames=true; after.showQualityBadge=false;
        TreeDeltaCommand command=TreeDeltaCommand.between(before,after,"Фамилия");
        TreeState target=TreeStateCopier.copy(before); command.redo(target);
        assertEquals("Орлова",target.people.get("a").maidenName);assertTrue(target.useMaidenNames);assertFalse(target.showQualityBadge);
        command.undo(target); assertTrue(PersonNames.equal(before.people.get("a"),target.people.get("a")));
        assertFalse(target.useMaidenNames);assertTrue(target.showQualityBadge);
    }
    @Test public void xmlRetainsStructuredNamesHiddenMaidenAndDisplaySettings() throws Exception {
        Person p=person();p.maidenNameEnabled=false;p.surnameDisplay="maiden";
        TreeState state=tree(p);state.useMaidenNames=true;state.showQualityBadge=false;
        StringWriter out=new StringWriter();TreeReadableExport.xml(state,out,q->"");
        TreeState restored=TreeXmlReader.read(out.toString());
        assertTrue(PersonNames.equal(p,restored.people.get("a")));assertTrue(restored.useMaidenNames);assertFalse(restored.showQualityBadge);
    }
    @Test public void gedcomRetainsGivenPatronymicAndBothSurnames() {
        Person p=person();p.surnameDisplay="maiden";p.maidenNameEnabled=false;
        String gedcom=GedcomWriter.write(tree(p));
        assertTrue(gedcom.contains("1 NAME Анна Петровна /Иванова/"));
        assertTrue(gedcom.contains("2 SURN Соколова"));
        Person restored=GedcomReader.read(gedcom).state.people.values().iterator().next();
        assertEquals(p.name,restored.name);assertTrue(PersonNames.equal(p,restored));
    }
    @Test public void importsExplicitBirthSurnameAndPreservesPrimarySpelling() {
        Person p=GedcomReader.read("0 HEAD\n0 @I1@ INDI\n1 NAME Henri IV /CAPET/\n2 GIVN Henri, IV\n2 SURN CAPET\n1 NAME Henri IV /BIRTH/\n2 TYPE birth\n0 TRLR\n").state.people.values().iterator().next();
        assertEquals("CAPET Henri IV",p.name);assertEquals("BIRTH",p.maidenName);assertTrue(p.maidenNameEnabled);
    }
    @Test public void searchFindsEitherSurnameRegardlessOfDisplayMode() {
        Person p=person();TreeState state=tree(p);state.useMaidenNames=true;
        assertSame(p,SmartPeopleSearch.findPerson(state,"Иванова"));assertSame(p,SmartPeopleSearch.findPerson(state,"Соколова"));
        p.maidenNameEnabled=false;p.surnameDisplay="current";
        assertSame(p,SmartPeopleSearch.findPerson(state,"Соколова Анна Петровна"));
    }

    @Test public void legacyMaidenBesideSurnameIsMigratedWithoutLosingNameWords() {
        for(String name:new String[]{"Иванова (Соколова) Анна Петровна","Иванова(Соколова) Анна Петровна"}) {
            Person p=new Person("old");p.name=name;
            assertTrue(PersonNames.migrateLegacy(p));
            assertEquals("Иванова Анна Петровна",p.name);assertEquals("Анна",p.givenName);
            assertEquals("Соколова",p.maidenName);assertTrue(p.maidenNameEnabled);
            assertFalse(PersonNames.migrateLegacy(p));
            assertEquals("Соколова Анна Петровна",PersonNames.alternate(p));
        }
    }

    @Test public void repairsPreviouslyMisparsedMaidenAndLeavesExplicitMaidenUntouched() {
        Person p=new Person("old");PersonNames.set(p,"Иванова","(Соколова)","Анна Петровна");
        p.maidenName="Орлова";p.maidenNameEnabled=false;
        assertTrue(PersonNames.migrateLegacy(p));assertEquals("Анна",p.givenName);assertEquals("Петровна",p.patronymic);
        assertEquals("Орлова",p.maidenName);assertFalse(p.maidenNameEnabled);
        p.name="Иванова (копия)";assertFalse(PersonNames.migrateLegacy(p));
    }
}
