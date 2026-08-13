package ru.drshapaya.androidft2;

/** Tunable soft-constraint weights. No solver weight should be hard-coded elsewhere. */
final class LayoutWeights {
    final double lineCrossings;
    final double movement;
    final double mainTrunkMovement;
    final double width;
    final double height;
    final double familyCenter;
    final double symmetry;
    final double siblingSpacing;
    final double wrongSide;
    final double emptySpace;
    final double changedOrder;
    final double connectionLength;

    LayoutWeights(
        double lineCrossings,
        double movement,
        double mainTrunkMovement,
        double width,
        double height,
        double familyCenter,
        double symmetry,
        double siblingSpacing,
        double wrongSide,
        double emptySpace,
        double changedOrder,
        double connectionLength
    ) {
        this.lineCrossings = lineCrossings;
        this.movement = movement;
        this.mainTrunkMovement = mainTrunkMovement;
        this.width = width;
        this.height = height;
        this.familyCenter = familyCenter;
        this.symmetry = symmetry;
        this.siblingSpacing = siblingSpacing;
        this.wrongSide = wrongSide;
        this.emptySpace = emptySpace;
        this.changedOrder = changedOrder;
        this.connectionLength = connectionLength;
    }

    static LayoutWeights defaults() {
        return new LayoutWeights(
            1000d,
            0.10d,
            10d,
            0.02d,
            0.01d,
            1d,
            8d,
            2d,
            20d,
            0.00001d,
            25d,
            0.02d);
    }

    /** Full rebuild has no old-coordinate stability cost; compactness becomes meaningful. */
    static LayoutWeights rebuildCompaction() {
        return new LayoutWeights(
            1000d,
            0d,
            0d,
            0.50d,
            0.01d,
            2d,
            8d,
            4d,
            20d,
            0.00001d,
            100d,
            0.02d);
    }
}
