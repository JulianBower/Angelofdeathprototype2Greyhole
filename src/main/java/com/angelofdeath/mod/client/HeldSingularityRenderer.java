package com.angelofdeath.mod.client;

import com.angelofdeath.mod.AngelOfDeathMod;
import com.angelofdeath.mod.item.AngelOfDeathWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draws the channeled singularity in front of every player who is holding Right-Click with the weapon. */
@EventBusSubscriber(modid = AngelOfDeathMod.MOD_ID, value = Dist.CLIENT)
public final class HeldSingularityRenderer {
    private HeldSingularityRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        boolean drew = false;

        for (AbstractClientPlayer player : level.players()) {
            if (!player.isUsingItem()) {
                continue;
            }
            ItemStack use = player.getUseItem();
            if (!(use.getItem() instanceof AngelOfDeathWeaponItem)) {
                continue;
            }

            int mode = AngelOfDeathWeaponItem.getMode(use);
            int power = AngelOfDeathWeaponItem.getPower(use);
            float heldTicks = player.getTicksUsingItem() + partialTick;
            Vec3 look = player.getViewVector(partialTick);
            Vec3 focal = player.getEyePosition(partialTick).add(look.scale(AngelOfDeathWeaponItem.FOCAL_DISTANCE));
            float time = level.getGameTime() + partialTick;

            poseStack.pushPose();
            poseStack.translate(focal.x - cameraPos.x, focal.y - cameraPos.y, focal.z - cameraPos.z);
            SingularityVisuals.renderHeld(poseStack, buffer, camera.rotation(), mode, power, heldTicks, look, time);
            poseStack.popPose();
            drew = true;
        }

        if (drew) {
            buffer.endBatch();
        }
    }
}
