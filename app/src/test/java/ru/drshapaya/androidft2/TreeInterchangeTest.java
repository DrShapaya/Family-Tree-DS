package ru.drshapaya.androidft2;

import static org.junit.Assert.*;
import java.io.StringWriter;
import java.util.*;
import org.junit.Test;

public class TreeInterchangeTest {
    private TreeState tree() {
        TreeState state=new TreeState();
        for(String id:Arrays.asList("a","b","c","d","e","f","g","h")) {
            Person p=new Person(id);p.name=id;p.gender=PersonGender.UNKNOWN;p.x=100+state.people.size()*320;p.y=400;
            state.people.put(id,p);
        }
        state.rootId="a";
        state.addRelation("partner","a","b"); // childless couple
        state.addRelation("partner","c","d");
        state.addRelation("parent","c","e");state.addRelation("parent","d","e");
        state.addRelation("parent","f","e"); // third parent must survive
        state.addRelation("parent","a","g"); // single parent; no invented second parent
        state.addRelation("sibling","g","h");
        state.links.add(new Relation("generic","family","b","h"));
        state.people.get("a").notes="Первая строка\n\n"+String.join("",Collections.nCopies(500,"Длинная история 😀 "))+"\nПоследняя строка";
        state.people.get("a").place="Томск";
        return state;
    }
    private Set<String> edges(TreeState state) {
        Set<String> result=new TreeSet<>();
        for(Relation r:state.links){String a=state.people.get(r.from).name,b=state.people.get(r.to).name;
            if(!r.type.equals("parent") && a.compareTo(b)>0){String tmp=a;a=b;b=tmp;}
            result.add(r.type+":"+a+":"+b);
        }return result;
    }
    @Test public void gedcomRoundTripPreservesEveryExplicitFamilyEdge() {
        TreeState original=tree();String ged=GedcomWriter.write(original);
        GedcomReader.Result imported=GedcomReader.read(ged);
        assertEquals(0,imported.unresolvedReferences);
        assertEquals(edges(original),edges(imported.state));
        Person a=imported.state.people.values().stream().filter(p->p.name.equals("a")).findFirst().get();
        assertEquals(original.people.get("a").notes,a.notes);assertEquals("Томск",a.place);
        for(Person p:imported.state.people.values())assertEquals(PersonGender.UNKNOWN,p.gender);
    }
    @Test public void importRepairsWhitespaceInFamilyPointersAndKeepsRepeatedUnions() {
        String ged="0 HEAD\n0 @C@ INDI\n1 NAME C\n1 FAMC   @F1@  \n1 FAMC @F2@\n"
            +"0 @A@ INDI\n1 NAME A\n1 FAMS @F1@ \n0 @B@ INDI\n1 NAME B\n0 @D@ INDI\n1 NAME D\n"
            +"0 @F2@ FAM\n1 WIFE @D@\n1 CHIL @C@\n0 @F1@ FAM\n1 HUSB @A@\n1 WIFE  @B@  \n1 CHIL @C@\n0 TRLR\n";
        GedcomReader.Result r=GedcomReader.read(ged);assertEquals(0,r.unresolvedReferences);
        assertEquals(new TreeSet<>(Arrays.asList("parent:A:C","parent:B:C","parent:D:C","partner:A:B")),edges(r.state));
    }
    @Test public void xmlRoundTripPreservesAllRelationshipTypesAndDetails() throws Exception {
        TreeState original=tree();original.links.add(new Relation("custom","family","a","h","left"));
        Person p=original.people.get("a");p.bornYear="1948";p.bornMonth="05";p.bornDay="21";
        p.photo="data:image/png;base64,YWJj";Memory m=new Memory();m.id="m";m.title="<История>";m.text="& текст\n😀";p.memories.add(m);
        StringWriter xml=new StringWriter();TreeReadableExport.xml(original,xml,q->q.photo);
        TreeState restored=TreeXmlReader.read(xml.toString());
        assertEquals(edges(original),edges(restored));assertEquals(original.rootId,restored.rootId);
        Person a=restored.people.get("a");assertEquals(p.notes,a.notes);assertEquals(p.photo,a.photo);
        assertEquals(p.bornDay,a.bornDay);assertEquals(p.x,a.x,0);assertEquals(m.text,a.memories.get(0).text);
        assertEquals("left",restored.links.get(restored.links.size()-1).side);
    }
    @Test(expected=java.io.IOException.class) public void xmlRejectsExternalEntities() throws Exception {
        TreeXmlReader.read("<!DOCTYPE family-tree [<!ENTITY x SYSTEM 'file:///secret'>]><family-tree>&x;</family-tree>");
    }
    @Test(expected=java.io.IOException.class) public void xmlRejectsForeignSchemas() throws Exception {
        TreeXmlReader.read("<family-tree version='1'><people/><relationships/></family-tree>");
    }
    @Test(expected=IllegalArgumentException.class) public void malformedGedcomCannotSilentlyDropFamilyRecords() {
        GedcomReader.read("0 HEAD\n0 @A@ INDI\n1 NAME A\nFAM @F1@\n1 HUSB @A@\n");
    }
    @Test public void pickerOnlyAcceptsSupportedExtensions() {
        for(String name:Arrays.asList("x.GED","x.gedcom","x.gdz","x.GDZ","x.ftree","x.tree","x.json","x.zip","x.xml")) assertTrue(name,TreeImportPicker.supports(name));
        for(String name:Arrays.asList("x.jpg","x.html","x.pdf","x.ged.exe","ged","x.xml/other")) assertFalse(name,TreeImportPicker.supports(name));
    }
}
