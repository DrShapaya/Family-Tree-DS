#!/usr/bin/env python3
"""Build a second, compact-caption edition of the two October channel tutorials."""

from __future__ import annotations

import shutil
import subprocess
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[2]
MONTH = ROOT / "docs" / "marketing" / "channel" / "2026-10"
PACKAGE = MONTH / "variant-b"
SOURCE = PACKAGE / "source"
OVERLAYS = PACKAGE / "overlays"
CAPTIONS = OVERLAYS / "captions"
HIGHLIGHTS = OVERLAYS / "highlights"
ENDCARDS = OVERLAYS / "endcards"
PUBLISH = PACKAGE / "publish"
PREVIEW = PACKAGE / "preview"
FONT_DIR = ROOT / "app" / "src" / "main" / "assets" / "fonts"

SIZE = (1080, 2400)
INK = (20, 61, 56)
TEAL = (21, 123, 108)
CORAL = (255, 139, 84)
WHITE = (255, 255, 255)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    name = "NotoSans-Bold.ttf" if bold else "NotoSans-Regular.ttf"
    return ImageFont.truetype(str(FONT_DIR / name), size)


def center_x(draw: ImageDraw.ImageDraw, text: str, face: ImageFont.FreeTypeFont, width: int = 1080) -> int:
    bounds = draw.textbbox((0, 0), text, font=face)
    return round((width - bounds[2] + bounds[0]) / 2)


def caption(path: Path, number: int, line: str) -> None:
    """A 174 px lower-third, less than 8% of the 2400 px frame."""
    frame = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    shadow = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle((54, 1944, 1026, 2118), radius=38, fill=(10, 53, 47, 70))
    frame.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(20)))

    draw = ImageDraw.Draw(frame)
    draw.rounded_rectangle((54, 1934, 1026, 2108), radius=38, fill=(255, 255, 255, 244), outline=(179, 222, 208, 255), width=3)
    draw.rounded_rectangle((78, 1960, 174, 2082), radius=27, fill=(*TEAL, 255))
    draw.text((102, 1986), f"{number:02}", font=font(48, True), fill=WHITE)

    face = font(42, True)
    bounds = draw.textbbox((0, 0), line, font=face)
    if bounds[2] - bounds[0] > 815:
        face = font(37, True)
        bounds = draw.textbbox((0, 0), line, font=face)
    if bounds[2] - bounds[0] > 815:
        raise ValueError(f"Caption is too wide: {line}")
    draw.text((204, 1986), line, font=face, fill=(*INK, 255))
    frame.save(path)


def target_overlay(path: Path, box: tuple[int, int, int, int], label: str = "НАЖАТЬ") -> None:
    """Transparent orange target ring; independent from the caption image."""
    frame = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    glow = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    halo = ImageDraw.Draw(glow)
    halo.rounded_rectangle(box, radius=34, outline=(*CORAL, 180), width=20)
    frame.alpha_composite(glow.filter(ImageFilter.GaussianBlur(15)))

    draw = ImageDraw.Draw(frame)
    draw.rounded_rectangle(box, radius=34, outline=(*CORAL, 255), width=9)
    draw.rounded_rectangle(tuple(v + d for v, d in zip(box, (12, 12, -12, -12))), radius=26, outline=(255, 255, 255, 235), width=3)
    x1, y1, x2, _ = box
    face = font(22, True)
    label_bounds = draw.textbbox((0, 0), label, font=face)
    chip_width = max(154, label_bounds[2] - label_bounds[0] + 44)
    chip_x = max(40, min(x2 - chip_width, SIZE[0] - chip_width - 40))
    chip_y = max(72, y1 - 76)
    draw.rounded_rectangle((chip_x, chip_y, chip_x + chip_width, chip_y + 58), radius=28, fill=(*CORAL, 247))
    draw.text((chip_x + 21, chip_y + 14), label, font=face, fill=WHITE)
    frame.save(path)


def endcard(path: Path, title: str, body: str) -> None:
    frame = Image.new("RGBA", SIZE, (0, 0, 0, 255))
    draw = ImageDraw.Draw(frame)
    for y in range(SIZE[1]):
        t = y / (SIZE[1] - 1)
        draw.line((0, y, SIZE[0], y), fill=(round(23 - 8 * t), round(70 + 57 * t), round(64 + 34 * t), 255))
    draw.ellipse((421, 566, 659, 804), fill=(226, 249, 241, 255))
    draw.line((478, 680, 526, 729), fill=(*TEAL, 255), width=22)
    draw.line((520, 730, 609, 627), fill=(*TEAL, 255), width=22)
    draw.text((center_x(draw, "ГОТОВО", font(34, True)), 858), "ГОТОВО", font=font(34, True), fill=(173, 235, 213, 255))

    title_face = font(64, True)
    draw.text((center_x(draw, title, title_face), 970), title, font=title_face, fill=WHITE)
    body_face = font(35)
    draw.text((center_x(draw, body, body_face), 1095), body, font=body_face, fill=(222, 247, 239, 255))
    draw.rounded_rectangle((196, 1650, 884, 1754), radius=52, fill=(222, 247, 239, 255))
    footer = "Family Tree DS"
    draw.text((center_x(draw, footer, font(31, True)), 1680), footer, font=font(31, True), fill=(*INK, 255))
    frame.save(path)


