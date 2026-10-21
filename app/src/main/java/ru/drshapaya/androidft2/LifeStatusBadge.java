package ru.drshapaya.androidft2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import java.util.HashMap;
import java.util.Map;

/** Canvas icons matching the card quality badge; paths are reused between frames. */
final class LifeStatusBadge {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path heart = new Path();
    private final Path flame = new Path();
    private final Path question = new Path();
    private static final LifeStatusAppearance DEFAULT = new LifeStatusAppearance();
    private final Map<String, Path> paths = new HashMap<>();

    LifeStatusBadge() {
        heart.moveTo(32, 55);
        heart.cubicTo(27, 50, 6, 35, 6, 21);
        heart.cubicTo(6, 6, 24, 4, 32, 17);
        heart.cubicTo(40, 4, 58, 6, 58, 21);
        heart.cubicTo(58, 35, 37, 50, 32, 55);
        heart.close();
        flame.moveTo(32, 0);
        flame.cubicTo(35, 7, 44, 13, 40, 20);
        flame.cubicTo(36, 27, 25, 27, 24, 19);
        flame.cubicTo(22, 12, 29, 7, 32, 0);
        flame.close();
        question.moveTo(21, 17);
        question.cubicTo(21, 4, 45, 4, 45, 18);
        question.cubicTo(45, 26, 32, 28, 32, 38);
        paths.put("heart", heart);
        paths.put("leaf", new Shape().m(12,53).c(0,25,18,8,56,6).c(59,36,40,57,20,53)
            .c(27,36,36,26,43,20).c(26,28,17,41,12,53).z().path);
        paths.put("leaf-outline", new Shape().m(12,50).c(1,25,22,14,56,6).c(63,43,42,60,17,52)
            .m(10,60).q(22,34,40,27).path);
        paths.put("petal", new Shape().m(32,54).c(12,38,18,17,32,5).c(46,17,52,38,32,54).z().path);
        paths.put("side-leaf", new Shape().m(31,45).c(8,41,5,20,5,12).c(27,12,38,27,31,45).z().path);
        Path leaves = new Shape().m(27,55).c(8,44,7,18,14,6).c(31,9,37,30,27,55).z()
            .m(31,58).c(28,35,43,19,58,14).c(64,43,49,60,31,58).z().path;
        Path vein = new Shape().m(28,60).c(34,42,40,31,51,24).c(39,40,38,49,36,60).z().path;
        leaves.op(vein,Path.Op.DIFFERENCE); paths.put("leaves",leaves);
        paths.put("lotus", new Shape().m(23,53).c(10,48,6,34,5,21).c(16,23,20,31,21,42).z()
            .m(32,54).c(23,42,22,22,32,8).c(42,22,41,42,32,54).z()
            .m(41,53).c(54,48,58,34,59,21).c(48,23,44,31,43,42).z().path);
        paths.put("infinity", new Shape().m(32,32).c(14,5,2,19,4,33).c(7,53,23,45,32,32)
            .c(49,5,62,19,60,33).c(57,53,41,45,32,32).path);
        paths.put("pulse", new Shape().m(3,34).l(18,34).l(25,9).l(36,55).l(44,29).l(61,29).path);
        paths.put("hourglass", new Shape().m(14,6).l(50,6).q(50,23,32,32).q(50,41,50,58)
            .l(14,58).q(14,41,32,32).q(14,23,14,6).z().path);
        paths.put("sand", new Shape().m(21,15).l(43,15).l(32,27).z().m(21,51).l(43,51).l(32,39).z().path);
        paths.put("urn", new Shape().m(19,6).l(45,6).l(45,12).l(19,12).z().m(18,18)
            .c(5,23,17,44,22,52).l(42,52).c(47,44,59,23,46,18).z().m(20,57).l(44,57).path);
        paths.put("ribbon", new Shape().m(21,6).c(26,1,40,1,44,9).c(53,25,36,40,20,61)
            .l(9,50).c(22,36,42,16,35,12).c(30,9,24,15,28,22).l(55,50).l(44,61)
            .c(31,45,11,25,21,6).z().path);
        paths.put("flower", new Shape().m(15,59).l(15,24).c(15,3,43,0,43,25)
            .m(34,25).c(37,17,50,17,54,29).l(60,38).q(50,48,41,41).l(33,40).q(28,32,34,25).path);
        paths.put("branch", new Shape().m(32,58).l(32,6).m(32,38).l(10,23).l(6,13)
            .m(18,29).l(18,13).m(32,27).l(22,5).m(32,38).l(52,24).l(58,12)
            .m(46,28).l(46,10).m(32,19).l(43,3).m(32,48).l(12,41).m(32,48).l(53,40).path);
        Path moon = new Path();
        // The intersection is a crescent; it stays within the glyph's bounds.
        moon.addCircle(32,32,27,Path.Direction.CW);
        Path cutout = new Path(); cutout.addCircle(46,23,25,Path.Direction.CW);
        moon.op(cutout,Path.Op.DIFFERENCE); paths.put("moon",moon);
        Path headstone = new Path(); headstone.addRoundRect(17,6,47,55,15,15,Path.Direction.CW);
        headstone.addRect(17,31,47,55,Path.Direction.CW);
        Path cross = new Path(); cross.addRect(30,16,34,38,Path.Direction.CW); cross.addRect(24,22,40,26,Path.Direction.CW);
        headstone.op(cross,Path.Op.DIFFERENCE); paths.put("headstone-cross",headstone);
    }

