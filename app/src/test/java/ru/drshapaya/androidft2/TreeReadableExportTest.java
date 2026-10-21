package ru.drshapaya.androidft2;

import static org.junit.Assert.*;
import java.io.StringReader;
import java.io.StringWriter;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.InputSource;

public class TreeReadableExportTest {
    private TreeState tree() {
        TreeState s=new TreeState();
        Person a=new Person("a\"&");a.name="Иван <Тест> & \"Семья\"";a.notes="</script><script>alert(1)</script>\nNext";
        a.bornYear="1950";a.bornMonth="04";a.bornDay="16";a.x=10;a.y=20;
        Person b=new Person("b");b.name="Ребёнок";b.x=20;b.y=400;
        s.people.put(a.id,a);s.people.put(b.id,b);s.rootId=a.id;s.addRelation("parent",a.id,b.id);
        return s;
    }
    @Test public void xmlIsWellFormedAndPreservesUnicodeLinksAndText() throws Exception {
        TreeState s=tree();StringWriter out=new StringWriter();TreeReadableExport.xml(s,out,p->"");
        javax.xml.parsers.DocumentBuilderFactory factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();
        org.w3c.dom.Document doc=factory.newDocumentBuilder().parse(new InputSource(new StringReader(out.toString())));
        assertEquals(2,doc.getElementsByTagName("person").getLength());
        assertEquals(s.rootId,doc.getElementsByTagName("relationship").item(0).getAttributes().getNamedItem("from").getNodeValue());
        assertEquals(s.people.get(s.rootId).notes,doc.getElementsByTagName("notes").item(0).getTextContent());
        assertEquals("16",doc.getElementsByTagName("birth").item(0).getAttributes().getNamedItem("day").getNodeValue());
        assertEquals("A😀B",TreeReadableExport.escape("A\u0001😀B\uD800"));
    }
    @Test public void htmlDataCannotEscapeItsScriptElement() throws Exception {
        StringWriter out=new StringWriter();TreeReadableExport.html(tree(),out,"__TREE_DATA__",false,p->"https://example.com/private.jpg");
        assertFalse(out.toString().contains("</script>"));
        JSONObject data=new JSONObject(out.toString());
        assertEquals(2,data.getJSONArray("people").length());assertEquals(1,data.getJSONArray("links").length());
        assertEquals(tree().people.values().iterator().next().notes,data.getJSONArray("people").getJSONObject(0).getString("notes"));
        assertEquals("",data.getJSONArray("people").getJSONObject(0).getString("photo"));
        assertEquals("16.04.1950",data.getJSONArray("people").getJSONObject(0).getString("born"));
    }
    @Test public void acceptsOnlyEmbeddedRasterPortraits() {
        assertEquals("data:image/png;base64,YWJj",TreeReadableExport.safePhoto("data:image/png;base64,YWJj"));
        assertEquals("",TreeReadableExport.safePhoto("data:image/svg+xml;base64,YWJj"));
        assertEquals("",TreeReadableExport.safePhoto("javascript:alert(1)"));
    }

    @Test public void generatesOfflinePreviewWithActualTemplate() throws Exception {
        java.nio.file.Path template=java.nio.file.Paths.get("src/main/assets/tree-export.html");
        if(!java.nio.file.Files.exists(template)) template=java.nio.file.Paths.get("app/src/main/assets/tree-export.html");
        TreeState s=new TreeState();String[] names={"Соколов Николай","Соколова Анна","Соколов Михаил","Соколова Елена","Соколов Алексей","Соколова Мария"};
        for(int i=0;i<names.length;i++){Person p=new Person("p"+i);p.name=names[i];p.bornYear=""+(1935+i*10);p.place="Томск";p.x=100+(i%2)*380;p.y=100+(i/2)*300;s.people.put(p.id,p);}
        s.rootId="p4";s.people.get("p4").notes="Пример семейной истории.\nВсе сведения в этом дереве вымышлены.";
        for(int i=0;i<6;i+=2)s.addRelation("partner","p"+i,"p"+(i+1));
        s.addRelation("parent","p0","p2");s.addRelation("parent","p1","p2");s.addRelation("parent","p2","p4");s.addRelation("parent","p3","p4");
        java.nio.file.Path output=java.nio.file.Paths.get("build/reports/readable-export/example.html");
        java.nio.file.Files.createDirectories(output.getParent());
        try(java.io.Writer writer=java.nio.file.Files.newBufferedWriter(output,java.nio.charset.StandardCharsets.UTF_8)){
            TreeReadableExport.html(s,writer,new String(java.nio.file.Files.readAllBytes(template),java.nio.charset.StandardCharsets.UTF_8),false,p->"");
        }
        String html=new String(java.nio.file.Files.readAllBytes(output),java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(html.contains("__TREE_DATA__"));assertTrue(html.contains("connect-src 'none'"));
    }
}
