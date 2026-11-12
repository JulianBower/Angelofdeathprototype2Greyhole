package com.angelofdeath.mod;

import com.angelofdeath.mod.network.ModNetwork;
import com.angelofdeath.mod.registry.ModCreativeTabs;
import com.angelofdeath.mod.registry.ModDataComponents;
import com.angelofdeath.mod.registry.ModEntities;
import com.angelofdeath.mod.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(AngelOfDeathMod.MOD_ID)
public class AngelOfDeathMod {
    public static final String MOD_ID = "angelofdeath";

    public AngelOfDeathMod(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(ModNetwork::register);
    }
}
