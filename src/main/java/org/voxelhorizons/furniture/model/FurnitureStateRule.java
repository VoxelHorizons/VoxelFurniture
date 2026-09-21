package org.voxelhorizons.furniture.model;

import org.voxelhorizons.content.ContentID;

/** Four horizontal neighbor bits, in north/east/south/west order. */
public final class FurnitureStateRule {
    private final int required;
    private final int absent;
    private final ContentID model;
    private final float yawOffset;
    private final boolean rotate;
    private final boolean relative;
    private final NeighborFacing neighborFacing;

    public FurnitureStateRule(int required, int absent, ContentID model, float yawOffset, boolean rotate) {
        this(required, absent, model, yawOffset, rotate, false, NeighborFacing.SAME);
    }

    public FurnitureStateRule(int required, int absent, ContentID model, float yawOffset,
                              boolean rotate, boolean relative) {
        this(required, absent, model, yawOffset, rotate, relative, NeighborFacing.SAME);
    }

    public FurnitureStateRule(int required, int absent, ContentID model, float yawOffset,
                              boolean rotate, boolean relative, boolean alignedOnly) {
        if ((required & absent) != 0) throw new IllegalArgumentException("A neighbor cannot be both required and absent");
        this.required = required;
        this.absent = absent;
        this.model = model;
        this.yawOffset = yawOffset;
        this.rotate = rotate;
        this.relative = relative;
        this.neighborFacing = alignedOnly ? NeighborFacing.SAME : NeighborFacing.ANY;
    }

    public ContentID model() { return model; }
    public float yawOffset() { return yawOffset; }
    public boolean relative() { return relative; }
    public boolean alignedOnly() { return neighborFacing == NeighborFacing.SAME; }
    public NeighborFacing neighborFacing() { return neighborFacing; }
    public FurnitureStateRule(int required, int absent, ContentID model, float yawOffset,
                              boolean rotate, boolean relative, NeighborFacing neighborFacing) {
        if ((required & absent) != 0) throw new IllegalArgumentException("A neighbor cannot be both required and absent");
        this.required = required;
        this.absent = absent;
        this.model = model;
        this.yawOffset = yawOffset;
        this.rotate = rotate;
        this.relative = relative;
        this.neighborFacing = neighborFacing == null ? NeighborFacing.SAME : neighborFacing;
    }

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

    public enum NeighborFacing {
        ANY, SAME, PERPENDICULAR, CLOCKWISE, COUNTERCLOCKWISE, OPPOSITE;

        public static NeighborFacing parse(Object raw, boolean alignedOnly) {
            if (raw == null) return alignedOnly ? SAME : ANY;
            String value = String.valueOf(raw).trim().toUpperCase(java.util.Locale.ROOT);
            if ("ALIGNED".equals(value)) value = "SAME";
            try { return valueOf(value); }
            catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("neighbor_facing must be ANY, SAME, PERPENDICULAR, CLOCKWISE, COUNTERCLOCKWISE or OPPOSITE");
            }
        }
    }

    static int rotate(int mask, int turns) {
        return ((mask << turns) | (mask >>> (4 - turns))) & 15;
    }
}
