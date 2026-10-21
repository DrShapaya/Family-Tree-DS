#!/usr/bin/env python3
"""Build the October export-and-share tutorial from real Android recordings."""

from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[2]
VIDEO = ROOT / "docs/marketing/channel/2026-10/videos/export-and-share/v1"
SOURCE = VIDEO / "source"
CAPTIONS = VIDEO / "overlays/captions"
HIGHLIGHTS = VIDEO / "overlays/highlights"
ENDCARDS = VIDEO / "overlays/endcards"
PHOTOS = VIDEO / "photos"
PUBLISH = VIDEO / "publish"
FONTS = ROOT / "app/src/main/assets/fonts"
SIZE = (1080, 2400)
TEAL = (20, 118, 108)
INK = (26, 50, 55)
ORANGE = (250, 135, 76)


def ffmpeg() -> str:
    found = shutil.which("ffmpeg")
    if found:
        return found
    return r"C:\Program Files\Wondershare\Recoverit - Data Recovery\ffmpeg.exe"


def run(*args: str) -> None:
    subprocess.run([ffmpeg(), "-hide_banner", "-loglevel", "error", "-y", *args], check=True)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    name = "NotoSans-Bold.ttf" if bold else "NotoSans-Regular.ttf"
    return ImageFont.truetype(str(FONTS / name), size)


def caption(number: int, title: str, detail: str, y: int = 1930) -> Path:
    path = CAPTIONS / f"step-{number:02}.png"
    panel = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    shadow = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((54, y + 11, 1026, y + 161), radius=32, fill=(9, 51, 48, 78))
    panel.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(18)))
    draw = ImageDraw.Draw(panel)
    draw.rounded_rectangle((54, y, 1026, y + 150), radius=32, fill=(255, 255, 255, 246), outline=(169, 215, 210, 255), width=3)
    draw.rounded_rectangle((77, y + 28, 166, y + 122), radius=24, fill=(*TEAL, 255))
    draw.text((103, y + 44), f"{number:02}", font=font(36, True), fill="white")
    main_face = font(37, True)
    if draw.textbbox((0, 0), title, font=main_face)[2] > 822:
        main_face = font(32, True)
    draw.text((191, y + 19), title, font=main_face, fill=(*INK, 255))
    draw.text((193, y + 80), detail, font=font(25), fill=(81, 104, 105, 255))
    panel.save(path)
    return path


def highlight(name: str, box: tuple[int, int, int, int], label: str) -> Path:
    path = HIGHLIGHTS / f"{name}.png"
    image = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    glow = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle(box, radius=28, outline=(*ORANGE, 170), width=19)
    image.alpha_composite(glow.filter(ImageFilter.GaussianBlur(14)))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle(box, radius=28, outline=(*ORANGE, 255), width=9)
    chip_width = max(178, draw.textbbox((0, 0), label, font=font(23, True))[2] + 42)
    chip_x = min(max(box[0], 30), SIZE[0] - chip_width - 30)
    chip_y = max(74, box[1] - 69)
    draw.rounded_rectangle((chip_x, chip_y, chip_x + chip_width, chip_y + 52), radius=24, fill=(*ORANGE, 250))
    draw.text((chip_x + 20, chip_y + 11), label, font=font(23, True), fill="white")
    image.save(path)
    return path


