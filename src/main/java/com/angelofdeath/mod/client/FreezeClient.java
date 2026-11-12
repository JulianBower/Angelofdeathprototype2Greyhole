package com.angelofdeath.mod.client;

import com.angelofdeath.mod.entity.SingularityEntity;
import com.angelofdeath.mod.item.AngelOfDeathWeaponItem;
import com.angelofdeath.mod.logic.FreezeRules;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Client-side check for whether the local player is inside a Grey Hole stasis field. */
public final class FreezeClient {
    private FreezeClient() {
    }

    public static boolean isFrozen(LocalPlayer self) {
        Level level = self.level();
        double radius = FreezeRules.GLOBAL_RADIUS;

        List<SingularityEntity> holes = level.getEntitiesOfClass(SingularityEntity.class,
                self.getBoundingBox().inflate(radius), e -> e.getMode() == AngelOfDeathWeaponItem.MODE_GREY_HOLE);
        for (SingularityEntity hole : holes) {
            if (self.getUUID().equals(hole.getOwnerUUID())) {
                continue;
            }
            if (hole.distanceToSqr(self) <= radius * radius) {
                return true;
            }
        }

        for (Player other : level.players()) {
            if (other == self || !other.isUsingItem()) {
                continue;
            }
            ItemStack use = other.getUseItem();
            if (use.getItem() instanceof AngelOfDeathWeaponItem
                    && AngelOfDeathWeaponItem.getMode(use) == AngelOfDeathWeaponItem.MODE_GREY_HOLE
                    && other.getTicksUsingItem() >= AngelOfDeathWeaponItem.FUSION_TICKS
                    && FreezeRules.inHeldCone(other, self)) {
                return true;
            }
        }
        return false;
    }
}
