package com.angelofdeath.mod.client;

import com.angelofdeath.mod.AngelOfDeathMod;
import com.angelofdeath.mod.item.AngelOfDeathWeaponItem;
import com.angelofdeath.mod.network.PlaceSingularityPayload;
import com.angelofdeath.mod.network.SwitchModePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Game-bus client events: key handling, placement trigger and Grey Hole movement lock. */
@EventBusSubscriber(modid = AngelOfDeathMod.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private static boolean wasAttackDown;
    private static int lastPlaceSentTick = -100;

    private ClientGameEvents() {
    }

    private static boolean isHoldingWeapon(Player player) {
        return player.getMainHandItem().getItem() instanceof AngelOfDeathWeaponItem
                || player.getOffhandItem().getItem() instanceof AngelOfDeathWeaponItem;
    }

    private static boolean isChanneling(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof AngelOfDeathWeaponItem;
    }

    private static void requestPlace(Player player) {
        if (isChanneling(player) && player.tickCount - lastPlaceSentTick > 4) {
            lastPlaceSentTick = player.tickCount;
            PacketDistributor.sendToServer(new PlaceSingularityPayload());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            wasAttackDown = false;
            while (ClientModEvents.SWITCH_MODE_KEY.consumeClick()) {
                // discard clicks that happen outside a world
            }
            return;
        }

        // X: switch singularity mode.
        while (ClientModEvents.SWITCH_MODE_KEY.consumeClick()) {
            if (minecraft.screen == null && isHoldingWeapon(player)) {
                PacketDistributor.sendToServer(new SwitchModePayload());
            }
        }

        // Left-click while holding right-click. Vanilla swallows attack clicks while an item is being used,
        // so the raw key state is edge-detected here in addition to the interaction events below.
        boolean attackDown = minecraft.options.keyAttack.isDown();
        if (attackDown && !wasAttackDown && minecraft.screen == null) {
            requestPlace(player);
        }
        wasAttackDown = attackDown;

        // Grey Hole stasis: kill any leftover velocity of the local player.
        if (FreezeClient.isFrozen(player)) {
            player.setDeltaMovement(Vec3.ZERO);
        }
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        requestPlace(event.getEntity());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide && isChanneling(event.getEntity())) {
            requestPlace(event.getEntity());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (event.getEntity() instanceof LocalPlayer player && FreezeClient.isFrozen(player)) {
            Input input = event.getInput();
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
        }
    }
}
