package com.angelofdeath.mod.registry;

import com.angelofdeath.mod.AngelOfDeathMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AngelOfDeathMod.MOD_ID);

    /** 0 = Black Hole, 1 = White Hole, 2 = Grey Hole (default 0). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SINGULARITY_MODE =
            DATA_COMPONENTS.register("singularity_mode", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** 1 to 5 (default 1). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> POWER_LEVEL =
            DATA_COMPONENTS.register("power_level", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    private ModDataComponents() {
    }
}
