"""Produce the audit table from measured results, with original URLs and checksums."""
import collections
import csv
import json
import pathlib

PROJECT = pathlib.Path(__file__).resolve().parents[1]
CORPUS = PROJECT / "app/build/internet-import-review"
DOCS = PROJECT / "docs/diagnostics"
manifest = json.loads((CORPUS / "manifest.json").read_text(encoding="utf-8"))
jvm = {row["id"]: row for row in json.loads((CORPUS / "jvm-results.json").read_text(encoding="utf-8"))}
android = {row["id"]: row for row in json.loads((CORPUS / "android-results.json").read_text(encoding="utf-8"))}
ui = json.loads((CORPUS / "ui-results.json").read_text(encoding="utf-8"))
formats = json.loads((CORPUS / "format-results.json").read_text(encoding="utf-8"))
assert len(manifest) == len(jvm) == len(android) == 45
assert len(ui) == 11 and len(formats) == 7
for name in ("jvm-results.json", "android-results.json", "ui-results.json", "format-results.json"):
    (DOCS / ("internet-import-2026-10-10-" + name)).write_bytes((CORPUS / name).read_bytes())

labels = {"open": "Импорт и расстановка", "parsed_without_layout": "Только чтение данных",
          "no_people": "Нет персон", "rejected": "Отклонён"}
rows = []
for entry in manifest:
    first, device = jvm[entry["id"]], android[entry["id"]]
    rows.append({"file": entry["id"], "source": entry["sourceApplication"], "bytes": entry["bytes"],
                 "encoding": entry["encoding"], "expected_people": entry["expectedPeople"],
                 "jvm_status": first["status"], "jvm_people": first.get("people", ""), "jvm_links": first.get("links", ""),
                 "jvm_ms": first["elapsedMs"], "android_status": device["status"], "android_people": device.get("people", ""),
                 "android_ms": device["elapsedMs"], "error": first.get("error", ""), "sha256": entry["sha"], "url": entry["url"]})
with (DOCS / "internet-import-audit-2026-10-10.csv").open("w", encoding="utf-8-sig", newline="") as file:
    writer = csv.DictWriter(file, fieldnames=list(rows[0]))
    writer.writeheader(); writer.writerows(rows)

