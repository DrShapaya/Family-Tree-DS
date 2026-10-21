#!/usr/bin/env python3
"""Build Telegram-ready visual assets from AndroidFT emulator captures."""

from pathlib import Path
from textwrap import wrap

from PIL import Image, ImageDraw, ImageFont, ImageFilter


ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "docs" / "marketing" / "channel" / "2026-10"
RAW = BASE / "raw"
FINAL = BASE / "final"
OVERLAYS = BASE / "overlays"
FONTS = ROOT / "app" / "src" / "main" / "assets" / "fonts"

W, H = 1080, 1350
TEAL = "#137568"
DEEP = "#12463F"
MINT = "#DDF4EC"
CREAM = "#F7F4EA"
WHITE = "#FFFFFF"
TEXT = "#17332F"
MUTED = "#58736E"


def font(size: int, bold: bool = False):
    name = "NotoSans-Bold.ttf" if bold else "NotoSans-Regular.ttf"
    return ImageFont.truetype(str(FONTS / name), size)


def rounded(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def fit_lines(draw, text, face, max_width):
    words = text.split()
    lines, current = [], ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if draw.textbbox((0, 0), candidate, font=face)[2] <= max_width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def gradient(size, top="#F8FBF7", bottom="#DDEFE7"):
    image = Image.new("RGB", size)
    draw = ImageDraw.Draw(image)
    a = tuple(int(top[i : i + 2], 16) for i in (1, 3, 5))
    b = tuple(int(bottom[i : i + 2], 16) for i in (1, 3, 5))
    for y in range(size[1]):
        t = y / max(1, size[1] - 1)
        color = tuple(round(a[j] * (1 - t) + b[j] * t) for j in range(3))
        draw.line((0, y, size[0], y), fill=color)
    return image


def add_brand(draw, y=1280, dark=False):
    color = WHITE if dark else TEAL
    draw.ellipse((64, y - 2, 82, y + 16), fill=color)
    draw.text((94, y - 12), "Family Tree DS", font=font(25, True), fill=color)


def phone_frame(source: Path, width=480):
    shot = Image.open(source).convert("RGB")
    height = round(shot.height * width / shot.width)
    shot = shot.resize((width, height), Image.Resampling.LANCZOS)
    frame = Image.new("RGBA", (width + 28, height + 28), (0, 0, 0, 0))
    shadow = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((10, 12, width + 22, height + 24), 35, fill=(13, 62, 55, 55))
    shadow = shadow.filter(ImageFilter.GaussianBlur(9))
    frame.alpha_composite(shadow)
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.width, shot.height), 28, fill=255)
    clipped = Image.new("RGBA", shot.size)
    clipped.paste(shot, mask=mask)
    frame.alpha_composite(clipped, (4, 4))
    ImageDraw.Draw(frame).rounded_rectangle((4, 4, width + 4, height + 4), 28, outline=(255, 255, 255, 190), width=4)
    return frame


def make_carousel(name, step, title, subtitle, source):
    canvas = gradient((W, H))
    draw = ImageDraw.Draw(canvas)
    draw.ellipse((-150, 920, 410, 1480), fill="#C3E6D9")
    rounded(draw, (54, 58, 230, 112), 27, TEAL)
    draw.text((79, 66), f"ШАГ {step}", font=font(28, True), fill=WHITE)

    title_font = font(55, True)
    y = 162
    for line in fit_lines(draw, title, title_font, 470):
        draw.text((60, y), line, font=title_font, fill=TEXT)
        y += 70
    y += 18
    body_font = font(30)
    for line in fit_lines(draw, subtitle, body_font, 445):
        draw.text((62, y), line, font=body_font, fill=MUTED)
        y += 45

    phone = phone_frame(RAW / source, 480)
    canvas.paste(phone, (552, 132), phone)
    add_brand(draw)
    canvas.save(FINAL / name, quality=95)


def make_questions():
    canvas = gradient((W, H), "#FBF8F0", "#E6F3EC")
    draw = ImageDraw.Draw(canvas)
    rounded(draw, (54, 48, 390, 101), 26, TEAL)
    draw.text((78, 56), "СЕМЕЙНЫЙ РАЗГОВОР", font=font(25, True), fill=WHITE)
    draw.text((58, 145), "10 вопросов", font=font(68, True), fill=DEEP)
    draw.text((58, 225), "которые стоит задать старшим", font=font(42, True), fill=TEAL)

    questions = [
        "Где и когда вы родились?",
        "Какими были ваши родители?",
        "Где жила ваша семья?",
        "Кем работали родные?",
        "Какие семейные прозвища вы помните?",
        "Какие праздники и традиции были важны?",
        "Какие истории рассказывали бабушки и дедушки?",
        "Какие события сильнее всего изменили семью?",
        "Какие фотографии и документы сохранились?",
        "Что вы хотите передать следующим поколениям?",
    ]
    cols = [(58, 350), (558, 350)]
    for col in range(2):
        x, y = cols[col]
        for idx in range(col * 5, col * 5 + 5):
            rounded(draw, (x, y, x + 440, y + 151), 25, WHITE)
            draw.ellipse((x + 20, y + 22, x + 72, y + 74), fill=TEAL)
            n = str(idx + 1)
            bbox = draw.textbbox((0, 0), n, font=font(25, True))
            draw.text((x + 46 - (bbox[2] - bbox[0]) / 2, y + 29), n, font=font(25, True), fill=WHITE)
            qfont = font(25, True)
            qy = y + 20
            for line in fit_lines(draw, questions[idx], qfont, 335):
                draw.text((x + 90, qy), line, font=qfont, fill=TEXT)
                qy += 34
            y += 166

    rounded(draw, (58, 1193, 1022, 1260), 32, MINT)
    draw.text((84, 1204), "Сохраните список перед разговором с родными", font=font(29, True), fill=DEEP)
    add_brand(draw, 1301)
    canvas.save(FINAL / "03-ten-questions.png", quality=95)