def endcard() -> Path:
    path = ENDCARDS / "result.png"
    image = Image.new("RGB", SIZE)
    draw = ImageDraw.Draw(image)
    for y in range(SIZE[1]):
        ratio = y / SIZE[1]
        draw.line((0, y, SIZE[0], y), fill=(int(16 + 8 * ratio), int(83 + 30 * ratio), int(78 + 27 * ratio)))
    draw.ellipse((427, 610, 653, 836), fill=(231, 250, 241))
    draw.line((481, 722, 525, 769), fill=TEAL, width=21)
    draw.line((516, 769, 604, 671), fill=TEAL, width=21)
    lines = [
        ("ГОТОВО", 36, 900, (171, 231, 211)),
        ("Дерево сохранено", 62, 1010, (255, 255, 255)),
        ("Файл .ftree готов к отправке", 34, 1135, (222, 247, 239)),
        ("Family Tree DS", 32, 1790, (222, 247, 239)),
    ]
    for words, size, y, color in lines:
        face = font(size, size != 34)
        width = draw.textbbox((0, 0), words, font=face)[2]
        draw.text(((SIZE[0] - width) // 2, y), words, font=face, fill=color)
    image.save(path)
    return path


def main() -> None:
    for folder in (SOURCE, CAPTIONS, HIGHLIGHTS, ENDCARDS, PHOTOS, PUBLISH):
        folder.mkdir(parents=True, exist_ok=True)

    share = PHOTOS / "record-share.png"
    source = SOURCE / "export-share-raw.mp4"
    extra = SOURCE / "export-share-part3.mp4"
    clean = SOURCE / "export-share-clean-source.mp4"
    run(
        "-i", str(source), "-i", str(extra), "-loop", "1", "-t", "5", "-i", str(share),
        "-filter_complex",
        "[0:v]trim=start=0:end=4,setpts=PTS-STARTPTS,fps=30[a];"
        "[0:v]trim=start=8:end=18,setpts=PTS-STARTPTS,fps=30[b];"
        "[0:v]trim=start=36:end=47,setpts=PTS-STARTPTS,fps=30[c];"
        "[1:v]trim=start=16:end=19.4,setpts=PTS-STARTPTS,fps=30[d];"
        "[2:v]fps=30,trim=duration=5,setpts=PTS-STARTPTS[e];"
        "[a][b][c][d][e]concat=n=5:v=1:a=0,format=yuv420p[v]",
        "-map", "[v]", "-an", "-c:v", "libx264", "-preset", "medium", "-crf", "18",
        "-movflags", "+faststart", str(clean),
    )

    steps = [
        (0.0, 2.5, caption(1, "Откройте меню «Ещё»", "Разделы приложения — внизу экрана")),
        (2.5, 5.2, caption(2, "Откройте «Файлы и экспорт»", "Здесь находятся действия с файлом дерева")),
        (5.2, 7.4, caption(3, "Нажмите «Экспорт»", "Выберите формат резервной копии", 2140)),
        (7.4, 11.1, caption(4, "Выберите Family Tree DS (.ftree)", "Полная копия с фото и вложениями")),
        (11.1, 13.0, caption(5, "Откроется окно сохранения", "Задайте место для нового файла")),
        (13.0, 19.5, caption(6, "Выберите папку для дерева", "Например, Documents на этом устройстве")),
        (19.5, 22.5, caption(7, "Нажмите Save", "Файл .ftree сохранится в выбранной папке")),
        (22.5, 25.5, caption(8, "Нажмите «Поделиться деревом»", "Приложение подготовит файл для отправки")),
        (25.5, 30.7, caption(9, "Выберите способ отправки", "Отправьте файл близким или сохраните в облаке", 1740)),
    ]
    targets = [
        (0.4, 2.6, highlight("more", (856, 2140, 1060, 2320), "НАЖАТЬ")),
        (2.5, 3.2, highlight("files", (214, 1140, 1025, 1330), "НАЖАТЬ")),
        (5.2, 7.3, highlight("export", (53, 1955, 902, 2103), "ЭКСПОРТ")),
        (7.5, 10.9, highlight("ftree", (70, 882, 1009, 1067), ".FTREE")),
        (13.0, 15.2, highlight("location", (36, 72, 136, 196), "ПАПКА")),
        (19.3, 21.5, highlight("save", (808, 2197, 1057, 2343), "СОХРАНИТЬ")),
        (22.5, 25.4, highlight("share", (48, 1210, 526, 1510), "ПОДЕЛИТЬСЯ")),
        (26.2, 30.6, highlight("send-app", (35, 2030, 630, 2285), "ВЫБРАТЬ")),
    ]
    final_card = endcard()
    layers = steps + targets
    cmd = ["-i", str(clean)]
    for _, _, path in layers:
        cmd += ["-loop", "1", "-i", str(path)]
    cmd += ["-loop", "1", "-t", "3.5", "-i", str(final_card)]

    graph = ["[0:v]fps=30,format=rgba[base]"]
    previous = "base"
    for index, (start, stop, _) in enumerate(layers, 1):
        output = f"layer{index}"
        graph.append(f"[{previous}][{index}:v]overlay=0:0:shortest=1:enable='between(t,{start:.2f},{stop:.2f})'[{output}]")
        previous = output
    end_index = len(layers) + 1
    graph.append(f"[{end_index}:v]fps=25,trim=duration=3.5,setpts=PTS-STARTPTS[end]")
    graph.append(f"[{previous}][end]concat=n=2:v=1:a=0,fade=t=out:st=33.62:d=0.70,format=yuv420p[out]")
    cmd += [
        "-filter_complex", ";".join(graph), "-map", "[out]", "-an",
        "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-movflags", "+faststart",
        str(PUBLISH / "05-export-and-share.mp4"),
    ]
    run(*cmd)

    run("-ss", "9", "-i", str(PUBLISH / "05-export-and-share.mp4"), "-frames:v", "1", str(PHOTOS / "cover.png"))
    run("-i", str(PUBLISH / "05-export-and-share.mp4"), "-vf", "fps=1/3,scale=270:600,tile=4x4", "-frames:v", "1", str(PHOTOS / "contact-final.png"))


if __name__ == "__main__":
    main()
