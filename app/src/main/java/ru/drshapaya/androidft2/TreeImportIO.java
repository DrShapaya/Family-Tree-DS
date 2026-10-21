package ru.drshapaya.androidft2;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Imports portable GEDCOM bundles without extracting arbitrary archive paths. */
final class TreeImportIO {
    static final long MAX_TEXT = 25L * 1024 * 1024;

    static final class Result {
        final TreeState state;
        int missingPhotos, unresolvedReferences, embeddedPhotos, recoveredTextLines, ambiguousPhotos;
        Result(TreeState state) { this.state = state; }
        String notice(boolean english) {
            StringBuilder s = new StringBuilder();
            if(recoveredTextLines>0) s.append(english
                ? "Non-standard GEDCOM text lines recovered: " + recoveredTextLines + ". Notes were preserved.\n\n"
                : "Восстановлены нестандартные строки текста GEDCOM: " + recoveredTextLines + ". Заметки сохранены.\n\n");
            if(ambiguousPhotos>0) s.append(english
                ? "Conflicting photo paths in the ZIP: " + ambiguousPhotos + ". These photos were skipped; the tree was imported.\n\n"
                : "Разные фотографии с одинаковым путём в ZIP: " + ambiguousPhotos + ". Эти фото пропущены; дерево прочитано.\n\n");
            if (missingPhotos > 0) s.append(english
                ? "Portraits not found: " + missingPhotos + ". Import a ZIP containing the GEDCOM file and its photo folder.\n\n"
                : "Не найдены фотографии карточек: " + missingPhotos + ". Импортируйте ZIP с файлом GEDCOM и его папкой фотографий.\n\n");
            if (embeddedPhotos > 0) s.append(english
                ? "Embedded GEDCOM images are not supported. Export photos into a folder and include it in the ZIP.\n\n"
                : "Встроенные изображения GEDCOM пока не поддерживаются. Экспортируйте фото в папку и добавьте её в ZIP.\n\n");
            if (unresolvedReferences > 0) s.append(english
                ? "References to missing records: " + unresolvedReferences + ". Some relationships or notes may be incomplete. Export the complete tree without filters."
                : "Ссылки на отсутствующие записи: " + unresolvedReferences + ". Часть связей или заметок может быть неполной. Экспортируйте дерево целиком, без фильтров.");
            return s.toString().trim();
        }
    }

    static Result readText(InputStream input, TreeStore store) throws Exception {
        String text = decodeText(readBytes(input, MAX_TEXT));
        if (TreeStore.normalizeImportedText(text).startsWith("<")) return new Result(TreeXmlReader.read(text));
        if (!TreeStore.looksLikeGedcom(TreeStore.normalizeImportedText(text))) return new Result(store.parse(text));
        GedcomReader.Result parsed = GedcomReader.read(text);
        Result result = result(parsed);
        result.missingPhotos = parsed.photos.size();
        return result;
    }