def make_workshop():
    canvas = gradient((W, H), "#173D38", "#0C685B")
    draw = ImageDraw.Draw(canvas)
    draw.text((58, 60), "ДНЕВНИК РАЗРАБОТКИ", font=font(27, True), fill="#A5E7D2")
    draw.text((58, 116), "Мастерская", font=font(68, True), fill=WHITE)
    subtitle_font = font(31)
    sy = 205
    for line in fit_lines(draw, "Оформляйте дерево в своём стиле", subtitle_font, 485):
        draw.text((58, sy), line, font=subtitle_font, fill="#D8F5EB")
        sy += 43
    phone = phone_frame(RAW / "workshop.png", 430)
    canvas.paste(phone, (594, 205), phone)
    features = ["Темы карточек", "Фоны и линии", "Рамки для фото", "Экспорт для печати"]
    y = 390
    for item in features:
        rounded(draw, (58, y, 528, y + 92), 28, "#E7F6F0")
        draw.ellipse((84, y + 30, 112, y + 58), fill="#8FE2C8")
        draw.text((136, y + 23), item, font=font(31, True), fill=DEEP)
        y += 112
    rounded(draw, (58, 944, 528, 1118), 30, "#E6F8F1")
    draw.text((86, 974), "Что показать\nв следующем ролике?", font=font(34, True), fill=DEEP, spacing=8)
    add_brand(draw, 1287, dark=True)
    canvas.save(FINAL / "06-workshop-diary.png", quality=95)


