package com.angelofdeath.mod.registry;

import com.angelofdeath.mod.AngelOfDeathMod;
import com.angelofdeath.mod.item.AngelOfDeathWeaponItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AngelOfDeathMod.MOD_ID);

    public static final DeferredItem<AngelOfDeathWeaponItem> ANGEL_OF_DEATH = ITEMS.register("angel_of_death",
            () -> new AngelOfDeathWeaponItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    private ModItems() {
    }
}
