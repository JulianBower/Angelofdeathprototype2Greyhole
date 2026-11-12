package com.angelofdeath.mod.logic;

import com.angelofdeath.mod.item.AngelOfDeathWeaponItem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Server-side logic for a singularity that is being channeled in the player's hand. */
public final class ChannelHandler {
    private static final DustParticleOptions BLACK_DUST = new DustParticleOptions(new Vector3f(0.0F, 0.0F, 0.0F), 1.6F);
    private static final DustParticleOptions WHITE_DUST = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.6F);
    private static final DustParticleOptions GREY_DUST = new DustParticleOptions(new Vector3f(0.53F, 0.53F, 0.57F), 1.8F);

    private static final Map<UUID, FreezeTracker> HELD_FREEZES = new HashMap<>();

    private ChannelHandler() {
    }

    public static void tick(ServerPlayer player) {
        ItemStack use = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
        if (!(use.getItem() instanceof AngelOfDeathWeaponItem)) {
            releaseHeldFreeze(player);
            return;
        }

        int heldTicks = player.getTicksUsingItem();
        int mode = AngelOfDeathWeaponItem.getMode(use);
        int power = AngelOfDeathWeaponItem.getPower(use);
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 focal = player.getEyePosition().add(look.scale(AngelOfDeathWeaponItem.FOCAL_DISTANCE));

        if (mode != AngelOfDeathWeaponItem.MODE_GREY_HOLE) {
            releaseHeldFreeze(player);
        }

        switch (mode) {
            case AngelOfDeathWeaponItem.MODE_WHITE_HOLE -> heldWhiteHole(player, level, focal, power, heldTicks);
            case AngelOfDeathWeaponItem.MODE_GREY_HOLE -> heldGreyHole(player, level, look, focal, heldTicks);
            default -> heldBlackHole(player, level, focal, power, heldTicks);
        }
    }

    // ---------------------------------------------------------------- Black Hole

    private static void heldBlackHole(ServerPlayer player, ServerLevel level, Vec3 focal, int power, int heldTicks) {
        double radius = 8.0D + power * 4.0D;
        boolean damageTick = heldTicks % 10 == 0;
        DamageSource source = SingularityActions.voidSource(level, player, player);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(focal, focal).inflate(radius),
                e -> e != player && e.isAlive() && !e.isSpectator());
        for (LivingEntity target : targets) {
            double distance = target.getBoundingBox().getCenter().distanceTo(focal);
            if (distance > radius) {
                continue;
            }
            double falloff = 1.0D - distance / radius;
            SingularityActions.pull(target, focal, (0.10D + 0.05D * power) * (0.5D + falloff), 2.0D);
            if (damageTick && SingularityActions.canHarm(target)) {
                target.invulnerableTime = 0;
                target.hurt(source, 2.0F * power);
            }
        }

        level.sendParticles(BLACK_DUST, focal.x, focal.y, focal.z, 6, 0.25D, 0.25D, 0.25D, 0.0D);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, focal.x, focal.y, focal.z, 8, 1.2D, 1.2D, 1.2D, 0.15D);
        if (heldTicks % 20 == 1) {
            SingularityActions.playSound(level, focal, SoundEvents.PORTAL_AMBIENT, 0.6F, 0.5F);
        }
    }

    // ---------------------------------------------------------------- White Hole

    private static void heldWhiteHole(ServerPlayer player, ServerLevel level, Vec3 focal, int power, int heldTicks) {
        double radius = 8.0D + power * 4.0D;
        DamageSource source = SingularityActions.voidSource(level, player, player);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(focal, focal).inflate(radius),
                e -> e != player && e.isAlive() && !e.isSpectator());
        for (LivingEntity target : targets) {
            if (target.getBoundingBox().inflate(0.5D).contains(focal) && SingularityActions.canHarm(target)) {
                shred(level, target, source);
                continue;
            }
            double distance = target.getBoundingBox().getCenter().distanceTo(focal);
            if (distance > radius) {
                continue;
            }
            // Force scales inversely with distance: strongest right at the white hole.
            double inverse = Math.max(0.1D, 1.0D - distance / radius);
            SingularityActions.push(target, focal, (0.20D + 0.10D * power) * (0.3D + inverse), 0.04D, 2.0D);
        }

        level.sendParticles(WHITE_DUST, focal.x, focal.y, focal.z, 6, 0.25D, 0.25D, 0.25D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, focal.x, focal.y, focal.z, 6, 0.3D, 0.3D, 0.3D, 0.2D);
        if (heldTicks % 20 == 1) {
            SingularityActions.playSound(level, focal, SoundEvents.BEACON_AMBIENT, 0.8F, 1.6F);
        }
    }

    private static void shred(ServerLevel level, LivingEntity target, DamageSource source) {
        Vec3 center = target.getBoundingBox().getCenter();
        target.invulnerableTime = 0;
        target.hurt(source, Float.MAX_VALUE);
        if (target.isAlive()) {
            target.kill();
        }
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 60, 0.5D, 0.7D, 0.5D, 0.3D);
        level.sendParticles(WHITE_DUST, center.x, center.y, center.z, 40, 0.5D, 0.7D, 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        SingularityActions.playSound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 1.2F);
        SingularityActions.playSound(level, center, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0F, 1.5F);
    }

    // ---------------------------------------------------------------- Grey Hole

    private static void heldGreyHole(ServerPlayer player, ServerLevel level, Vec3 look, Vec3 focal, int heldTicks) {
        int fusion = AngelOfDeathWeaponItem.FUSION_TICKS;
        if (heldTicks < fusion) {
            fusionEffects(level, look, focal, heldTicks, fusion);
            return;
        }

        if (heldTicks == fusion) {
            level.sendParticles(ParticleTypes.FLASH, focal.x, focal.y, focal.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.EXPLOSION, focal.x, focal.y, focal.z, 3, 0.4D, 0.4D, 0.4D, 0.0D);
            SingularityActions.playSound(level, focal, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.8F);
            SingularityActions.playSound(level, focal, SoundEvents.END_PORTAL_SPAWN, 1.0F, 1.6F);
        }

        // Spatial stasis: living entities and projectiles in front of the caster hang in mid-air.
        FreezeTracker tracker = HELD_FREEZES.computeIfAbsent(player.getUUID(), id -> new FreezeTracker());
        Set<UUID> keep = new HashSet<>();
        AABB box = new AABB(player.getEyePosition(), player.getEyePosition()).inflate(FreezeRules.HELD_RANGE);
        List<Entity> candidates = level.getEntitiesOfClass(Entity.class, box,
                e -> e != player && !e.isSpectator() && !e.isPassenger()
                        && (e instanceof LivingEntity || e instanceof Projectile)
                        && FreezeRules.inHeldCone(player, e));
        for (Entity entity : candidates) {
            tracker.freeze(entity);
            keep.add(entity.getUUID());
        }
        tracker.keepOnly(level, keep);

        level.sendParticles(GREY_DUST, focal.x, focal.y, focal.z, 4, 0.3D, 0.3D, 0.3D, 0.0D);
        level.sendParticles(ParticleTypes.ASH, focal.x, focal.y, focal.z, 6, 0.8D, 0.8D, 0.8D, 0.02D);
        if (heldTicks % 20 == 0) {
            SingularityActions.playSound(level, focal, SoundEvents.BEACON_AMBIENT, 0.8F, 0.7F);
        }
    }

    private static void fusionEffects(ServerLevel level, Vec3 look, Vec3 focal, int heldTicks, int fusion) {
        double progress = heldTicks / (double) fusion;
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 up = right.cross(look).normalize();
        double separation = 1.6D * Math.pow(1.0D - progress, 1.4D) + 0.05D;
        double angle = 6.0D * Math.PI * progress * progress;
        Vec3 offset = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).scale(separation);
        Vec3 blackPos = focal.subtract(offset);
        Vec3 whitePos = focal.add(offset);

        level.sendParticles(BLACK_DUST, blackPos.x, blackPos.y, blackPos.z, 5, 0.08D, 0.08D, 0.08D, 0.0D);
        level.sendParticles(WHITE_DUST, whitePos.x, whitePos.y, whitePos.z, 5, 0.08D, 0.08D, 0.08D, 0.0D);

        // Converging trails from each orb toward the focal point.
        Vec3 toFocalFromBlack = focal.subtract(blackPos).scale(0.25D);
        Vec3 toFocalFromWhite = focal.subtract(whitePos).scale(0.25D);
        level.sendParticles(ParticleTypes.SQUID_INK, blackPos.x, blackPos.y, blackPos.z, 0,
                toFocalFromBlack.x, toFocalFromBlack.y, toFocalFromBlack.z, 1.0D);
        level.sendParticles(ParticleTypes.END_ROD, whitePos.x, whitePos.y, whitePos.z, 0,
                toFocalFromWhite.x, toFocalFromWhite.y, toFocalFromWhite.z, 1.0D);

        // Rising beacon / warden hum.
        if (heldTicks == 1) {
            SingularityActions.playSound(level, focal, SoundEvents.BEACON_AMBIENT, 1.5F, 0.6F);
        } else if (heldTicks == 10) {
            SingularityActions.playSound(level, focal, SoundEvents.BEACON_AMBIENT, 1.5F, 0.9F);
            SingularityActions.playSound(level, focal, SoundEvents.WARDEN_HEARTBEAT, 1.5F, 0.8F);
        } else if (heldTicks == 20) {
            SingularityActions.playSound(level, focal, SoundEvents.BEACON_AMBIENT, 1.5F, 1.3F);
            SingularityActions.playSound(level, focal, SoundEvents.WARDEN_HEARTBEAT, 1.5F, 1.2F);
        }
    }

    // ---------------------------------------------------------------- Freeze cleanup

    public static void releaseHeldFreeze(ServerPlayer player) {
        FreezeTracker tracker = HELD_FREEZES.remove(player.getUUID());
        if (tracker == null) {
            return;
        }
        for (ServerLevel level : player.server.getAllLevels()) {
            tracker.releaseAll(level);
        }
    }

    public static void releaseAll(MinecraftServer server) {
        for (FreezeTracker tracker : HELD_FREEZES.values()) {
            for (ServerLevel level : server.getAllLevels()) {
                tracker.releaseAll(level);
            }
        }
        HELD_FREEZES.clear();
    }
}
