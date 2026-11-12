package com.angelofdeath.mod.registry;

import com.angelofdeath.mod.AngelOfDeathMod;
import com.angelofdeath.mod.entity.SingularityEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AngelOfDeathMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SingularityEntity>> SINGULARITY =
            ENTITY_TYPES.register("singularity", () -> EntityType.Builder
                    .<SingularityEntity>of(SingularityEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .fireImmune()
                    .build("singularity"));

    private ModEntities() {
    }
}
