package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads records first, then resolves cross references, independent of record order. */
final class GedcomReader {
    static final class Result {
        final TreeState state = new TreeState();
        final Map<String, List<String>> photos = new LinkedHashMap<>();
        int unresolvedReferences;
        int embeddedPhotos;
        int recoveredTextLines;
    }

    private static final class Node {
        int level;
        String pointer = "", tag, value;
        boolean literalText;
        final List<Node> children = new ArrayList<>();
        Node child(String tag) {
            for (Node child : children) if (tag.equals(child.tag)) return child;
            return null;
        }
        String value(String tag) { Node n = child(tag); return n == null ? "" : n.value; }
    }

    private static final class Family {
        final Set<String> parents = new LinkedHashSet<>();
        final Set<String> children = new LinkedHashSet<>();
    }

    static Result read(String text) {
        return read(text,true);
    }

    /** Data-only diagnostic path; production imports always also arrange the tree. */
    static Result read(String text,boolean arrange) {
        Result result = new Result();
        List<Node> roots = new ArrayList<>(), stack = new ArrayList<>(), nodes=new ArrayList<>();
        int count = 0, lineNumber = 0;
        java.util.regex.Pattern syntax = java.util.regex.Pattern.compile("^\\s*(\\d+)\\s+(?:(@[^@]+@)\\s+)?([A-Za-z_][A-Za-z_0-9]*)(?:[ \\t](.*))?$",java.util.regex.Pattern.DOTALL);
        for (String raw : TreeStore.normalizeImportedText(text).split("\\r\\n|\\n|\\r")) {
            lineNumber++;
            if (raw.trim().isEmpty()) continue;
            if (++count > 500_000) throw new IllegalArgumentException("Слишком много строк в GEDCOM");
            java.util.regex.Matcher parts = syntax.matcher(raw);
            if (!parts.matches()) {
                // Some exporters write literal line breaks in text instead of CONT.
                // Recover only text payloads; never hide broken record headers or IDs.
                Node previous=stack.isEmpty() ? null : stack.get(stack.size()-1);
                if(previous!=null && ("NOTE".equals(previous.tag) || "CONT".equals(previous.tag)
                    || "CONC".equals(previous.tag) || "TEXT".equals(previous.tag))
                    && !previous.value.trim().matches("@[^@]+@")
                    && !raw.trim().startsWith("@") && !raw.trim().matches("\\d{1,2}\\s+.*")) {
                    previous.value += "\n"+raw; result.recoveredTextLines++; continue;
                }
                throw new IllegalArgumentException("Некорректная строка GEDCOM: " + lineNumber);
            }
            Node n = new Node();
            try { n.level = Integer.parseInt(parts.group(1)); }
            catch (NumberFormatException ignored) { throw new IllegalArgumentException("Некорректный уровень GEDCOM: " + lineNumber); }
            if (n.level < 0 || n.level > 99) throw new IllegalArgumentException("Некорректный уровень GEDCOM: " + lineNumber);
            n.pointer = parts.group(2) == null ? "" : parts.group(2);
            n.tag = parts.group(3).toUpperCase(java.util.Locale.ROOT);
            n.value = parts.group(4) == null ? "" : parts.group(4);
            // Preserve spaces in NOTE/CONC, but normalize identifiers and enums.
            if (n.value.trim().matches("@[^@]+@") || "SEX".equals(n.tag)) n.value = n.value.trim();
            while (!stack.isEmpty() && stack.get(stack.size() - 1).level >= n.level) stack.remove(stack.size() - 1);
            if (n.level == 0) roots.add(n);
            else if (!stack.isEmpty() && stack.get(stack.size() - 1).level == n.level - 1) stack.get(stack.size() - 1).children.add(n);
            else throw new IllegalArgumentException("Нарушена вложенность GEDCOM в строке " + lineNumber);
            stack.add(n);
            nodes.add(n);
        }
        boolean gedcom7=false;
        for(Node root:roots) if("HEAD".equals(root.tag)) {
            Node gedc=root.child("GEDC");
            gedcom7=gedc!=null && gedc.value("VERS").startsWith("7."); break;
        }
        for(Node node:nodes) {
            node.literalText=gedcom7 ? node.value.startsWith("@@") : node.value.contains("@@");
            if(gedcom7) { if(node.literalText) node.value=node.value.substring(1); }
            else node.value=node.value.replace("@@","@");
        }
        // GEDCOM 7 allows records without an XREF when nothing points to them.
        if(gedcom7) for(Node root:roots) if(root.pointer.isEmpty()
            && ("INDI".equals(root.tag) || "FAM".equals(root.tag)))
            root.pointer="@__FTDS_ANON_"+UUID.randomUUID().toString().replace("-","")+"@";
        Map<String, Node> records = new LinkedHashMap<>();
        for (Node n : roots) if (!n.pointer.isEmpty()) {
            if (records.put(n.pointer, n) != null) throw new IllegalArgumentException("Повторяющийся идентификатор GEDCOM: " + n.pointer);
        }
        Map<String, Person> people = new LinkedHashMap<>();
        Map<String, Family> families = new LinkedHashMap<>();
        Map<String, String> parentGenders = new LinkedHashMap<>();
        Set<String> explicitSex = new LinkedHashSet<>();
        for (Node n : roots) {
            if ("INDI".equals(n.tag)) {
                if (n.pointer.isEmpty()) throw new IllegalArgumentException("У записи INDI нет идентификатора");
                Person p = new Person("p_" + UUID.randomUUID().toString().replace("-", ""));
                Node name = n.child("NAME");
                String full = name == null ? "" : name.value;
                if (full.isEmpty() && name != null) full = name.value("GIVN") + " /" + name.value("SURN") + "/";
                p.name = TreeStore.cleanGedcomName(full);
                if (name != null) {
                    String surname = name.value("SURN");
                    int open = full.indexOf('/'), close = open < 0 ? -1 : full.indexOf('/',open+1);
                    if (close >= 0) surname = full.substring(open+1,close).trim();
                    if (close >= 0 || !surname.isEmpty()) {
                        String given = name.value("GIVN");
                        if (close >= 0) given = (full.substring(0,open) + " " + full.substring(close+1)).trim();
                        String patronymic = "";
                        if (n.child("_FTDS_GIVEN") != null) { given = n.value("_FTDS_GIVEN"); patronymic = n.value("_FTDS_PATR"); }
                        if (!PersonNames.join(surname,given,patronymic).isEmpty() || n.child("_FTDS_GIVEN") != null)
                            PersonNames.set(p,surname,given,patronymic);
                    }
                    for (Node otherName : n.children) {
                        if (!"NAME".equals(otherName.tag) || otherName == name || !"birth".equalsIgnoreCase(otherName.value("TYPE"))) continue;
                        p.maidenName = otherName.value("SURN");
                        int a = otherName.value.indexOf('/'), b = a < 0 ? -1 : otherName.value.indexOf('/',a+1);
                        if (p.maidenName.isEmpty() && b >= 0) p.maidenName = otherName.value.substring(a+1,b).trim();
                    }
                }
                if (n.child("_FTDS_MAIDEN") != null) p.maidenName = n.value("_FTDS_MAIDEN");
                p.maidenNameEnabled = !p.maidenName.isEmpty() && !"N".equals(n.value("_FTDS_MAIDEN_ENABLED"));
                p.surnameDisplay = PersonNames.mode(n.value("_FTDS_SURNAME_DISPLAY"));
                PersonNames.migrateLegacy(p);
                String sex = n.value("SEX");
                if (n.child("SEX") != null) explicitSex.add(n.pointer);
                p.gender = "M".equalsIgnoreCase(sex) ? PersonGender.MALE : "F".equalsIgnoreCase(sex) ? PersonGender.FEMALE : PersonGender.UNKNOWN;
                p.genderManual = !PersonGender.UNKNOWN.equals(p.gender);
                p.lifeStatus = PersonLifeStatus.normalize(n.value("_FTDS_LIFE_STATUS"));
                List<String> primary = new ArrayList<>(), other = new ArrayList<>();
                for (Node field : n.children) {
                    switch (field.tag) {
                        case "BIRT": case "DEAT":
                            boolean birth = "BIRT".equals(field.tag);
                            if (!birth && ("Y".equalsIgnoreCase(field.value.trim()) || field.child("DATE") != null))
                                p.lifeStatus = PersonLifeStatus.DECEASED;
                            TreeStore.applyGedcomDate(p, birth ? "born" : "died", field.value("DATE"));
                            if (birth) p.place = field.value("PLAC");
                            break;
                        case "NOTE": case "SNOTE":
                            Node note = resolve(field, records, result);
                            if (note != null) p.notes += (p.notes.isEmpty() ? "" : "\n") + continuedText(note);
                            break;
                        case "FAMC": case "FAMS":
                            if (field.value.isEmpty() || (gedcom7 && "@VOID@".equals(field.value))) break;
                            Family f = families.computeIfAbsent(field.value, key -> new Family());
                            ("FAMC".equals(field.tag) ? f.children : f.parents).add(n.pointer);
                            if (!records.containsKey(field.value) || !"FAM".equals(records.get(field.value).tag)) result.unresolvedReferences++;
                            break;
                        case "OBJE":
                            Node media = resolve(field, records, result);
                            if (media == null) break;
                            List<String> target = "Y".equalsIgnoreCase(field.value("_PRIM")) || "Y".equalsIgnoreCase(media.value("_PRIM")) ? primary : other;
                            for (Node file : media.children) if ("FILE".equals(file.tag) && !TreeImportIO.imageMime(file.value).isEmpty()) target.add(continuedText(file));
                            if (media.child("BLOB") != null) result.embeddedPhotos++;
                            break;
                        default: break;
                    }
                }
                primary.addAll(other);
                if (!primary.isEmpty()) result.photos.put(p.id, primary);
                people.put(n.pointer.isEmpty() ? p.id : n.pointer, p);
                result.state.people.put(p.id, p);
            } else if ("FAM".equals(n.tag)) {
                if (n.pointer.isEmpty()) throw new IllegalArgumentException("У записи FAM нет идентификатора");
                Family f = families.computeIfAbsent(n.pointer, key -> new Family());
                for (Node field : n.children) {
                    if ("HUSB".equals(field.tag) || "WIFE".equals(field.tag)) {
                        if(gedcom7 && "@VOID@".equals(field.value)) continue;
                        f.parents.add(field.value);
                        parentGenders.put(field.value, "HUSB".equals(field.tag) ? PersonGender.MALE : PersonGender.FEMALE);
                    } else if ("CHIL".equals(field.tag) && !(gedcom7 && "@VOID@".equals(field.value))) f.children.add(field.value);
                }
            }
        }
        for (Map.Entry<String, String> role : parentGenders.entrySet()) {
            Person p = people.get(role.getKey());
            if (!gedcom7 && p != null && !explicitSex.contains(role.getKey()) && PersonGender.UNKNOWN.equals(p.gender)) {
                p.gender = role.getValue();p.genderManual=true;
            }
        }
        Set<String> links = new LinkedHashSet<>();
        for (Node n : roots) if ("INDI".equals(n.tag)) {
            for (Node field : n.children) if ("ASSO".equals(field.tag) && "family".equalsIgnoreCase(field.value("RELA").trim())) {
                Person a = people.get(n.pointer), b = people.get(field.value);
                if (a != null && b != null) link(result.state, links, "family", a, b);
                else result.unresolvedReferences++;
            }
        }
        for (Family f : families.values()) {
            List<Person> parents = resolvePeople(f.parents, people, result), children = resolvePeople(f.children, people, result);
            for (int i = 1; i < parents.size(); i++) link(result.state, links, "partner", parents.get(0), parents.get(i));
            for (Person parent : parents) for (Person child : children) link(result.state, links, "parent", parent, child);
            // A family may contain siblings whose parents were not exported or are unknown.
            if (parents.isEmpty()) for (int i = 1; i < children.size(); i++) link(result.state, links, "sibling", children.get(i - 1), children.get(i));
        }
        result.state.rootId = result.state.people.isEmpty() ? "" : result.state.people.values().iterator().next().id;
        result.state.selectedId = result.state.rootId;
        if(arrange) {
            TreeLayoutEngine.layout(result.state);
            TreeStore.refreshColors(result.state);
        }
        return result;
    }