    static float centerY(float top, float height, float scale, boolean qualityVisible) {
        return top + height - 10f * scale - Math.max(1f, 22f * scale) / 2f
            - (qualityVisible ? 28f * scale : 0f);
    }

    static int color(String status) {
        return PersonLifeStatus.LIVING.equals(status) ? Color.rgb(38, 131, 108)
            : PersonLifeStatus.DECEASED.equals(status) ? Color.rgb(77, 94, 118)
            : Color.rgb(128, 138, 151);
    }

    void draw(Canvas canvas, String status, float cx, float cy, float diameter, float scale) {
        draw(canvas,status,DEFAULT,cx,cy,diameter,scale);
    }

    void draw(Canvas canvas, String status, LifeStatusAppearance appearance, float cx, float cy, float diameter, float scale) {
        int background = Color.parseColor(appearance.color(status));
        boolean plain = "plain".equals(appearance.style), soft = "soft".equals(appearance.style);
        paint.setStyle(Paint.Style.FILL);
        if (!plain) {
            paint.setColor(Color.argb(232, 255, 255, 255));
            canvas.drawCircle(cx, cy, diameter / 2f + Math.max(0.35f, 1.8f * scale), paint);
            paint.setColor(soft ? (light(background) ? Color.rgb(77,94,118) : pale(background)) : background);
            canvas.drawCircle(cx, cy, diameter / 2f, paint);
        }
        if (diameter < 7f) return;
        canvas.save();
        float unit = diameter / (plain ? 76f : 112f);
        canvas.translate(cx - 32f * unit,cy - 32f * unit);
        canvas.scale(unit,unit);
        paint.setColor(plain || soft ? background : light(background) ? Color.rgb(36,42,48) : Color.WHITE);
        paint.setStrokeWidth(5); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        glyph(canvas,appearance.icon(status));
        canvas.restore();
    }

    private static int pale(int color) {
        return Color.rgb((Color.red(color)+1020)/5,(Color.green(color)+1020)/5,(Color.blue(color)+1020)/5);
    }

    private static boolean light(int color) { return Color.red(color)*.299f+Color.green(color)*.587f+Color.blue(color)*.114f>=150; }

