package com.angelofdeath.mod.logic;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Geometry shared by server and client for the held Grey Hole stasis field. */
public final class FreezeRules {
    public static final double HELD_RANGE = 15.0D;
    public static final double HELD_MIN_DOT = 0.3D;
    public static final double GLOBAL_RADIUS = 75.0D;

    private FreezeRules() {
    }

    public static boolean inHeldCone(Entity caster, Entity target) {
        Vec3 eye = caster.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
        double distance = to.length();
        if (distance > HELD_RANGE) {
            return false;
        }
        if (distance < 2.0D) {
            return true;
        }
        return to.scale(1.0D / distance).dot(caster.getLookAngle()) >= HELD_MIN_DOT;
    }
}
