package com.angelofdeath.mod.entity;

import com.angelofdeath.mod.logic.FreezeRules;
import com.angelofdeath.mod.logic.FreezeTracker;
import com.angelofdeath.mod.logic.SingularityActions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A placed singularity. Mode 0 = Black Hole, 1 = White Hole, 2 = Grey Hole.
 */
public class SingularityEntity extends Entity {
    public static final int BLACK_DURATION = 100;
    public static final int WHITE_DURATION = 100;
    public static final int GREY_DURATION = 120;
    private static final int MAX_PUSHED_BLOCKS = 2500;

    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_POWER =
            SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final DustParticleOptions BLACK_DUST = new DustParticleOptions(new Vector3f(0.0F, 0.0F, 0.0F), 2.0F);
    private static final DustParticleOptions WHITE_DUST = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 2.0F);
    private static final DustParticleOptions GREY_DUST = new DustParticleOptions(new Vector3f(0.53F, 0.53F, 0.57F), 2.0F);

    private int life;
    private final FreezeTracker freezeTracker = new FreezeTracker();

    public SingularityEntity(EntityType<? extends SingularityEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void configure(int mode, int power, @Nullable UUID owner) {
        this.entityData.set(DATA_MODE, Mth.clamp(mode, 0, 2));
        this.entityData.set(DATA_POWER, Mth.clamp(power, 1, 5));
        this.entityData.set(DATA_OWNER, Optional.ofNullable(owner));
    }

    public int getMode() {
        return this.entityData.get(DATA_MODE);
    }

    public int getPower() {
        return this.entityData.get(DATA_POWER);
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public int getDuration() {
        return switch (getMode()) {
            case 1 -> WHITE_DURATION;
            case 2 -> GREY_DURATION;
            default -> BLACK_DURATION;
        };
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MODE, 0);
        builder.define(DATA_POWER, 1);
        builder.define(DATA_OWNER, Optional.empty());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_MODE, Mth.clamp(tag.getInt("Mode"), 0, 2));
        this.entityData.set(DATA_POWER, Mth.clamp(tag.getInt("Power"), 1, 5));
        this.entityData.set(DATA_OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        this.life = tag.getInt("Life");
        this.freezeTracker.load(tag, "Frozen");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Mode", getMode());
        tag.putInt("Power", getPower());
        UUID owner = getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("Life", this.life);
        this.freezeTracker.save(tag, "Frozen");
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160.0D * 160.0D;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(0.8D + 0.32D * getPower() * 2.5D);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel && reason.shouldDestroy()) {
            this.freezeTracker.releaseAll(serverLevel);
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.life++;
        switch (getMode()) {
            case 1 -> tickWhiteHole(level);
            case 2 -> tickGreyHole(level);
            default -> tickBlackHole(level);
        }
        if (this.life >= getDuration()) {
            this.discard();
        }
    }

    private boolean isOwner(Entity entity) {
        UUID owner = getOwnerUUID();
        return owner != null && owner.equals(entity.getUUID());
    }

    @Nullable
    private Entity getOwnerEntity(ServerLevel level) {
        UUID owner = getOwnerUUID();
        return owner == null ? null : level.getEntity(owner);
    }

    private boolean isAffectable(Entity entity) {
        return entity != this && !isOwner(entity) && !(entity instanceof SingularityEntity)
                && !entity.isSpectator() && !(entity instanceof HangingEntity);
    }

    // ------------------------------------------------------------------ Black Hole

    private void tickBlackHole(ServerLevel level) {
        int power = getPower();
        double radius = 14.0D + power * 7.0D;
        Vec3 center = position();
        boolean damageTick = this.life % 10 == 0;
        DamageSource source = SingularityActions.voidSource(level, this, getOwnerEntity(level));

        List<Entity> targets = level.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(radius),
                e -> isAffectable(e) && (e instanceof LivingEntity || e instanceof ItemEntity));
        for (Entity target : targets) {
            double distance = target.getBoundingBox().getCenter().distanceTo(center);
            if (distance > radius) {
                continue;
            }
            double falloff = 1.0D - distance / radius;
            SingularityActions.pull(target, center, (0.35D + 0.15D * power) * (0.4D + 0.6D * falloff), 3.0D);
            if (target instanceof LivingEntity living) {
                living.fallDistance = 0.0F;
                if (damageTick && distance < 3.0D && SingularityActions.canHarm(living)) {
                    living.invulnerableTime = 0;
                    living.hurt(source, 4.0F * power);
                }
            }
        }

        level.sendParticles(BLACK_DUST, center.x, center.y, center.z, 10, 0.5D, 0.5D, 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 30, 3.0D + power, 3.0D + power,
                3.0D + power, 0.2D);
        if (this.life == 1) {
            SingularityActions.playSound(level, center, SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.5F);
        }
        if (this.life % 20 == 0) {
            SingularityActions.playSound(level, center, SoundEvents.PORTAL_AMBIENT, 2.0F, 0.5F);
        }
    }

    // ------------------------------------------------------------------ White Hole

    private void tickWhiteHole(ServerLevel level) {
        int power = getPower();
        Vec3 center = position();

        if (this.life == 1) {
            shockwave(level, power, center);
            blockPush(level, power);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 150, 1.5D, 1.5D, 1.5D, 0.8D);
            SingularityActions.playSound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 10.0F, 0.6F);
            SingularityActions.playSound(level, center, SoundEvents.LIGHTNING_BOLT_IMPACT, 10.0F, 0.8F);
        } else {
            // Repulsive barrier field.
            double radius = 10.0D + power * 5.0D;
            List<Entity> targets = level.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(radius),
                    e -> isAffectable(e) && (e instanceof LivingEntity || e instanceof ItemEntity));
            for (Entity target : targets) {
                double distance = target.getBoundingBox().getCenter().distanceTo(center);
                if (distance > radius) {
                    continue;
                }
                double inverse = Math.max(0.2D, 1.0D - distance / radius);
                SingularityActions.push(target, center, (0.25D + 0.12D * power) * inverse, 0.03D, 3.0D);
            }
        }

        level.sendParticles(WHITE_DUST, center.x, center.y, center.z, 10, 0.5D, 0.5D, 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 8, 0.6D, 0.6D, 0.6D, 0.25D);
        if (this.life % 20 == 0) {
            SingularityActions.playSound(level, center, SoundEvents.BEACON_AMBIENT, 2.0F, 1.5F);
        }
    }

    private void shockwave(ServerLevel level, int power, Vec3 center) {
        double blast = 8.0D * power; // Power 5 reaches 40 blocks.
        DamageSource source = level.damageSources().explosion(this, getOwnerEntity(level));
        List<Entity> targets = level.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(blast),
                e -> isAffectable(e) && (e instanceof LivingEntity || e instanceof ItemEntity
                        || e instanceof Projectile || e instanceof PrimedTnt));
        for (Entity target : targets) {
            Vec3 away = target.getBoundingBox().getCenter().subtract(center);
            double distance = away.length();
            if (distance > blast) {
                continue;
            }
            double factor = 1.0D - distance / blast;
            Vec3 direction = distance < 0.01D ? new Vec3(0.0D, 1.0D, 0.0D) : away.scale(1.0D / distance);
            double speed = (0.9D + 0.8D * power) * (0.4D + 0.6D * factor);
            Vec3 velocity = direction.scale(speed).add(0.0D, 0.35D + 0.1D * power * factor, 0.0D);
            target.setDeltaMovement(velocity);
            target.hurtMarked = true;
            target.fallDistance = 0.0F;
            if (target instanceof LivingEntity living && SingularityActions.canHarm(living)) {
                living.invulnerableTime = 0;
                living.hurt(source, (float) ((2.0D + 6.0D * power) * factor));
            }
        }
    }

    private void blockPush(ServerLevel level, int power) {
        int radius = 4 + 2 * power;
        BlockPos origin = this.blockPosition();
        Vec3 originCenter = position();
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (level.isOutsideBuildHeight(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()
                            || state.getDestroySpeed(level, cursor) < 0.0F
                            || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                            || state.hasProperty(BlockStateProperties.BED_PART)) {
                        continue;
                    }
                    candidates.add(cursor.immutable());
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(originCenter)));
        int limit = Math.min(candidates.size(), MAX_PUSHED_BLOCKS);
        for (int i = 0; i < limit; i++) {
            BlockPos pos = candidates.get(i);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                continue;
            }
            Vec3 blockCenter = Vec3.atCenterOf(pos);
            Vec3 away = blockCenter.subtract(originCenter);
            double distance = away.length();
            Vec3 direction = distance < 0.001D ? new Vec3(0.0D, 1.0D, 0.0D) : away.scale(1.0D / distance);
            double factor = 1.0D - Math.min(1.0D, distance / (radius + 1.0D));

            FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
            double speed = (0.5D + 0.25D * power) * (0.5D + 0.5D * factor) * (0.85D + this.random.nextDouble() * 0.3D);
            double upward = 0.35D + 0.45D * factor + this.random.nextDouble() * 0.2D;
            falling.setDeltaMovement(direction.x * speed, upward, direction.z * speed);
            falling.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------ Grey Hole

    private boolean isFreezable(Entity entity) {
        return isAffectable(entity) && !entity.isPassenger() && !(entity instanceof Marker)
                && !(entity instanceof Display) && !(entity instanceof LightningBolt);
    }

    private void tickGreyHole(ServerLevel level) {
        Vec3 center = position();
        double radius = FreezeRules.GLOBAL_RADIUS;
        double radiusSqr = radius * radius;

        Set<UUID> keep = new HashSet<>();
        List<Entity> targets = level.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(radius),
                e -> isFreezable(e) && e.distanceToSqr(center) <= radiusSqr);
        for (Entity target : targets) {
            this.freezeTracker.freeze(target);
            keep.add(target.getUUID());
        }
        this.freezeTracker.keepOnly(level, keep);

        level.sendParticles(GREY_DUST, center.x, center.y, center.z, 8, 0.6D, 0.6D, 0.6D, 0.0D);
        level.sendParticles(ParticleTypes.ASH, center.x, center.y, center.z, 20, 4.0D, 4.0D, 4.0D, 0.02D);
        if (this.life == 1) {
            SingularityActions.playSound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0F, 0.5F);
        }
        if (this.life % 20 == 0) {
            SingularityActions.playSound(level, center, SoundEvents.BEACON_AMBIENT, 4.0F, 0.6F);
        }
    }
}