def overlay(name, title, subtitle, position="top"):
    canvas = Image.new("RGBA", (1080, 2400), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    y = 105 if position == "top" else 1830
    height = 320 if position == "top" else 300
    rounded(draw, (42, y, 1038, y + height), 42, (16, 62, 56, 230))
    draw.ellipse((76, y + 55, 108, y + 87), fill="#8FE2C8")
    draw.text((128, y + 40), "FAMILY TREE DS", font=font(26, True), fill="#AEEBD8")
    title_font = font(50, True)
    ty = y + 100
    for line in fit_lines(draw, title, title_font, 870):
        draw.text((76, ty), line, font=title_font, fill=WHITE)
        ty += 65
    draw.text((76, y + height - 67), subtitle, font=font(29), fill="#D8F5EB")
    canvas.save(OVERLAYS / name)


def tutorial_caption(name, step, title, note):
    """Create a readable lower-third for a vertical screen recording."""
    canvas = Image.new("RGBA", (1080, 2400), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    y = 1760
    rounded(draw, (42, y, 1038, y + 350), 44, (13, 60, 54, 238))
    rounded(draw, (76, y + 46, 240, y + 102), 28, (143, 226, 200, 255))
    draw.text((105, y + 56), f"ШАГ {step}", font=font(25, True), fill=DEEP)

    title_face = font(47, True)
    ty = y + 126
    for line in fit_lines(draw, title, title_face, 900):
        draw.text((76, ty), line, font=title_face, fill=WHITE)
        ty += 61
    draw.text((76, y + 292), note, font=font(27), fill="#CDEDE3")
    canvas.save(OVERLAYS / name)


def tutorial_outro(name, title, subtitle, footer):
    """Create a calm full-screen ending card for a tutorial."""
    canvas = gradient((1080, 2400), "#173F39", "#0C7465").convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    draw.ellipse((390, 450, 690, 750), fill="#DDF4EC")
    draw.line((465, 602, 535, 672), fill=TEAL, width=28)
    draw.line((530, 672, 630, 535), fill=TEAL, width=28)
    draw.text((422, 825), "ГОТОВО", font=font(36, True), fill="#A9E8D5")

    title_face = font(68, True)
    ty = 930
    for line in fit_lines(draw, title, title_face, 900):
        bbox = draw.textbbox((0, 0), line, font=title_face)
        x = (1080 - (bbox[2] - bbox[0])) / 2
        draw.text((x, ty), line, font=title_face, fill=WHITE)
        ty += 86

    subtitle_face = font(36)
    ty += 34
    for line in fit_lines(draw, subtitle, subtitle_face, 850):
        bbox = draw.textbbox((0, 0), line, font=subtitle_face)
        x = (1080 - (bbox[2] - bbox[0])) / 2
        draw.text((x, ty), line, font=subtitle_face, fill="#D9F5EC")
        ty += 52

    rounded(draw, (120, 1570, 960, 1690), 50, "#E4F7F1")
    footer_face = font(31, True)
    bbox = draw.textbbox((0, 0), footer, font=footer_face)
    draw.text(((1080 - (bbox[2] - bbox[0])) / 2, 1607), footer, font=footer_face, fill=DEEP)
    draw.ellipse((398, 2205, 418, 2225), fill=WHITE)
    draw.text((434, 2189), "Family Tree DS", font=font(31, True), fill=WHITE)
    canvas.save(OVERLAYS / name)


def video_cover(name, title, subtitle, source):
    size = (1080, 1920)
    src = Image.open(RAW / source).convert("RGB")
    blurred = src.resize(size, Image.Resampling.LANCZOS).filter(ImageFilter.GaussianBlur(24))
    tint = Image.new("RGBA", size, (7, 46, 41, 155))
    canvas = Image.alpha_composite(blurred.convert("RGBA"), tint)
    draw = ImageDraw.Draw(canvas)
    rounded(draw, (54, 56, 390, 112), 28, (19, 117, 104, 240))
    draw.text((82, 64), "МИНИ-ИНСТРУКЦИЯ", font=font(25, True), fill=WHITE)
    y = 168
    for line in fit_lines(draw, title, font(66, True), 940):
        draw.text((58, y), line, font=font(66, True), fill=WHITE)
        y += 82
    draw.text((60, y + 10), subtitle, font=font(34), fill="#D7F4EA")
    phone = phone_frame(RAW / source, 630)
    canvas.paste(phone, (225, 645), phone)
    add_brand(draw, 565, dark=True)
    canvas.convert("RGB").save(FINAL / name, quality=94)


def main():
    FINAL.mkdir(parents=True, exist_ok=True)
    OVERLAYS.mkdir(parents=True, exist_ok=True)

    make_carousel(
        "01-start-1.png", "1", "Начните с себя",
        "Не нужно заранее знать всю историю семьи. Добавьте то, что помните сейчас.",
        "tree-imported.png",
    )
    make_carousel(
        "01-start-2.png", "2", "Добавьте родителей",
        "Запишите известные даты, места и прикрепите фотографию. Остальное можно дополнить позже.",
        "person-profile.png",
    )
    make_carousel(
        "01-start-3.png", "3", "Поговорите с близкими",
        "Один разговор часто даёт больше новых ветвей, чем несколько часов поиска в архиве.",
        "people.png",
    )
    make_questions()
    make_workshop()

    overlay("search-intro.png", "Умный поиск по дереву", "Пример запроса: «предки Анны»")
    overlay("search-outro.png", "Готово — нужная ветвь найдена", "Пишите запрос обычными словами", "bottom")
    overlay("files-intro.png", "Как сохранить семейное дерево", "Откройте «Ещё» → «Файлы и экспорт»")
    overlay("files-outro.png", "Последние версии всегда под рукой", "Копию можно открыть или восстановить", "bottom")

    search_steps = [
        ("search-step-1.png", 1, "Открываем семейное дерево", "Начинаем с главного экрана приложения"),
        ("search-step-2.png", 2, "Заходим в раздел «Люди»", "Здесь находится список всех персон"),
        ("search-step-3.png", 3, "Нажимаем на строку поиска", "Запрос можно написать обычными словами"),
        ("search-step-4.png", 4, "Вводим: «предки Анны»", "Приложение распознаёт имя и родственную связь"),
        ("search-step-5.png", 5, "Проверяем результат", "На экране показаны люди из нужной ветви"),
    ]
    for args in search_steps:
        tutorial_caption(*args)
    tutorial_outro(
        "search-outro-v2.png", "Нужная ветвь найдена",
        "Используйте обычные фразы, чтобы быстрее находить людей в дереве.",
        "Попробуйте свой запрос",
    )

    files_steps = [
        ("files-step-1.png", 1, "Открываем семейное дерево", "Начинаем с главного экрана приложения"),
        ("files-step-2.png", 2, "Переходим в раздел «Ещё»", "Кнопка находится в нижнем меню"),
        ("files-step-3.png", 3, "Выбираем «Файлы и экспорт»", "Здесь собраны сохранение, импорт и отправка"),
        ("files-step-4.png", 4, "Открываем «Сохранённые версии»", "Приложение хранит последние локальные копии"),
        ("files-step-5.png", 5, "Выбираем нужную копию", "Для восстановления нажмите «Загрузить»"),
    ]
    for args in files_steps:
        tutorial_caption(*args)
    tutorial_outro(
        "files-outro-v2.png", "Копии дерева под рукой",
        "После важных изменений дополнительно сохраните файл дерева в надёжном месте.",
        "Сделайте свежую копию сегодня",
    )

    video_cover(
        "02-smart-search-cover.jpg", "Найдите нужную ветвь за несколько секунд",
        "Умный поиск понимает обычные фразы", "people.png",
    )
    video_cover(
        "05-backup-cover.jpg", "Сохраните семейное дерево надёжно",
        "Файл дерева и последние версии", "files.png",
    )


if __name__ == "__main__":
    main()