intro = """# Проверка импорта на публичных файлах — 10 октября 2026

Проверены 45 скачанных исходных файлов общим размером около 63 МиБ. В корпусе есть публичные исторические деревья, примеры экспортов разных программ и контрольные файлы разработчиков. Контрольные файлы GEDCOM 7 не являются историческими генеалогиями. Скачанные оригиналы не редактировались; каждый сверяется по SHA-256. Набор проверяет импорт, а не все функции приложения или все существующие форматы генеалогических программ.

Источники: [gedcom4j](https://github.com/frizbog/gedcom4j), [публичные генеалогии D-Jeffrey](https://github.com/D-Jeffrey/gedcom-samples), [официальные файлы Gramps](https://github.com/gramps-project/gramps), [официальные примеры GEDCOM](https://gedcom.io/tools/). Программы-экспортёры определены по HEAD.SOUR: PAF, Family Tree Maker, Legacy, Family Historian, Gramps, Ancestris, FamilyOrigins, LifeLines и другие.

## Результат

- JVM, штатный код импорта: 32 файла импортированы с расстановкой; 3 больших файла прочитаны отдельно без расстановки; 8 отклонены по ограничениям или несовместимости; 2 корректных контрольных файла не содержат персон.
- Эмулятор Android 16 / API 36: 31 файл импортирован штатным кодом, 4 больших прочитаны без расстановки; те же 8 отклонений и 2 пустых дерева. Это не проверка на телефонах пользователей.
- Через настоящие окна импорта открыты 11 оригиналов; проверено сохранение и чтение с диска. GEDZIP также проверен после перезапуска Activity.
- Из скачанного GEDZIP создано 7 дополнительных вариантов: JSON, TREE, старый текстовый FTREE, контейнер FTREE, XML Family Tree DS, GEDCOM и ZIP. Все открыты через интерфейс. Это производные тесты собственного формата приложения, а не скачанные сторонние XML/FTREE.
- Проверено точное число INDI по независимому подсчёту в исходнике; после сохранения сверены имена, заметки, годы дат и типы/концы связей. На Android проверено декодирование реального JPEG из GEDZIP. Сохранённые связи сверяются с импортированной моделью; полный независимый аудит всех значений GEDCOM и всех типов событий не выполнялся.
- Последние проверки: 50 подходящих JVM-тестов, 3 инструментальных теста корпуса и 6 тестов меню импорта — без ошибок. В двух тестах корпуса выполнены 11 импортов оригиналов и 7 импортов производных файлов. В тестах меню дополнительно проверен выбор GED, который файловый провайдер считает обычным бинарным файлом, а также отмена, прокрутка и оформление окон.

## Исправления, найденные при проверке

1. UTF-16 без BOM в обоих порядках байтов; некорректный UTF-16 больше не заменяется молча символами-заглушками.
2. Явно заявленные CP1252 / Windows-125x и Latin-1. Для ANSI учитываются кодовая страница, язык и известные западные экспортёры; прежний русский вариант сохраняется для остальных старых экспортов. Неоднозначный ANSI без метаданных всё ещё нельзя гарантированно распознать.
3. Расширение GEDZIP `.gdz` теперь разрешено в выборе файлов.
4. GEDCOM 7: записи без XREF получают локальные идентификаторы; SNOTE читаются; @VOID@ не создаёт ложных отсутствующих родственников; пол не назначается по HUSB/WIFE. Для GEDCOM 5 сохранена прежняя трактовка ролей с сохранением пола при повторном чтении.
5. URI-пути фотографий с percent-encoding. `media/CharlotteBront%C3%AB.jpg` из исходного GEDZIP теперь соответствует реальному файлу `CharlotteBrontë.jpg` в архиве. Проверено открытие JPEG на Android и его сохранение.
6. Пробелы и пустые строки в начале/конце биографии сохраняются после повторного открытия.
7. Экранирование @ различается в GEDCOM 5 и 7. Импорт это учитывает; экспорт GEDCOM 5 экранирует @ и не разрывает пару при переносе CONC.

## Неустранённые ограничения и обнаруженные проблемы

- Четыре исходника с не-ASCII символами ANSEL не поддерживаются. `royal92.ged` объявляет ANSEL, но содержит только ASCII: его текст читается без преобразования. Для остальных приложение предлагает экспорт UTF-8.
- Habsburg: 34 020 персон, 567 672 непустые строки, 9,45 МиБ — отклонён ограничением 500 000 строк. WilliamLongsword: 203 154 персоны, 46,09 МиБ — отклонён ограничением текста 25 МиБ. Эти отклонения не считаются успешным импортом.
- Сложная расстановка больших деревьев слишком медленная. В первом полном прогоне дерево PAF на 4 578 персон заняло примерно 44 секунды на JVM, `royal92` на 3 010 — примерно 84 секунды. Прогон с `Queen.ged` не завершился за общий лимит 300 секунд; файл не успел завершить импорт. Стек показал работу алгоритма расстановки. В итоговом корпусе эти 3 дерева отдельно прочитаны без расстановки; это не доказательство успешного открытия в приложении. `pres2020` на 2 322 человека полностью импортирован на JVM, на Android отдельно проверено чтение данных.
- На визуальной проверке `maximal70.gdz` обнаружен слишком большой разнос отдельной несвязанной карточки: x=11 880 при остальных x=160…480. Общий обзор делает карточки очень маленькими. Данные и фотография сохранены, но расстановка/масштаб требуют отдельной доработки. Скриншот сохранён в `app/build/internet-import-review/internet-gedzip-after-relaunch.png`.
- Один контрольный файл Gramps объявляет ASCII, но содержит Latin-1: он отклонён, а не прочитан с испорченными именами. ZIP `imp_5.2.4` — база Gramps с SQLite, без GEDCOM; импорт этой базы не поддерживается. Два официальных контрольных файла без INDI не дают открываемого дерева.
- Предупреждения об отсутствующих фото и ссылках ожидаемы для отдельных GED, опубликованных без медиа или с неполными ссылками. В `Queen` обнаружено 622 неразрешённых обращения; этот факт не скрыт. Семейные события, источники, ссылки на документы, переводы заметок и все неизвестные теги GEDCOM не покрываются полным сохранением.
- Оригиналов пользователей `drevo.ged`, `drevoBAF.ged`, `Gedcom.zip` нет: доступны только их скриншоты. Именно их исправление этой проверкой не подтверждено. Подтверждённого публичного экспорта Ahnenblatt 4 для корпуса найти не удалось. Реальный архив больше 1 ГБ не скачивался и не проверялся.

## Воспроизведение

`tools/download_internet_import_corpus.py` загружает закреплённые URL из `docs/diagnostics/internet-import-corpus-2026-10-10.json`, сверяет SHA-256 и складывает оригиналы в `app/build/internet-import-review`. `tools/audit_internet_imports.py` независимо считает записи. `InternetImportCorpusTest` пропускается, если корпус не скачан. Для инструментального `InternetImportUiTest` корпус нужно скопировать в `…/Android/data/ru.drshapaya.familytree.debug/cache/internet-import-review` на эмуляторе API 29+.

Проверка JVM: `gradlew.bat :app:testDebugUnitTest --tests '*InternetImportCorpusTest' --tests '*GedcomCompatibilityTest' --tests '*TreeInterchangeTest' --tests '*TreeImportArchiveTest' --tests '*GedcomLargeImportTest' --tests '*GedcomNameImportTest'`.

Проверка Android: `adb shell am instrument -w -r -e class ru.drshapaya.androidft2.InternetImportUiTest ru.drshapaya.familytree.debug.test/android.test.InstrumentationTestRunner`.

CSV содержит URL, SHA-256, размер и фактические результаты всех файлов. JSON-результаты JVM, Android, интерфейса и дополнительных форматов лежат рядом с этим отчётом. Исходники и скриншоты находятся в `app/build/internet-import-review`. Собрана `AndroidFT-2.8.1-real-import-tested-debug.apk` в родительской папке проекта.

## Все 45 оригиналов

«Только данные» означает, что расстановка и открытие через интерфейс этим результатом не подтверждены.

| Файл / источник | Персон в оригинале | JVM | Android | Причина отказа / замечание |
|---|---:|---|---|---|
"""
table = []
for entry in manifest:
    first, device = jvm[entry["id"]], android[entry["id"]]
    reason = first.get("error", "").replace("java.io.IOException: ", "").replace("java.lang.IllegalArgumentException: ", "").replace("|", "/")
    if first["status"] == "parsed_without_layout": reason = "Расстановка в итоговом прогоне не выполнялась"
    if first["status"] == "no_people": reason = "Контрольный файл без INDI"
    table.append(f"| [{entry['id']}]({entry['url']}) | {entry['expectedPeople']} | {labels[first['status']]} | {labels[device['status']]} | {reason} |")
(DOCS / "internet-import-audit-2026-10-10.md").write_text(intro + "\n".join(table) + "\n", encoding="utf-8")
print("Wrote report and CSV for", len(rows), "verified originals")
