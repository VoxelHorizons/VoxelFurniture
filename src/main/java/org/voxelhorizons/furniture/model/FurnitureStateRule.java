package org.voxelhorizons.furniture.model;

import org.voxelhorizons.content.ContentID;

/** Four horizontal neighbor bits, in north/east/south/west order. */
public final class FurnitureStateRule {
    private final int required;
    private final int absent;
    private final ContentID model;
    private final float yawOffset;
    private final boolean rotate;

    public FurnitureStateRule(int required, int absent, ContentID model, float yawOffset, boolean rotate) {
        if ((required & absent) != 0) throw new IllegalArgumentException("A neighbor cannot be both required and absent");
        this.required = required;
        this.absent = absent;
        this.model = model;
        this.yawOffset = yawOffset;
        this.rotate = rotate;
    }

    public ContentID model() { return model; }
    public float yawOffset() { return yawOffset; }
    public int specificity() { return Integer.bitCount(required | absent); }

    /** Returns the matched quarter-turn clockwise, or -1. */
    public int match(int neighbors) {
        for (int turns = 0; turns < (rotate ? 4 : 1); turns++) {
            int requiredTurned = rotate(required, turns);
            int absentTurned = rotate(absent, turns);
            if ((neighbors & requiredTurned) == requiredTurned && (neighbors & absentTurned) == 0) return turns;
        }
        return -1;
    }

    private static int rotate(int mask, int turns) {
        return ((mask << turns) | (mask >>> (4 - turns))) & 15;
    }
}