def ffmpeg_binary() -> str:
    installed = shutil.which("ffmpeg")
    if installed:
        return installed
    local = Path(r"C:\Program Files\Wondershare\Recoverit - Data Recovery\ffmpeg.exe")
    if local.is_file():
        return str(local)
    raise FileNotFoundError("ffmpeg was not found")


def run_ffmpeg(args: list[str]) -> None:
    subprocess.run([ffmpeg_binary(), "-hide_banner", "-loglevel", "error", "-y", *args], check=True)


def compose(name: str, input_name: str, trim_start: float, source_seconds: float, body_seconds: float, total_seconds: float,
            steps: list[tuple[float, float, str]], targets: list[tuple[float, float, str]], end_name: str) -> None:
    """Put caption and target PNGs on independent ffmpeg tracks."""
    still_seconds = body_seconds - source_seconds
    inputs: list[Path] = []
    for _, _, file_name in steps:
        inputs.append(CAPTIONS / file_name)
    for _, _, file_name in targets:
        inputs.append(HIGHLIGHTS / file_name)
    inputs.append(ENDCARDS / end_name)

    command = ["-ss", str(trim_start), "-t", str(source_seconds), "-i", str(SOURCE / input_name)]
    for image in inputs:
        command.extend(["-loop", "1", "-i", str(image)])

    graph = [f"[0:v]setpts=PTS-STARTPTS,fps=30,tpad=stop_mode=clone:stop_duration={still_seconds:.3f}[base]"]
    last = "base"
    index = 1
    for start, end, _ in steps + targets:
        next_label = f"layer{index}"
        graph.append(f"[{last}][{index}:v]overlay=0:0:enable='between(t,{start:.2f},{end:.2f})'[{next_label}]")
        last = next_label
        index += 1

    end_label = f"end{index}"
    graph.append(f"[{index}:v]format=rgba,fade=t=in:st={body_seconds:.2f}:d=0.35:alpha=1[{end_label}]")
    graph.append(f"[{last}][{end_label}]overlay=0:0:enable='between(t,{body_seconds:.2f},{total_seconds:.2f})',fade=t=out:st={total_seconds-0.55:.2f}:d=0.55,format=yuv420p[out]")
    command.extend([
        "-filter_complex", ";".join(graph), "-map", "[out]", "-t", str(total_seconds), "-an",
        "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-movflags", "+faststart",
        str(PUBLISH / name),
    ])
    run_ffmpeg(command)


def archive(path: Path, members: list[tuple[Path, str]]) -> None:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as package:
        for source, destination in members:
            if source.is_dir():
                for file in sorted(source.rglob("*")):
                    if file.is_file():
                        package.write(file, str(Path(destination) / file.relative_to(source)))
            else:
                package.write(source, destination)