    private void glyph(Canvas c, String icon) {
        paint.setStyle(Paint.Style.FILL);
        switch (icon) {
            case "heart": case "leaf": case "leaves": case "lotus": case "moon": case "ribbon":
                c.drawPath(paths.get(icon),paint); break;
            case "leaf-outline": case "infinity": case "pulse": stroke(c,icon); break;
            case "sprout":
                c.drawPath(paths.get("side-leaf"),paint);
                c.save(); c.scale(-1,1,32,32); c.drawPath(paths.get("side-leaf"),paint); c.restore();
                paint.setStrokeWidth(5); c.drawLine(32,39,32,60,paint); break;
            case "tree":
                c.drawRect(28,31,36,60,paint);
                paint.setStrokeWidth(4);
                c.drawLine(32,44,15,28,paint); c.drawLine(32,44,49,28,paint);
                c.drawLine(32,53,19,43,paint); c.drawLine(32,53,45,43,paint);
                leaf(c,32,10,0); leaf(c,18,17,-35); leaf(c,46,17,35);
                leaf(c,9,29,-55); leaf(c,55,29,55); leaf(c,19,41,-65); leaf(c,45,41,65);
                leaf(c,30,28,-15); leaf(c,38,32,30); break;
            case "sun": case "sunrise":
                c.save(); if ("sunrise".equals(icon)) c.clipRect(0,0,64,34);
                c.drawCircle(32,32,14,paint); paint.setStyle(Paint.Style.STROKE);
                for(int i=0;i<8;i++) { c.save(); c.rotate(i*45,32,32); c.drawLine(32,5,32,11,paint); c.restore(); }
                c.restore();
                if ("sunrise".equals(icon)) { c.drawLine(6,39,58,39,paint); c.drawLine(11,49,53,49,paint); } break;
            case "candle":
                c.drawPath(flame,paint); c.drawRoundRect(22,31,42,59,3,3,paint);
                paint.setStrokeWidth(4); c.drawLine(16,59,48,59,paint); break;
            case "cross":
                c.drawRoundRect(27,5,37,60,1,1,paint); c.drawRoundRect(12,21,52,31,1,1,paint); break;
            case "headstone": case "headstone-cross":
                if ("headstone-cross".equals(icon)) c.drawPath(paths.get(icon),paint);
                else { c.drawRoundRect(17,6,47,55,15,15,paint); c.drawRect(17,31,47,55,paint); }
                c.drawRoundRect(10,58,54,64,2,2,paint);
                break;
            case "flower": stroke(c,"flower"); paint.setStyle(Paint.Style.FILL); c.drawOval(1,39,15,53,paint); break;
            case "hourglass": stroke(c,"hourglass"); paint.setStyle(Paint.Style.FILL); c.drawPath(paths.get("sand"),paint); break;
            case "urn": c.drawPath(paths.get("urn"),paint); break;
            case "bare-tree": stroke(c,"branch"); paint.setStrokeWidth(7); c.drawLine(32,42,32,59,paint); break;
            case "dash": paint.setStrokeWidth(7); c.drawLine(17,32,47,32,paint); break;
            case "ellipsis": for(int x=16;x<=48;x+=16) c.drawCircle(x,32,5.5f,paint); break;
            default: paint.setStrokeWidth(7); paint.setStyle(Paint.Style.STROKE); c.drawPath(question,paint);
                paint.setStyle(Paint.Style.FILL); c.drawCircle(32,52,4,paint);
        }
    }

    private void stroke(Canvas c,String name) { paint.setStyle(Paint.Style.STROKE); c.drawPath(paths.get(name),paint); }

    private void leaf(Canvas c,float x,float y,float angle) {
        c.save(); c.translate(x-32,y-32); c.rotate(angle,32,32); c.scale(.32f,.26f,32,32);
        c.drawPath(paths.get("petal"),paint); c.restore();
    }

    private static final class Shape {
        final Path path = new Path();
        Shape m(float x,float y) { path.moveTo(x,y); return this; }
        Shape l(float x,float y) { path.lineTo(x,y); return this; }
        Shape q(float x,float y,float a,float b) { path.quadTo(x,y,a,b); return this; }
        Shape c(float x,float y,float a,float b,float d,float e) { path.cubicTo(x,y,a,b,d,e); return this; }
        Shape z() { path.close(); return this; }
    }
}
