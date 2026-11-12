package com.angelofdeath.mod.logic;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Tracks entities that were frozen in place (zero velocity, no gravity, no AI) and restores
 * their original flags when they are released.
 */
public final class FreezeTracker {
    private static final class State {
        final boolean hadNoGravity;
        final boolean hadNoAi;
        final Vec3 pos;

        State(boolean hadNoGravity, boolean hadNoAi, Vec3 pos) {
            this.hadNoGravity = hadNoGravity;
            this.hadNoAi = hadNoAi;
            this.pos = pos;
        }
    }

    private final Map<UUID, State> states = new HashMap<>();

    public boolean isEmpty() {
        return states.isEmpty();
    }

    public void freeze(Entity entity) {
        State state = states.get(entity.getUUID());
        boolean fresh = false;
        if (state == null) {
            boolean noAi = entity instanceof Mob mob && mob.isNoAi();
            state = new State(entity.isNoGravity(), noAi, entity.position());
            states.put(entity.getUUID(), state);
            entity.setNoGravity(true);
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
            }
            fresh = true;
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
        if (entity instanceof ServerPlayer) {
            entity.hurtMarked = true;
        } else {
            entity.setPos(state.pos);
            if (fresh) {
                entity.hurtMarked = true;
            }
        }
    }

    public void keepOnly(ServerLevel level, Set<UUID> keep) {
        Iterator<Map.Entry<UUID, State>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, State> entry = iterator.next();
            if (!keep.contains(entry.getKey())) {
                restore(level, entry.getKey(), entry.getValue());
                iterator.remove();
            }
        }
    }

    public void releaseAll(ServerLevel level) {
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            restore(level, entry.getKey(), entry.getValue());
        }
        states.clear();
    }

    private static void restore(ServerLevel level, UUID id, State state) {
        Entity entity = level.getEntity(id);
        if (entity == null) {
            return;
        }
        entity.setNoGravity(state.hadNoGravity);
        if (entity instanceof Mob mob) {
            mob.setNoAi(state.hadNoAi);
        }
        entity.hurtMarked = true;
    }

    public void save(CompoundTag tag, String key) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("Id", entry.getKey());
            item.putBoolean("NoGravity", entry.getValue().hadNoGravity);
            item.putBoolean("NoAi", entry.getValue().hadNoAi);
            item.putDouble("X", entry.getValue().pos.x);
            item.putDouble("Y", entry.getValue().pos.y);
            item.putDouble("Z", entry.getValue().pos.z);
            list.add(item);
        }
        tag.put(key, list);
    }

    public void load(CompoundTag tag, String key) {
        states.clear();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            if (!item.hasUUID("Id")) {
                continue;
            }
            states.put(item.getUUID("Id"), new State(item.getBoolean("NoGravity"), item.getBoolean("NoAi"),
                    new Vec3(item.getDouble("X"), item.getDouble("Y"), item.getDouble("Z"))));
        }
    }
}