    static Result readZip(InputStream source, TreeStore store) throws Exception {
        File staged = store.mediaStore().createStagingFile("gedcom-import", ".zip");
        try {
            try (FileOutputStream out = new FileOutputStream(staged)) {
                byte[] buffer = new byte[65536];
                long nextSpaceCheck = 0, total = 0;
                int n;
                while ((n = source.read(buffer)) != -1) {
                    total += n;
                    if(total>=nextSpaceCheck) {
                        store.mediaStore().ensureFreeSpace(8L * 1024 * 1024);
                        nextSpaceCheck=total+8L * 1024 * 1024;
                    }
                    out.write(buffer, 0, n);
                }
            }
            try (ZipFile zip = new ZipFile(staged)) {
                if (zip.getEntry("manifest.json") != null && zip.getEntry("tree.json") != null) {
                    return new Result(TreePackageIO.readStaged(staged, store));
                }
                Map<String, ZipEntry> entries = new LinkedHashMap<>();
                List<String> trees = new ArrayList<>();
                java.util.Set<String> ambiguous=new java.util.HashSet<>();
                Enumeration<? extends ZipEntry> all = zip.entries();
                int count = 0;
                while (all.hasMoreElements()) {
                    ZipEntry e = all.nextElement();
                    if (++count > 10_000) throw new IOException("Слишком много файлов в архиве");
                    String name = safePath(e.getName());
                    if (name.isEmpty()) throw new IOException("Недопустимый путь в архиве");
                    if (e.isDirectory() || name.startsWith("__MACOSX/")) continue;
                    String lower = name.toLowerCase(Locale.ROOT);
                    boolean isTree=lower.endsWith(".ged") || lower.endsWith(".gedcom");
                    if(!isTree && imageMime(name).isEmpty()) continue;
                    if(ambiguous.contains(name)) continue;
                    ZipEntry previous=entries.get(name);
                    if(previous!=null) {
                        if(sameEntry(zip,previous,e,isTree ? MAX_TEXT : TreeMediaStore.MAX_PHOTO_BYTES)) continue;
                        if(isTree) throw new IOException("В архиве разные файлы GEDCOM с одинаковым путём: "+name);
                        entries.remove(name); ambiguous.add(name); continue;
                    }
                    entries.put(name,e);
                    if(isTree) trees.add(name);
                }
                if (trees.size() != 1) throw new IOException(trees.isEmpty()
                    ? "В архиве нет GEDCOM. Добавьте .ged и папку фотографий."
                    : "В архиве несколько деревьев GEDCOM. Оставьте один .ged и его фотографии.");
                String treeName = trees.get(0);
                GedcomReader.Result parsed;
                try (InputStream input = zip.getInputStream(entries.get(treeName))) {
                    parsed = GedcomReader.read(decodeText(readBytes(input, MAX_TEXT)));
                }
                Result result = result(parsed);
                result.ambiguousPhotos=ambiguous.size();
                Map<String, String> imported = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> photo : parsed.photos.entrySet()) {
                    String mediaId = "";
                    for (String reference : photo.getValue()) {
                        String match = findPhoto(entries.keySet(), treeName, reference);
                        if (match.isEmpty() || imageMime(match).isEmpty()) continue;
                        mediaId = imported.get(match);
                        if (mediaId == null) {
                            try (InputStream input = zip.getInputStream(entries.get(match))) {
                                TreeMediaStore.StoredMedia stored = store.mediaStore().importPhoto(input, match, imageMime(match));
                                mediaId = stored.id;
                                imported.put(match, mediaId);
                            }
                        }
                        break;
                    }
                    if (mediaId == null || mediaId.isEmpty()) result.missingPhotos++;
                    else parsed.state.people.get(photo.getKey()).photoMediaId = mediaId;
                }
                return result;
            }
        } finally { staged.delete(); }
    }

    private static Result result(GedcomReader.Result parsed) {
        Result result = new Result(parsed.state);
        result.unresolvedReferences = parsed.unresolvedReferences;
        result.embeddedPhotos = parsed.embeddedPhotos;
        result.recoveredTextLines = parsed.recoveredTextLines;
        return result;
    }

    private static boolean sameEntry(ZipFile zip,ZipEntry first,ZipEntry second,long limit) throws Exception {
        if(first.getSize()!=second.getSize() || first.getCrc()!=second.getCrc() || first.getSize()>limit) return false;
        // CRC alone does not establish that two payloads are identical.
        return java.util.Arrays.equals(entryDigest(zip,first,limit),entryDigest(zip,second,limit));
    }
    private static byte[] entryDigest(ZipFile zip,ZipEntry entry,long limit) throws Exception {
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        try(InputStream input=zip.getInputStream(entry)) {
            byte[] buffer=new byte[65536]; long size=0; int count;
            while((count=input.read(buffer))!=-1) {
                size+=count; if(size>limit) throw new IOException("Файл превышает допустимый размер");
                digest.update(buffer,0,count);
            }
        }
        return digest.digest();
    }

    static String safePath(String value) {
        String path = value.replace('\\', '/');
        while (path.startsWith("./")) path = path.substring(2);
        if (path.startsWith("/") || path.contains(":") || path.indexOf('\0') >= 0) return "";
        for (String part : path.split("/")) if ("..".equals(part)) return "";
        return path;
    }

    static String findPhoto(java.util.Set<String> entries, String treeName, String reference) {
        return findPhoto(entries,treeName,reference,true);
    }
    private static String findPhoto(java.util.Set<String> entries,String treeName,String reference,boolean decodeUri) {
        String path = safePath(reference);
        if (path.isEmpty()) return "";
        int slash = treeName.lastIndexOf('/');
        String relative = (slash < 0 ? "" : treeName.substring(0, slash + 1)) + path;
        if (entries.contains(relative)) return relative;
        if (entries.contains(path)) return path;
        String match = "";
        for (String entry : entries) if (entry.equalsIgnoreCase(relative) || entry.equalsIgnoreCase(path)) {
            if (!match.isEmpty()) return ""; // Ambiguous files must not be assigned to the wrong person.
            match = entry;
        }
        if(!match.isEmpty() || !decodeUri || reference.indexOf('%')<0) return match;
        // GEDZIP FILE paths are URI paths; Unicode names and spaces are percent-encoded.
        try {
            String decoded=java.net.URLDecoder.decode(reference.replace("+","%2B"),"UTF-8");
            return findPhoto(entries,treeName,decoded,false);
        }catch(java.io.UnsupportedEncodingException | IllegalArgumentException invalid) { return ""; }
    }

    static String imageMime(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "";
    }

    static byte[] readBytes(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) {
            if ((long) out.size() + n > limit) throw new IOException("Файл превышает допустимый размер");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    static String decodeText(byte[] data) throws IOException {
        if (data.length >= 2 && ((data[0] == (byte) 0xff && data[1] == (byte) 0xfe)
            || (data[0] == (byte) 0xfe && data[1] == (byte) 0xff))) return decodeStrict(data, StandardCharsets.UTF_16);
        // GEDCOM 5.x UNICODE transmissions need not have a BOM (0 HEAD in UTF-16).
        if(data.length>=4 && data[0]=='0' && data[1]==0 && data[2]==' ' && data[3]==0)
            return decodeStrict(data,StandardCharsets.UTF_16LE);
        if(data.length>=4 && data[0]==0 && data[1]=='0' && data[2]==0 && data[3]==' ')
            return decodeStrict(data,StandardCharsets.UTF_16BE);
        try {
            return decodeStrict(data,StandardCharsets.UTF_8);
        } catch (CharacterCodingException error) {
            String header = new String(data, 0, Math.min(data.length, 4096), StandardCharsets.ISO_8859_1);
            header=header.split("(?m)^0[ \\t]+@",2)[0];
            java.util.regex.Matcher charset = java.util.regex.Pattern.compile("(?im)^1[ \\t]+CHAR[ \\t]+([^\\r\\n]+)").matcher(header);
            if(charset.find()) {
                String encoding=charset.group(1).trim().toUpperCase(Locale.ROOT);
                if(encoding.matches("(?:WINDOWS-|CP)125[0-8]"))
                    return decodeStrict(data,Charset.forName("windows-"+encoding.substring(encoding.length()-4)));
                if(encoding.equals("ISO-8859-1") || encoding.equals("LATIN1") || encoding.equals("LATIN-1"))
                    return decodeStrict(data,StandardCharsets.ISO_8859_1);
                if(encoding.equals("ANSI")) {
                    java.util.regex.Matcher version=java.util.regex.Pattern.compile("(?im)^2[ \\t]+VERS[ \\t]+(?:WINDOWS-|CP)?(125[0-8])[ \\t]*$")
                        .matcher(header.substring(charset.end()));
                    if(version.find()) return decodeStrict(data,Charset.forName("windows-"+version.group(1)));
                    // Russian legacy exporters use CP1251; western ANSI exports use CP1252.
                    boolean russian=java.util.regex.Pattern.compile("(?im)^1[ \\t]+LANG[ \\t]+(?:Russian|Русский)[ \\t]*$").matcher(header).find();
                    boolean western=java.util.regex.Pattern.compile("(?im)^1[ \\t]+(?:SOUR[ \\t]+(?:FTM|FamilyOrigins)|LANG[ \\t]+(?:English|German|French|Spanish|Italian|Portuguese))[ \\t]*$").matcher(header).find();
                    return decodeStrict(data,Charset.forName(western && !russian ? "windows-1252" : "windows-1251"));
                }
            }
            throw new IOException("Кодировка файла не поддерживается. Экспортируйте GEDCOM в UTF-8.", error);
        }
    }
    private static String decodeStrict(byte[] data,Charset charset) throws CharacterCodingException {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
    }
}