    private static void link(TreeState state, Set<String> seen, String type, Person a, Person b) {
        if (a == b) return;
        String key = type + ":" + ("parent".equals(type) || a.id.compareTo(b.id) < 0 ? a.id + ":" + b.id : b.id + ":" + a.id);
        if (seen.add(key)) state.links.add(new Relation("l_" + UUID.randomUUID().toString().replace("-", ""), type, a.id, b.id));
    }

    private static List<Person> resolvePeople(Set<String> refs, Map<String, Person> people, Result result) {
        List<Person> found = new ArrayList<>();
        for (String ref : refs) { Person p = people.get(ref); if (p != null) found.add(p); else result.unresolvedReferences++; }
        return found;
    }

    private static Node resolve(Node node, Map<String, Node> records, Result result) {
        if(node.literalText) return node;
        if("@VOID@".equals(node.value)) return null;
        if (!node.value.matches("@[^@]+@")) return node;
        Node target = records.get(node.value);
        if (target == null || !target.tag.equals(node.tag)) { result.unresolvedReferences++; return null; }
        return target;
    }

    private static String continuedText(Node node) {
        StringBuilder text = new StringBuilder(node.value);
        for (Node child : node.children) {
            if ("CONT".equals(child.tag)) text.append('\n').append(continuedText(child));
            else if ("CONC".equals(child.tag)) text.append(continuedText(child));
        }
        return text.toString();
    }
}
