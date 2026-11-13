package com.angelofdeath.mod.logic;

import com.angelofdeath.mod.entity.SingularityEntity;
import com.angelofdeath.mod.item.AngelOfDeathWeaponItem;
import com.angelofdeath.mod.registry.ModDataComponents;
import com.angelofdeath.mod.registry.ModEntities;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

public final class SingularityActions {
    /** Placement reach in blocks. */
    public static final double REACH = 300.0D;
    public static final int PLACE_COOLDOWN_TICKS = 40;
    private static final double LOAD_CHECK_STEP = 4.0D;
    private static final double UNLOADED_MARGIN = 16.0D;

    private SingularityActions() {
    }

    /** Where a placed singularity goes, and the living entity it latches onto (if the ray hit one). */
    public record Target(Vec3 pos, @Nullable LivingEntity entity) {
    }

    public static DamageSource voidSource(ServerLevel level, @Nullable Entity direct, @Nullable Entity causing) {
        return level.damageSources().source(DamageTypes.FELL_OUT_OF_WORLD, direct, causing);
    }

    public static boolean canHarm(LivingEntity entity) {
        return !(entity instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    public static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Pulls an entity toward a focus point, never overshooting it. */
    public static void pull(Entity entity, Vec3 focus, double strength, double maxSpeed) {
        Vec3 to = focus.subtract(entity.getBoundingBox().getCenter());
        double distance = to.length();
        if (distance < 1.0E-4D) {
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.3D));
            entity.hurtMarked = true;
            return;
        }
        double step = Math.min(strength, distance);
        Vec3 velocity = entity.getDeltaMovement().scale(0.55D).add(to.scale(step / distance));
        if (velocity.lengthSqr() > maxSpeed * maxSpeed) {
            velocity = velocity.scale(maxSpeed / velocity.length());
        }
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
        entity.fallDistance = 0.0F;
    }

    /** Pushes an entity away from a source point. */
    public static void push(Entity entity, Vec3 source, double strength, double lift, double maxSpeed) {
        Vec3 away = entity.getBoundingBox().getCenter().subtract(source);
        double distance = away.length();
        Vec3 direction = distance < 1.0E-4D ? new Vec3(0.0D, 1.0D, 0.0D) : away.scale(1.0D / distance);
        Vec3 velocity = entity.getDeltaMovement().scale(0.6D).add(direction.scale(strength)).add(0.0D, lift, 0.0D);
        if (velocity.lengthSqr() > maxSpeed * maxSpeed) {
            velocity = velocity.scale(maxSpeed / velocity.length());
        }
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
    }

    @Nullable
    private static LivingEntity resolveLiving(Entity entity) {
        Entity resolved = entity;
        if (resolved instanceof PartEntity<?> part) {
            resolved = part.getParent();
        }
        return resolved instanceof LivingEntity living ? living : null;
    }

    /**
     * How far along the ray every chunk is loaded. Block raycasts must never run into unloaded chunks, because
     * asking the level for a block there would force the server to load or generate them.
     */
    private static double loadedReach(ServerLevel level, Vec3 eye, Vec3 look) {
        for (double d = 0.0D; d <= REACH; d += LOAD_CHECK_STEP) {
            Vec3 p = eye.add(look.scale(d));
            if (!level.hasChunk(Mth.floor(p.x) >> 4, Mth.floor(p.z) >> 4)) {
                return Math.max(0.0D, d - LOAD_CHECK_STEP);
            }
        }
        return REACH;
    }

    /**
     * 300-block raycast along the look vector. Living entities (including the Ender Dragon, via its parts) are
     * checked first and win over blocks; otherwise the first block is used, or the end of the ray in mid-air.
     */
    public static Target findTarget(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(REACH));

