package ru.drshapaya.androidft2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.LruCache;

import java.util.Locale;

/**
 * Renderer independent from the live View. It is safe to use on the export
 * worker and keeps PDF text/lines vector while rasterizing only photos.
 */
final class TreeDocumentRenderer {
    private final TreeState state;
    private final TreeMediaStore mediaStore;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path clip = new Path();
    private final Rect source = new Rect();
    private final RectF card = new RectF();
    private final LruCache<String, Bitmap> photos = new LruCache<String, Bitmap>(12 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return Math.max(1, bitmap.getAllocationByteCount() / 1024);
        }
    };
    private final float cardWidth;
    private final float cardHeight;

    TreeDocumentRenderer(TreeState state, TreeMediaStore mediaStore) {
        this.state = state == null ? new TreeState() : state;
        this.mediaStore = mediaStore;
        cardWidth = this.state.compactCards
            ? TreeLayoutEngine.GRID * 6f
            : TreeLayoutEngine.CARD_W;
        cardHeight = this.state.compactCards
            ? TreeLayoutEngine.GRID * 2.5f
            : TreeLayoutEngine.CARD_H;
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    RectF bounds() {
        RectF bounds = new RectF(
            Float.MAX_VALUE,
            Float.MAX_VALUE,
            -Float.MAX_VALUE,
            -Float.MAX_VALUE);
        for (Person person : state.people.values()) {
            if (person == null || !Float.isFinite(person.x) || !Float.isFinite(person.y)) continue;
            bounds.left = Math.min(bounds.left, person.x);
            bounds.top = Math.min(bounds.top, person.y);
            bounds.right = Math.max(bounds.right, person.x + cardWidth);
            bounds.bottom = Math.max(bounds.bottom, person.y + cardHeight);
        }
        if (bounds.left == Float.MAX_VALUE) bounds.set(0f, 0f, cardWidth, cardHeight);
        if (state.guidesVisible) {
            for (Guide guide : state.guides) {
                if (guide == null || !Float.isFinite(guide.position)) continue;
                if ("v".equals(guide.axis)) {
                    bounds.left = Math.min(bounds.left, guide.position);
                    bounds.right = Math.max(bounds.right, guide.position);
                } else {
                    bounds.top = Math.min(bounds.top, guide.position);
                    bounds.bottom = Math.max(bounds.bottom, guide.position);
                }
            }
        }
        bounds.inset(-48f, -48f);
        return bounds;
    }

    void render(
        Canvas canvas,
        RectF worldRegion,
        RectF target,
        float pixelsPerWorld,
        boolean monochrome
    ) {
        if (canvas == null || worldRegion == null || target == null) return;
        int checkpoint = canvas.save();
        canvas.clipRect(target);
        canvas.drawColor(Color.WHITE);
        canvas.translate(
            target.left - worldRegion.left * pixelsPerWorld,
            target.top - worldRegion.top * pixelsPerWorld);
        canvas.scale(pixelsPerWorld, pixelsPerWorld);
        drawGuides(canvas, worldRegion, monochrome);
        drawLinks(canvas, worldRegion, monochrome);
        drawCards(canvas, worldRegion, monochrome);
        canvas.restoreToCount(checkpoint);
    }

    void clear() {
        photos.evictAll();
    }

    private void drawGuides(Canvas canvas, RectF visible, boolean monochrome) {
        if (!state.guidesVisible) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        for (Guide guide : state.guides) {
            if (guide == null) continue;
            paint.setColor(monochrome
                ? Color.LTGRAY
                : TreeState.parseColor(guide.color, Color.rgb(47, 125, 117)));
            if ("v".equals(guide.axis)) {
                if (guide.position < visible.left || guide.position > visible.right) continue;
                canvas.drawLine(guide.position, visible.top, guide.position, visible.bottom, paint);
            } else {
                if (guide.position < visible.top || guide.position > visible.bottom) continue;
                canvas.drawLine(visible.left, guide.position, visible.right, guide.position, paint);
            }
        }
    }

    private void drawLinks(Canvas canvas, RectF visible, boolean monochrome) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3f);
        paint.setColor(monochrome ? Color.DKGRAY : Color.rgb(47, 125, 117));
        for (Relation relation : state.links) {
            Person from = state.people.get(relation.from);
            Person to = state.people.get(relation.to);
            if (from == null || to == null) continue;
            float minX = Math.min(from.x, to.x) - cardWidth;
            float minY = Math.min(from.y, to.y) - cardHeight;
            float maxX = Math.max(from.x, to.x) + cardWidth * 2f;
            float maxY = Math.max(from.y, to.y) + cardHeight * 2f;
            if (!RectF.intersects(visible, new RectF(minX, minY, maxX, maxY))) continue;
            buildLinkPath(relation, from, to);
            canvas.drawPath(path, paint);
        }
    }

    private void buildLinkPath(Relation relation, Person from, Person to) {
        path.reset();
        float ax = from.x + cardWidth / 2f;
        float ay = from.y + cardHeight / 2f;
        float bx = to.x + cardWidth / 2f;
        float by = to.y + cardHeight / 2f;
        if ("parent".equals(relation.type)) {
            float startY = from.y + cardHeight;
            float endY = to.y;
            float midY = startY + (endY - startY) / 2f;
            path.moveTo(ax, startY);
            if ("orthogonal".equals(state.parentLineMode)) {
                path.lineTo(ax, midY);
                path.lineTo(bx, midY);
                path.lineTo(bx, endY);
            } else {
                path.cubicTo(ax, midY, bx, midY, bx, endY);
            }
            return;
        }
        float startX = ax < bx ? from.x + cardWidth : from.x;
        float endX = ax < bx ? to.x : to.x + cardWidth;
        float midX = startX + (endX - startX) / 2f;
        path.moveTo(startX, ay);
        path.cubicTo(midX, ay, midX, by, endX, by);
    }

    private void drawCards(Canvas canvas, RectF visible, boolean monochrome) {
        for (Person person : state.people.values()) {
            card.set(person.x, person.y, person.x + cardWidth, person.y + cardHeight);
            if (!RectF.intersects(visible, card)) continue;
            float radius = 8f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(monochrome ? Color.WHITE : person.color);
            canvas.drawRoundRect(card, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(monochrome ? Color.DKGRAY : Color.argb(180, 255, 255, 255));
            canvas.drawRoundRect(card, radius, radius, paint);

            float padding = state.compactCards ? 10f : 14f;
            float avatar = state.compactCards ? 48f : 64f;
            float cx = card.left + padding + avatar / 2f;
            float cy = card.top + padding + avatar / 2f;
            drawAvatar(canvas, person, cx, cy, avatar, monochrome);

            float textLeft = card.left + padding + avatar + 12f;
            float maxTextWidth = Math.max(1f, card.right - textLeft - padding);
            text.setColor(Color.rgb(34, 37, 39));
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            text.setTextSize(17f);
            drawFitted(canvas, displayName(person), textLeft, card.top + padding + 22f, maxTextWidth);

            if (!state.hideCardDetails) {
                text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                text.setTextSize(12f);
                text.setColor(Color.rgb(70, 76, 80));
                String meta = person.place == null ? "" : person.place.trim();
                if (meta.isEmpty()) meta = yearRange(person);
                drawFitted(
                    canvas,
                    meta,
                    card.left + padding,
                    card.bottom - padding,
                    cardWidth - padding * 2f);
            }
        }
    }

    private void drawAvatar(
        Canvas canvas,
        Person person,
        float cx,
        float cy,
        float size,
        boolean monochrome
    ) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawCircle(cx, cy, size / 2f, paint);
        Bitmap bitmap = photo(person);
        if (bitmap != null && !monochrome) {
            int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
            int left = Math.max(0, (bitmap.getWidth() - side) / 2);
            int top = Math.max(0, (bitmap.getHeight() - side) / 2);
            source.set(left, top, left + side, top + side);
            RectF target = new RectF(
                cx - size / 2f,
                cy - size / 2f,
                cx + size / 2f,
                cy + size / 2f);
            int checkpoint = canvas.save();
            clip.reset();
            clip.addCircle(cx, cy, size / 2f, Path.Direction.CW);
            canvas.clipPath(clip);
            canvas.drawBitmap(bitmap, source, target, paint);
            canvas.restoreToCount(checkpoint);
        } else {
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(18f);
            text.setColor(Color.DKGRAY);
            Paint.FontMetrics fm = text.getFontMetrics();
            canvas.drawText(initials(person.name), cx, cy - (fm.ascent + fm.descent) / 2f, text);
            text.setTextAlign(Paint.Align.LEFT);
        }
    }

    private Bitmap photo(Person person) {
        String mediaId = person.photoMediaId == null ? "" : person.photoMediaId;
        if (mediaId.isEmpty() || mediaStore == null) return null;
        Bitmap cached = photos.get(mediaId);
        if (cached != null) return cached;
        Bitmap decoded = mediaStore.decodeBitmap(mediaId, 384);
        if (decoded != null) photos.put(mediaId, decoded);
        return decoded;
    }

    private void drawFitted(Canvas canvas, String value, float x, float baseline, float width) {
        if (value == null || value.trim().isEmpty()) return;
        String textValue = value.trim().replace('\n', ' ');
        float original = text.getTextSize();
        float measured = Math.max(1f, text.measureText(textValue));
        if (measured > width) text.setTextSize(Math.max(7f, original * width / measured));
        canvas.drawText(textValue, x, baseline, text);
        text.setTextSize(original);
    }

    private static String displayName(Person person) {
        return person.name == null || person.name.trim().isEmpty() ? "Без имени" : person.name.trim();
    }

    private static String yearRange(Person person) {
        String born = person.bornYear == null ? "" : person.bornYear.trim();
        String died = person.diedYear == null ? "" : person.diedYear.trim();
        if (!born.isEmpty() && !died.isEmpty()) return born + "–" + died;
        if (!born.isEmpty()) return "Род. " + born;
        if (!died.isEmpty()) return "Ум. " + died;
        return "";
    }

    private static String initials(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) return "?";
        String[] parts = value.split("\\s+");
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1 && !parts[1].isEmpty()
            ? parts[1].substring(0, 1)
            : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }
}
