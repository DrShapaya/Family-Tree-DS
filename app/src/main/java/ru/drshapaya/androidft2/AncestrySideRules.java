package ru.drshapaya.androidft2;

import java.util.Map;

/** Shared geometric rules for outward ancestry and collateral relatives. */
final class AncestrySideRules {
    private static final float COLLATERAL_GAP = TreeLayoutEngine.GRID * 2f;

    private AncestrySideRules() {}

    static float requiredLeftShift(
        BranchContour contour,
        LayoutSnapshot.Position member
    ) {
        float shift = 0f;
        if (contour == null || member == null) return shift;
        int memberRow = Math.round(member.y / TreeLayoutEngine.GRID);
        for (Map.Entry<Integer, BranchContour.Span> entry : contour.rows.entrySet()) {
            float allowedRight = entry.getKey() >= memberRow
                ? member.x - COLLATERAL_GAP
                : member.x + TreeLayoutEngine.CARD_W;
            shift = Math.min(shift, allowedRight - entry.getValue().right);
        }
        return shift;
    }

    static float requiredRightShift(
        BranchContour contour,
        LayoutSnapshot.Position member
    ) {
        float shift = 0f;
        if (contour == null || member == null) return shift;
        int memberRow = Math.round(member.y / TreeLayoutEngine.GRID);
        for (Map.Entry<Integer, BranchContour.Span> entry : contour.rows.entrySet()) {
            float allowedLeft = entry.getKey() >= memberRow
                ? member.x + TreeLayoutEngine.CARD_W + COLLATERAL_GAP
                : member.x;
            shift = Math.max(shift, allowedLeft - entry.getValue().left);
        }
        return shift;
    }

    static double leftError(
        BranchContour contour,
        LayoutSnapshot.Position member
    ) {
        return Math.max(0f, -requiredLeftShift(contour, member));
    }

    static double rightError(
        BranchContour contour,
        LayoutSnapshot.Position member
    ) {
        return Math.max(0f, requiredRightShift(contour, member));
    }
}