        AABB search = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, search, e -> {
            if (e.isSpectator() || !e.isPickable()) {
                return false;
            }
            LivingEntity living = resolveLiving(e);
            return living != null && living != player;
        }, REACH * REACH);
        if (entityHit != null) {
            LivingEntity living = resolveLiving(entityHit.getEntity());
            if (living != null) {
                return new Target(living.getBoundingBox().getCenter(), living);
            }
        }

        double reach = loadedReach(level, eye, look);
        Vec3 clipEnd = eye.add(look.scale(reach));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, clipEnd, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double placeDistance;
        if (blockHit.getType() != HitResult.Type.MISS) {
            placeDistance = Math.max(1.0D, eye.distanceTo(blockHit.getLocation()) - 1.0D);
        } else if (reach < REACH) {
            placeDistance = Math.max(1.0D, reach - UNLOADED_MARGIN);
        } else {
            placeDistance = REACH;
        }
        return new Target(eye.add(look.scale(placeDistance)), null);
    }

    /** Spawns the placed singularity for the player's current mode and power, stops channeling, applies cooldown. */
    public static void spawnFromPlayer(ServerPlayer player, ItemStack weapon) {
        ServerLevel level = player.serverLevel();
        int mode = AngelOfDeathWeaponItem.getMode(weapon);
        int power = AngelOfDeathWeaponItem.getPower(weapon);
        Target target = findTarget(player);

        SingularityEntity entity = ModEntities.SINGULARITY.get().create(level);
        if (entity == null) {
            return;
        }
        entity.configure(mode, power, player.getUUID());
        entity.setPos(target.pos());
        if (target.entity() != null) {
            entity.setAttachedTarget(target.entity().getUUID());
        }
        level.addFreshEntity(entity);

        ChannelHandler.releaseHeldFreeze(player);
        player.stopUsingItem();
        player.getCooldowns().addCooldown(weapon.getItem(), PLACE_COOLDOWN_TICKS);

        float pitch = switch (mode) {
            case AngelOfDeathWeaponItem.MODE_WHITE_HOLE -> 1.4F;
            case AngelOfDeathWeaponItem.MODE_GREY_HOLE -> 1.0F;
            default -> 0.6F;
        };
        playSound(level, player.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, pitch);
        playSound(level, target.pos(), SoundEvents.END_PORTAL_SPAWN, 1.5F, pitch);
    }

    /** Handles the PlaceSingularityPayload (left-click while holding right-click). */
    public static void placeFromChannel(ServerPlayer player) {
        if (!player.isUsingItem()) {
            return;
        }
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof AngelOfDeathWeaponItem)) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }
        int mode = AngelOfDeathWeaponItem.getMode(stack);
        if (mode == AngelOfDeathWeaponItem.MODE_GREY_HOLE
                && player.getTicksUsingItem() < AngelOfDeathWeaponItem.FUSION_TICKS) {
            player.displayClientMessage(Component.translatable("message.angelofdeath.fusion_incomplete"), true);
            return;
        }
        spawnFromPlayer(player, stack);
    }

    /** Handles the SwitchModePayload (X keybind): 0 -> 1 -> 2 -> 0. */
    public static void switchMode(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof AngelOfDeathWeaponItem)) {
            stack = player.getOffhandItem();
            if (!(stack.getItem() instanceof AngelOfDeathWeaponItem)) {
                return;
            }
        }
        int next = (AngelOfDeathWeaponItem.getMode(stack) + 1) % 3;
        stack.set(ModDataComponents.SINGULARITY_MODE.get(), next);

        if (player.isUsingItem()) {
            // Restart the channel so a new mode always starts from tick 0 (fusion included).
            ChannelHandler.releaseHeldFreeze(player);
            player.stopUsingItem();
        }

        ServerLevel level = player.serverLevel();
        switch (next) {
            case AngelOfDeathWeaponItem.MODE_WHITE_HOLE ->
                    playSound(level, player.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.5F);
            case AngelOfDeathWeaponItem.MODE_GREY_HOLE ->
                    playSound(level, player.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 1.0F);
            default -> playSound(level, player.position(), SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.6F);
        }
        player.displayClientMessage(AngelOfDeathWeaponItem.modeMessage(next), true);
    }
}