def main() -> None:
    for directory in (SOURCE, CAPTIONS, HIGHLIGHTS, ENDCARDS, PUBLISH, PREVIEW):
        directory.mkdir(parents=True, exist_ok=True)

    shutil.copy2(MONTH / "raw" / "search-tutorial-v2.mp4", SOURCE / "search-tutorial-v2.mp4")
    shutil.copy2(MONTH / "raw" / "files-tutorial-v2.mp4", SOURCE / "files-tutorial-v2.mp4")
    run_ffmpeg([
        "-ss", "15.0", "-t", "18.75", "-i", str(SOURCE / "search-tutorial-v2.mp4"),
        "-an", "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-movflags", "+faststart",
        str(SOURCE / "search-clean-source.mp4"),
    ])
    run_ffmpeg([
        "-ss", "5.0", "-t", "14.50", "-i", str(SOURCE / "files-tutorial-v2.mp4"),
        "-an", "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-movflags", "+faststart",
        str(SOURCE / "files-clean-source.mp4"),
    ])
    run_ffmpeg([
        "-ss", "9.75", "-i", str(SOURCE / "files-clean-source.mp4"),
        "-frames:v", "1", str(SOURCE / "files-reading-pause.png"),
    ])
    run_ffmpeg([
        "-i", str(SOURCE / "files-clean-source.mp4"),
        "-loop", "1", "-t", "2.5", "-i", str(SOURCE / "files-reading-pause.png"),
        "-i", str(SOURCE / "files-clean-source.mp4"),
        "-filter_complex",
        "[0:v]trim=duration=9.75,setpts=PTS-STARTPTS,fps=30[a];"
        "[1:v]trim=duration=2.50,setpts=PTS-STARTPTS,fps=30[b];"
        "[2:v]trim=start=9.75,setpts=PTS-STARTPTS,fps=30[c];"
        "[a][b][c]concat=n=3:v=1:a=0,format=yuv420p[v]",
        "-map", "[v]", "-t", "17.0", "-an", "-c:v", "libx264", "-preset", "medium", "-crf", "18",
        "-movflags", "+faststart", str(SOURCE / "files-extended-source.mp4"),
    ])

    search_lines = [
        "Откройте раздел «Люди»",
        "Нажмите «Умный поиск»",
        "Введите «предки Анны»",
        "Посмотрите найденных родственников",
    ]
    files_lines = [
        "Откройте меню «Ещё»",
        "Нажмите «Файлы и экспорт»",
        "Здесь можно отправить файл дерева",
        "Откройте «Сохранённые версии»",
        "Выберите копию для восстановления",
    ]
    for prefix, lines in (("search", search_lines), ("files", files_lines)):
        for number, line in enumerate(lines, 1):
            caption(CAPTIONS / f"{prefix}-{number:02}.png", number, line)

    highlights = {
        "search-people.png": ((430, 2140, 650, 2318), "НАЖАТЬ"),
        "search-input.png": ((45, 472, 894, 642), "НАЖАТЬ"),
        "search-results.png": ((48, 830, 1035, 1272), "РЕЗУЛЬТАТ"),
        "files-more.png": ((858, 2140, 1060, 2318), "НАЖАТЬ"),
        "files-menu.png": ((218, 1110, 1025, 1300), "НАЖАТЬ"),
        "files-share.png": ((50, 1190, 535, 1500), "ФАЙЛ"),
        "files-versions.png": ((48, 1680, 898, 1812), "НАЖАТЬ"),
        "files-load.png": ((704, 613, 991, 735), "ВОССТАНОВИТЬ"),
    }
    for name, (box, label) in highlights.items():
        target_overlay(HIGHLIGHTS / name, box, label)

    endcard(ENDCARDS / "search-end.png", "Ветвь найдена", "Теперь попробуйте свой запрос")
    endcard(ENDCARDS / "files-end.png", "Копии под рукой", "Сохраните файл дерева отдельно")

    compose(
        "02-smart-search-variant-b.mp4", "search-tutorial-v2.mp4", 15.0, 18.75, 21.5, 25.0,
        [
            (0.0, 5.6, "search-01.png"),
            (5.6, 10.0, "search-02.png"),
            (10.0, 17.2, "search-03.png"),
            (17.2, 21.5, "search-04.png"),
        ],
        [
            (0.55, 4.65, "search-people.png"),
            (6.0, 9.25, "search-input.png"),
            (17.5, 20.65, "search-results.png"),
        ],
        "search-end.png",
    )
    compose(
        "05-backup-variant-b.mp4", "files-extended-source.mp4", 0.0, 17.0, 23.0, 26.5,
        [
            (0.0, 3.0, "files-01.png"),
            (3.0, 7.0, "files-02.png"),
            (7.0, 10.0, "files-03.png"),
            (10.0, 13.5, "files-04.png"),
            (13.5, 23.0, "files-05.png"),
        ],
        [
            (0.55, 2.75, "files-more.png"),
            (3.2, 6.55, "files-menu.png"),
            (7.2, 9.55, "files-share.png"),
            (10.2, 13.15, "files-versions.png"),
            (14.0, 22.2, "files-load.png"),
        ],
        "files-end.png",
    )

    for title, video in (("search", "02-smart-search-variant-b.mp4"), ("files", "05-backup-variant-b.mp4")):
        run_ffmpeg([
            "-i", str(PUBLISH / video), "-vf", "fps=1/2,scale=216:480,tile=5x3", "-frames:v", "1",
            str(PREVIEW / f"{title}-contact-sheet.png"),
        ])

    archive(PACKAGE / "variant-b-source.zip", [(SOURCE, "source"), (Path(__file__), "build_channel_variant_b.py"), (PACKAGE / "EDITING_NOTES.md", "EDITING_NOTES.md")])
    archive(PACKAGE / "variant-b-overlays.zip", [(OVERLAYS, "overlays")])
    archive(PACKAGE / "variant-b-ready-to-post.zip", [(PUBLISH, "publish")])


if __name__ == "__main__":
    main()
