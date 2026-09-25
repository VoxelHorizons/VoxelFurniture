package org.voxelhorizons.furniture.model;

public final class FurnitureIdleAnimationDefinition {
    private final double bobAmplitude;
    private final int bobPeriodTicks;
    private final float spinDegreesPerTick;

    public FurnitureIdleAnimationDefinition(double bobAmplitude, int bobPeriodTicks, float spinDegreesPerTick) {
        this.bobAmplitude = bobAmplitude;
        this.bobPeriodTicks = bobPeriodTicks;
        this.spinDegreesPerTick = spinDegreesPerTick;
    }

    public double bobAmplitude() { return bobAmplitude; }
    public int bobPeriodTicks() { return bobPeriodTicks; }
    public float spinDegreesPerTick() { return spinDegreesPerTick; }
    public boolean enabled() {
        return bobAmplitude != 0.0D || spinDegreesPerTick != 0.0F;
    }
}
