package ru.drshapaya.androidft2;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Only our versioned interchange schema; never resolve external entities. */
final class TreeXmlReader {
    static TreeState read(String text) throws Exception {
        if (text.contains("<!DOCTYPE") || text.contains("<!ENTITY")) throw new IOException("XML с DTD или внешними сущностями не поддерживается");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder builder=factory.newDocumentBuilder();
        builder.setEntityResolver((publicId,systemId)-> { throw new org.xml.sax.SAXException("External entities disabled"); });
        Element root=builder.parse(new InputSource(new StringReader(text))).getDocumentElement();
        if (!"family-tree".equals(root.getLocalName()) || !"urn:family-tree-ds:export:1".equals(root.getNamespaceURI()) || !"1".equals(root.getAttribute("version")))
            throw new IOException("Это не XML Family Tree DS. Для переноса из другой программы используйте GEDCOM.");
        TreeState state=new TreeState();
        Element people=child(root,"people"), links=child(root,"relationships");
        if(people==null || links==null) throw new IOException("В XML отсутствуют персоны или связи");
        for(Node node=people.getFirstChild();node!=null;node=node.getNextSibling()) {
            if(!(node instanceof Element) || !"person".equals(node.getLocalName())) continue;
            Element e=(Element)node;
            String id=e.getAttribute("id");
            if(id.isEmpty() || state.people.containsKey(id)) throw new IOException("Пустой или повторяющийся идентификатор персоны");
            Person p=new Person(id); p.name=value(e,"name"); p.gender=value(e,"gender");
            p.surname=value(e,"surname"); p.givenName=value(e,"given-name"); p.patronymic=value(e,"patronymic");
            p.maidenName=value(e,"maiden-name"); p.namePartsSet="true".equals(value(e,"name-parts-set"));
            p.maidenNameEnabled="true".equals(value(e,"maiden-name-enabled"));
            p.surnameDisplay=PersonNames.mode(value(e,"surname-display"));
            if(p.namePartsSet) PersonNames.set(p,p.surname,p.givenName,p.patronymic);
            PersonNames.migrateLegacy(p);
            p.genderManual=!PersonGender.UNKNOWN.equals(p.gender);
            p.lifeStatus=PersonLifeStatus.normalize(value(e,"life-status"));
            p.place=value(e,"birth-place"); p.notes=value(e,"notes");
            p.photo=TreeReadableExport.safePhoto(value(e,"portrait"));
            date(p,child(e,"birth"),true); date(p,child(e,"death"),false);
            Element pos=child(e,"position");
            if(pos!=null) { p.x=coordinate(pos,"x");p.y=coordinate(pos,"y"); }
            Element memories=child(e,"memories");
            if(memories!=null) for(Node m=memories.getFirstChild();m!=null;m=m.getNextSibling()) {
                if(!(m instanceof Element) || !"memory".equals(m.getLocalName())) continue;
                Element me=(Element)m; Memory memory=new Memory();
                memory.id=me.getAttribute("id"); memory.type=me.getAttribute("type");
                memory.title=value(me,"title");memory.text=value(me,"text");memory.at=value(me,"date");p.memories.add(memory);
            }
            state.people.put(id,p);
        }
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(Node node=links.getFirstChild();node!=null;node=node.getNextSibling()) {
            if(!(node instanceof Element) || !"relationship".equals(node.getLocalName())) continue;
            Element e=(Element)node; String from=e.getAttribute("from"),to=e.getAttribute("to"),type=e.getAttribute("type"),id=e.getAttribute("id");
            if(!state.people.containsKey(from) || !state.people.containsKey(to) || from.equals(to)
                || !("parent".equals(type)||"partner".equals(type)||"sibling".equals(type)||"family".equals(type)) || id.isEmpty() || !ids.add(id))
                throw new IOException("В XML обнаружена некорректная связь: " + id);
            state.links.add(new Relation(id,type,from,to,e.hasAttribute("side")?e.getAttribute("side"):"right"));
        }
        state.useMaidenNames="true".equals(value(root,"use-maiden-names"));
        state.showQualityBadge=!"false".equals(value(root,"show-quality-badge"));
        state.showLifeStatusBadge="true".equals(value(root,"show-life-status-badge"));
        state.lifeStatusAppearance=LifeStatusAppearance.decode(value(root,"life-status-appearance"));
        state.rootId=root.getAttribute("root");
        if(!state.people.containsKey(state.rootId)) state.rootId=state.people.isEmpty()?"":state.people.keySet().iterator().next();
        state.selectedId=state.rootId;TreeLayoutEngine.ensurePositions(state);TreeStore.refreshColors(state);return state;
    }
    private static float coordinate(Element e,String key) throws IOException {
        try { float f=Float.parseFloat(e.getAttribute(key));if(Float.isFinite(f)) return f; } catch(NumberFormatException ignored) {}
        throw new IOException("Некорректная координата XML");
    }
    private static Element child(Element e,String tag) {
        for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element && tag.equals(n.getLocalName())) return (Element)n;
        return null;
    }
    private static String value(Element e,String tag) { Element c=child(e,tag);return c==null?"":c.getTextContent(); }
    private static void date(Person p,Element e,boolean birth) {
        if(e==null) return;
        if(birth) { p.born=e.getTextContent();p.bornDay=e.getAttribute("day");p.bornMonth=e.getAttribute("month");p.bornYear=e.getAttribute("year"); }
        else { p.died=e.getTextContent();p.diedDay=e.getAttribute("day");p.diedMonth=e.getAttribute("month");p.diedYear=e.getAttribute("year"); }
    }
}
