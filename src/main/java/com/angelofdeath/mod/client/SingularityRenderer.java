package com.angelofdeath.mod.client;

import com.angelofdeath.mod.entity.SingularityEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;

public class SingularityRenderer extends EntityRenderer<SingularityEntity> {
    public SingularityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(SingularityEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(SingularityEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float time = entity.tickCount + partialTick;
        float remaining = entity.getDuration() - time;
        float scale = Math.min(1.0F, time / 6.0F) * Mth.clamp(remaining / 6.0F, 0.0F, 1.0F);
        if (scale <= 0.0F) {
            return;
        }
        float radius = SingularityVisuals.placedRadius(entity.getPower()) * scale;
        Quaternionf cameraOrientation = this.entityRenderDispatcher.cameraOrientation();

        poseStack.pushPose();
        // A latched singularity is drawn at the interpolated center of its host so it glides with the mob
        // instead of stepping at the network update rate.
        Entity host = entity.getClientTarget();
        if (host != null) {
            double hostX = Mth.lerp(partialTick, host.xOld, host.getX());
            double hostY = Mth.lerp(partialTick, host.yOld, host.getY()) + host.getBbHeight() * 0.5D;
            double hostZ = Mth.lerp(partialTick, host.zOld, host.getZ());
            double selfX = Mth.lerp(partialTick, entity.xOld, entity.getX());
            double selfY = Mth.lerp(partialTick, entity.yOld, entity.getY());
            double selfZ = Mth.lerp(partialTick, entity.zOld, entity.getZ());
            poseStack.translate(hostX - selfX, hostY - selfY, hostZ - selfZ);
        }
        SingularityVisuals.renderOrb(poseStack, buffer, cameraOrientation, entity.getMode(), radius, time);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SingularityEntity entity) {
        return SingularityVisuals.WHITE_TEXTURE;
    }
}
