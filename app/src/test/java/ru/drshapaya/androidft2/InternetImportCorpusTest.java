package ru.drshapaya.androidft2;

import android.content.Context;
import android.content.ContextWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Opt-in audit of unchanged public exports downloaded into build/internet-import-review. */
public final class InternetImportCorpusTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test(timeout=300_000) public void auditDownloadedOriginalsThroughProductionImport() throws Exception {
        File corpus=new File("build/internet-import-review");
        Assume.assumeTrue(new File(corpus,"manifest.json").isFile());
        Context context=new ContextWrapper(null) {
            @Override public File getFilesDir() { return folder.getRoot(); }
            @Override public Context getApplicationContext() { return this; }
        };
        TreeStore store=new TreeStore(context);
        JSONArray manifest=new JSONArray(new String(java.nio.file.Files.readAllBytes(new File(corpus,"manifest.json").toPath()),StandardCharsets.UTF_8));
        JSONArray report=new JSONArray();
        int unexpected=0;
        for(int i=0;i<manifest.length();i++) {
            JSONObject item=manifest.getJSONObject(i), row=new JSONObject(item.toString());
            long started=System.nanoTime();
            try(InputStream input=new BufferedInputStream(new FileInputStream(new File(corpus,item.getString("id"))))) {
                if(item.getInt("expectedPeople")>3000 && "open".equals(item.getString("expectedDisposition"))) {
                    GedcomReader.Result parsed=GedcomReader.read(TreeImportIO.decodeText(TreeImportIO.readBytes(input,TreeImportIO.MAX_TEXT)),false);
                    assertEquals(item.getInt("expectedPeople"),parsed.state.people.size());
                    row.put("status","parsed_without_layout").put("people",parsed.state.people.size()).put("links",parsed.state.links.size())
                        .put("unresolvedReferences",parsed.unresolvedReferences);
                }else {
                TreeImportIO.Result result="zip".equals(item.getString("kind")) ? TreeImportIO.readZip(input,store) : TreeImportIO.readText(input,store);
                row.put("people",result.state.people.size()).put("links",result.state.links.size())
                    .put("missingPhotos",result.missingPhotos).put("unresolvedReferences",result.unresolvedReferences)
                    .put("recoveredTextLines",result.recoveredTextLines);
                assertEquals("People must not disappear",item.getInt("expectedPeople"),result.state.people.size());
                if(result.state.people.size()<=5000) {
                    TreeState reopened=store.parse(store.exportText(result.state));
                    assertEquals(result.state.people.size(),reopened.people.size());assertEquals(result.state.links.size(),reopened.links.size());
                    for(Person person:result.state.people.values()) {
                        Person saved=reopened.people.get(person.id);assertNotNull(saved);
                        assertEquals(person.name,saved.name);assertEquals(person.notes,saved.notes);
                        assertEquals(person.bornYear,saved.bornYear);assertEquals(person.diedYear,saved.diedYear);
                        assertEquals(person.gender,saved.gender);
                    }
                    for(int edge=0;edge<result.state.links.size();edge++) {
                        Relation a=result.state.links.get(edge),b=reopened.links.get(edge);
                        assertEquals(a.type,b.type);assertEquals(a.from,b.from);assertEquals(a.to,b.to);
                    }
                    row.put("roundTrip",true);
                }
                row.put("status",result.state.people.isEmpty() ? "no_people" : "open");
                if(!"open".equals(item.getString("expectedDisposition")) && !result.state.people.isEmpty()) unexpected++;
                if(item.getString("id").equals("gedcom7-maximal70.gdz")) {
                    assertEquals("The original GEDZIP portrait must be imported",0,result.missingPhotos);
                    assertTrue(result.state.people.values().stream().anyMatch(p -> !p.photoMediaId.isEmpty()));
                }
                }
            }catch(Exception | AssertionError error) {
                row.put("status","rejected").put("error",error.toString());
                if("open".equals(item.getString("expectedDisposition"))) unexpected++;
            }
            row.put("elapsedMs",(System.nanoTime()-started)/1_000_000L);report.put(row);
            java.nio.file.Files.write(new File(corpus,"jvm-results.json").toPath(),report.toString(2).getBytes(StandardCharsets.UTF_8));
            System.out.println(item.getString("id")+" "+row.getString("status")+" "+row.optInt("people")+" people "+row.getLong("elapsedMs")+" ms "+row.optString("error"));
        }
        assertEquals("Unexpected import outcomes; see build/internet-import-review/jvm-results.json",0,unexpected);
    }
}
