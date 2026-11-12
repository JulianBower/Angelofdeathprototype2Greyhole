package com.angelofdeath.mod.client;

import com.angelofdeath.mod.AngelOfDeathMod;
import com.angelofdeath.mod.item.AngelOfDeathWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Procedural geometry for the Black / White / Grey Hole orbs. */
public final class SingularityVisuals {
    public static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AngelOfDeathMod.MOD_ID, "textures/misc/white.png");

    private static final int SLICES = 28;
    private static final int STACKS = 18;
    private static final int RING_SEGMENTS = 64;
    private static final int GLOW_LAYERS = 10;

    private SingularityVisuals() {
    }

    public static float heldRadius(int power) {
        return 0.30F + 0.10F * power;
    }

    public static float placedRadius(int power) {
        return 0.80F + 0.32F * power;
    }

    // ------------------------------------------------------------------ high level

    /** Held effect, drawn in front of a channeling player. The pose stack must already be at the focal point. */
    public static void renderHeld(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, int mode, int power,
                                  float heldTicks, Vec3 look, float time) {
        float radius = heldRadius(power);
        if (mode == AngelOfDeathWeaponItem.MODE_GREY_HOLE && heldTicks < AngelOfDeathWeaponItem.FUSION_TICKS) {
            renderFusion(ps, buffer, camera, radius, heldTicks, look, time);
        } else {
            renderOrb(ps, buffer, camera, mode, radius, time);
        }
    }

    public static void renderOrb(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, int mode, float radius,
                                 float time) {
        switch (mode) {
            case AngelOfDeathWeaponItem.MODE_WHITE_HOLE -> renderWhiteHole(ps, buffer, camera, radius, time);
            case AngelOfDeathWeaponItem.MODE_GREY_HOLE -> renderGreyHole(ps, buffer, camera, radius, time);
            default -> renderBlackHole(ps, buffer, camera, radius);
        }
    }

    /** Solid pitch-black sphere with a razor-thin white event horizon ring hugging its edge. */
    public static void renderBlackHole(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, float radius) {
        renderSphere(ps, buffer, radius, 0, 0, 0, 255, false);
        renderRing(ps, buffer, camera, radius * 1.03F, radius * 1.065F, 255, 255, 255, 255);
    }

    /** Blinding white sphere with a soft additive white corona. */
    public static void renderWhiteHole(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, float radius,
                                       float time) {
        renderSphere(ps, buffer, radius, 255, 255, 255, 255, false);
        float pulse = 1.0F + 0.08F * Mth.sin(time * 0.3F);
        renderGlow(ps, buffer, camera, radius * 0.95F, radius * 2.8F * pulse, 255, 255, 255, 0.85F);
    }

    /** Metallic grey sphere with pulsing inner-white and outer-shadow distortion rings. */
    public static void renderGreyHole(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, float radius,
                                      float time) {
        renderSphere(ps, buffer, radius, 135, 135, 145, 255, false);
        float pulse = 1.0F + 0.07F * Mth.sin(time * 0.15F);
        float counterPulse = 1.0F + 0.07F * Mth.sin(time * 0.15F + (float) Math.PI);
        renderRing(ps, buffer, camera, radius * 1.12F * pulse, radius * 1.22F * pulse, 255, 255, 255, 235);
        renderRing(ps, buffer, camera, radius * 1.30F * counterPulse, radius * 1.52F * counterPulse, 15, 15, 20, 220);
    }

    /** Mini black hole and mini white hole spiral together until they fuse at tick 30. */
    public static void renderFusion(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, float radius,
                                    float heldTicks, Vec3 look, float time) {
        double progress = Mth.clamp(heldTicks / AngelOfDeathWeaponItem.FUSION_TICKS, 0.0F, 1.0F);
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 up = right.cross(look).normalize();
        double separation = 1.6D * Math.pow(1.0D - progress, 1.4D) + 0.05D;
        double angle = 6.0D * Math.PI * progress * progress;
        Vec3 offset = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).scale(separation);
        float miniRadius = radius * (float) (0.5D + 0.3D * progress);

        // Mini black hole starts on the player's left, mini white hole on the right.
        ps.pushPose();
        ps.translate(-offset.x, -offset.y, -offset.z);
        renderBlackHole(ps, buffer, camera, miniRadius);
        ps.popPose();

        ps.pushPose();
        ps.translate(offset.x, offset.y, offset.z);
        renderWhiteHole(ps, buffer, camera, miniRadius, time);
        ps.popPose();
    }

    // ------------------------------------------------------------------ primitives

    /** Fullbright UV sphere. */
    public static void renderSphere(PoseStack ps, MultiBufferSource buffer, float radius, int r, int g, int b, int a,
                                    boolean translucent) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.beaconBeam(WHITE_TEXTURE, translucent));
        PoseStack.Pose pose = ps.last();
        for (int i = 0; i < STACKS; i++) {
            double phi0 = Math.PI * (-0.5D + (double) i / STACKS);
            double phi1 = Math.PI * (-0.5D + (double) (i + 1) / STACKS);
            for (int j = 0; j < SLICES; j++) {
                double theta0 = 2.0D * Math.PI * j / SLICES;
                double theta1 = 2.0D * Math.PI * (j + 1) / SLICES;
                sphereVertex(consumer, pose, radius, phi0, theta0, r, g, b, a);
                sphereVertex(consumer, pose, radius, phi1, theta0, r, g, b, a);
                sphereVertex(consumer, pose, radius, phi1, theta1, r, g, b, a);
                sphereVertex(consumer, pose, radius, phi0, theta1, r, g, b, a);
            }
        }
    }

    private static void sphereVertex(VertexConsumer consumer, PoseStack.Pose pose, float radius, double phi,
                                     double theta, int r, int g, int b, int a) {
        float nx = (float) (Math.cos(phi) * Math.cos(theta));
        float ny = (float) Math.sin(phi);
        float nz = (float) (Math.cos(phi) * Math.sin(theta));
        consumer.addVertex(pose, nx * radius, ny * radius, nz * radius)
                .setColor(r, g, b, a)
                .setUv(0.5F, 0.5F)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    /** Flat camera-facing annulus (double sided). */
    public static void renderRing(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, float inner, float outer,
                                  int r, int g, int b, int a) {
        ps.pushPose();
        ps.mulPose(camera);
        VertexConsumer consumer = buffer.getBuffer(RenderType.beaconBeam(WHITE_TEXTURE, true));
        PoseStack.Pose pose = ps.last();
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = 2.0D * Math.PI * i / RING_SEGMENTS;
            double a1 = 2.0D * Math.PI * (i + 1) / RING_SEGMENTS;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            ringVertex(consumer, pose, inner * c0, inner * s0, r, g, b, a);
            ringVertex(consumer, pose, outer * c0, outer * s0, r, g, b, a);
            ringVertex(consumer, pose, outer * c1, outer * s1, r, g, b, a);
            ringVertex(consumer, pose, inner * c1, inner * s1, r, g, b, a);

            ringVertex(consumer, pose, inner * c1, inner * s1, r, g, b, a);
            ringVertex(consumer, pose, outer * c1, outer * s1, r, g, b, a);
            ringVertex(consumer, pose, outer * c0, outer * s0, r, g, b, a);
            ringVertex(consumer, pose, inner * c0, inner * s0, r, g, b, a);
        }
        ps.popPose();
    }

    private static void ringVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, int r, int g,
                                   int b, int a) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(r, g, b, a)
                .setUv(0.5F, 0.5F)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    /** Soft additive glow: concentric annulus layers whose alpha fades to zero at the outer edge. */
    public static void renderGlow(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, float innerRadius,
                                  float outerRadius, int r, int g, int b, float peakAlpha) {
        ps.pushPose();
        ps.mulPose(camera);
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = ps.last();
        for (int layer = 0; layer < GLOW_LAYERS; layer++) {
            float t0 = layer / (float) GLOW_LAYERS;
            float t1 = (layer + 1) / (float) GLOW_LAYERS;
            float r0 = Mth.lerp(t0, innerRadius, outerRadius);
            float r1 = Mth.lerp(t1, innerRadius, outerRadius);
            int alpha0 = (int) (255.0F * peakAlpha * (1.0F - t0) * (1.0F - t0));
            int alpha1 = (int) (255.0F * peakAlpha * (1.0F - t1) * (1.0F - t1));
            for (int i = 0; i < RING_SEGMENTS; i++) {
                double a0 = 2.0D * Math.PI * i / RING_SEGMENTS;
                double a1 = 2.0D * Math.PI * (i + 1) / RING_SEGMENTS;
                float c0 = (float) Math.cos(a0);
                float s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1);
                float s1 = (float) Math.sin(a1);
                glowVertex(consumer, pose, r0 * c0, r0 * s0, r, g, b, alpha0);
                glowVertex(consumer, pose, r1 * c0, r1 * s0, r, g, b, alpha1);
                glowVertex(consumer, pose, r1 * c1, r1 * s1, r, g, b, alpha1);
                glowVertex(consumer, pose, r0 * c1, r0 * s1, r, g, b, alpha0);

                glowVertex(consumer, pose, r0 * c1, r0 * s1, r, g, b, alpha0);
                glowVertex(consumer, pose, r1 * c1, r1 * s1, r, g, b, alpha1);
                glowVertex(consumer, pose, r1 * c0, r1 * s0, r, g, b, alpha1);
                glowVertex(consumer, pose, r0 * c0, r0 * s0, r, g, b, alpha0);
            }
        }
        ps.popPose();
    }

    private static void glowVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, int r, int g,
                                   int b, int a) {
        consumer.addVertex(pose, x, y, 0.0F).setColor(r, g, b, Mth.clamp(a, 0, 255));
    }
}
