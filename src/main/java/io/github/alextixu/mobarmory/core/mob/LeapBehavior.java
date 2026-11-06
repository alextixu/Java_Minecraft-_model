package io.github.alextixu.mobarmory.core.mob;

/**
 * Leap ("衝刺", R4b decision Q5 A): Minecraft's own leap at the target, the one spiders and wolves use, when the
 * target is 2 to 4 blocks away and the mob stands on the ground. Needs a behavior that gives the mob a target
 * (attacking players or neutral, decision O4).
 */
public record LeapBehavior() implements Behavior {
    public static final String NAME = "leap";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean needsTarget() {
        return true;
    }
}
