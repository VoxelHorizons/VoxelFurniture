package org.voxelhorizons.furniture.model;

/** Visual-only highlight options; never affects furniture interaction or persistence. */
public final class FurnitureHighlightDefinition {
    private final boolean hover;
    private final int rgb;
    private final double range;
    private final boolean lineOfSight;
    private final boolean includeParts;

    public FurnitureHighlightDefinition(boolean hover, int rgb, double range,
                                        boolean lineOfSight, boolean includeParts) {
        this.hover = hover;
        this.rgb = rgb;
        this.range = range;
        this.lineOfSight = lineOfSight;
        this.includeParts = includeParts;
    }

    public boolean hover() { return hover; }
    public int rgb() { return rgb; }
    public double range() { return range; }
    public boolean lineOfSight() { return lineOfSight; }
    public boolean includeParts() { return includeParts; }
}
