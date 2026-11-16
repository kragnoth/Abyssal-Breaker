package net.kragnoth.abyssal.core;

/**
 * Everything the simulation wants the player to see or hear but does not itself track.
 * The desktop front-end turns these into particles and synthesised sound; tests use {@link #NONE}.
 */
public interface Fx {
    enum Sfx { HIT, HIT_HARD, CRUNCH, PADDLE, WALL, ORE, ORE_HI, POD, BOOM, HULL, DROP, CLAMP, LAUNCH, ARC, LASER, SLAM, CLANG, CATCH, WARN, GROWL, DEVOUR, BUY, DENY, EVOLVE, TICK }

    /** {@code pitch} is a chain index for HIT/HIT_HARD (higher = higher pitch), ignored otherwise. */
    default void sound(Sfx s, int pitch) {}
    /** Radial spark burst. */
    default void burst(float x, float y, int n, float r, float g, float b, float speed, float life) {}
    /** A single spark with an explicit velocity. */
    default void spark(float x, float y, float z, float vx, float vy, float r, float g, float b, float life) {}
    /** Tumbling rock chunks. */
    default void debris(float x, float y, int color, int n) {}
    /** A short-lived point light. */
    default void flash(float x, float y, float r, float g, float b, float radius, float life) {}
    /** Centre-screen announcement. */
    default void message(String text, int color, float seconds) {}

    Fx NONE = new Fx() {};
}
