package ru.drshapaya.androidft2;

import java.util.*;

/** Exports explicit relationships without dropping childless families or extra parents. */
final class GedcomWriter {
    private static final class Family {
        String id;
        final List<String> parents = new ArrayList<>();
        final Set<String> children = new LinkedHashSet<>();
    }
    static String write(TreeState state) {
        Map<String,String> ids = new LinkedHashMap<>();
        for (String id : state.people.keySet()) ids.put(id, "I" + (ids.size()+1));
        List<Family> families = new ArrayList<>();
        Map<String,Set<String>> parents = new LinkedHashMap<>();
        for (Relation r : state.links) {
            if (!ids.containsKey(r.from) || !ids.containsKey(r.to) || r.from.equals(r.to)) continue;
            if ("parent".equals(r.type)) parents.computeIfAbsent(r.to, k -> new LinkedHashSet<>()).add(r.from);
            if ("partner".equals(r.type) || "sibling".equals(r.type)) {
                Family f = new Family();
                if ("partner".equals(r.type)) { f.parents.add(r.from); f.parents.add(r.to); }
                else { f.children.add(r.from); f.children.add(r.to); }
                families.add(f);
            }
        }
        for (Map.Entry<String,Set<String>> entry : parents.entrySet()) {
            Set<String> remaining = new LinkedHashSet<>(entry.getValue());
            for (Family f : families) {
                if (f.parents.size() == 2 && entry.getValue().containsAll(f.parents)) {
                    f.children.add(entry.getKey()); remaining.removeAll(f.parents);
                }
            }
            for (String parent : remaining) {
                Family single = null;
                for (Family f : families) if (f.parents.size()==1 && f.parents.get(0).equals(parent)) { single=f; break; }
                if (single==null) { single=new Family(); single.parents.add(parent); families.add(single); }
                single.children.add(entry.getKey());
            }
        }
        for (int i=0;i<families.size();i++) families.get(i).id="F"+(i+1);
        StringBuilder out = new StringBuilder("0 HEAD\n1 SOUR Family Tree DS\n1 GEDC\n2 VERS 5.5.1\n2 FORM LINEAGE-LINKED\n1 CHAR UTF-8\n");
        for (Person p : state.people.values()) {
            out.append("0 @").append(ids.get(p.id)).append("@ INDI\n");
            if (p.namePartsSet) {
                text(out,1,"NAME",PersonNames.join("",p.givenName,p.patronymic) + " /" + p.surname + "/");
                text(out,2,"GIVN",PersonNames.join("",p.givenName,p.patronymic));
                text(out,2,"SURN",p.surname);
                text(out,1,"_FTDS_GIVEN",p.givenName);
                text(out,1,"_FTDS_PATR",p.patronymic);
            } else text(out,1,"NAME",p.name);
            if (!p.maidenName.isEmpty()) {
                String[] parts = PersonNames.parts(p);
                text(out,1,"NAME",PersonNames.join("",parts[1],parts[2]) + " /" + p.maidenName + "/");
                text(out,2,"TYPE","birth"); text(out,2,"SURN",p.maidenName);
                text(out,1,"_FTDS_MAIDEN",p.maidenName);
            }
            text(out,1,"_FTDS_MAIDEN_ENABLED",p.maidenNameEnabled ? "Y" : "N");
            text(out,1,"_FTDS_SURNAME_DISPLAY",p.surnameDisplay);
            String gender=PersonGender.resolve(p);
            if (PersonGender.MALE.equals(gender)) out.append("1 SEX M\n");
            else if (PersonGender.FEMALE.equals(gender)) out.append("1 SEX F\n");
            else out.append("1 SEX U\n");
            text(out,1,"_FTDS_LIFE_STATUS",PersonLifeStatus.normalize(p.lifeStatus));
            String born=MainActivityFiles.gedDate(p.bornDay,p.bornMonth,p.bornYear);
            String died=MainActivityFiles.gedDate(p.diedDay,p.diedMonth,p.diedYear);
            if (born.isEmpty()) born=p.born;
            if (died.isEmpty()) died=p.died;
            if (!born.isEmpty() || !p.place.isEmpty()) {
                out.append("1 BIRT\n"); if(!born.isEmpty()) text(out,2,"DATE",born);
                if(!p.place.isEmpty()) text(out,2,"PLAC",p.place);
            }
            if (!died.isEmpty()) { out.append("1 DEAT\n"); text(out,2,"DATE",died); }
            else if (PersonLifeStatus.DECEASED.equals(p.lifeStatus)) out.append("1 DEAT Y\n");
            if (!p.notes.isEmpty()) text(out,1,"NOTE",p.notes);
            for (Relation r : state.links) if ("family".equals(r.type) && p.id.equals(r.from) && ids.containsKey(r.to)) {
                out.append("1 ASSO @").append(ids.get(r.to)).append("@\n2 RELA family\n");
            }
            for (Family f:families) {
                if(f.parents.contains(p.id)) out.append("1 FAMS @").append(f.id).append("@\n");
                if(f.children.contains(p.id)) out.append("1 FAMC @").append(f.id).append("@\n");
            }
        }
        for (Family f:families) {
            out.append("0 @").append(f.id).append("@ FAM\n");
            // HUSB/WIFE are GEDCOM slots; SEX remains authoritative for same-sex/unknown partners.
            List<String> slots=new ArrayList<>(f.parents);
            if(slots.size()==2 && PersonGender.FEMALE.equals(PersonGender.resolve(state.people.get(slots.get(0))))
                && PersonGender.MALE.equals(PersonGender.resolve(state.people.get(slots.get(1))))) Collections.reverse(slots);
            for(int i=0;i<slots.size();i++) out.append(i==0 && !(slots.size()==1 && PersonGender.FEMALE.equals(PersonGender.resolve(state.people.get(slots.get(0)))))?"1 HUSB @":"1 WIFE @").append(ids.get(slots.get(i))).append("@\n");
            for(String child:f.children) out.append("1 CHIL @").append(ids.get(child)).append("@\n");
        }
        return out.append("0 TRLR\n").toString();
    }
    private static void text(StringBuilder out,int level,String tag,String value) {
        String[] lines=value.replace("\r\n","\n").replace('\r','\n').split("\n",-1);
        for(int i=0;i<lines.length;i++) {
            String line=lines[i].replace("@","@@"); int start=0;
            do {
                int end=Math.min(line.length(),start+180);
                if(end<line.length() && end>start && Character.isHighSurrogate(line.charAt(end-1))) end--;
                if(end<line.length() && line.charAt(end)=='@' && line.charAt(end-1)=='@') {
                    int run=0;for(int at=end-1;at>=start && line.charAt(at)=='@';at--) run++;
                    if((run&1)!=0) end--; // Never split an escaped @ pair across CONC lines.
                }
                out.append(i==0 && start==0?level:level+1).append(' ')
                    .append(start>0?"CONC":i==0?tag:"CONT").append(' ').append(line,start,end).append('\n');
                start=end;
            } while(start<line.length());
        }
    }
}
