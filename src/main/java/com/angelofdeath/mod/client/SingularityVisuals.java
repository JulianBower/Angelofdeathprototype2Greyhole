package com.angelofdeath.mod.client;

import com.angelofdeath.mod.AngelOfDeathMod;
import com.angelofdeath.mod.item.AngelOfDeathWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
    private static final int ORBIT_SEGMENTS = 72;
    private static final int ARC_STEPS = 8;
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
            default -> renderBlackHole(ps, buffer, camera, radius, time, true);
        }
    }

    /**
     * Pitch-black core, a double-sided camera-facing event horizon ring that fades from white to pale silver, and
     * (optionally) two tilted dark-silver accretion rings orbiting the core.
     */
    public static void renderBlackHole(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, float radius,
                                       float time, boolean accretionRings) {
        renderSphere(ps, buffer, radius, 0, 0, 0, 255);
        renderGradientRing(ps, buffer, camera, radius * 1.02F, radius * 1.13F,
                255, 255, 255, 255, 196, 203, 216, 70);
        if (accretionRings) {
            renderOrbitRing(ps, buffer, radius * 1.45F, radius * 2.15F, 68.0F, time * 1.1F, time * 0.14F,
                    120, 126, 138, 235, 52, 56, 66, 0);
            renderOrbitRing(ps, buffer, radius * 1.90F, radius * 2.70F, 52.0F, 90.0F - time * 0.8F, -time * 0.10F,
                    104, 110, 122, 225, 44, 48, 58, 0);
        }
    }

    /** Blinding white sphere with a soft additive white corona. */
    public static void renderWhiteHole(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, float radius,
                                       float time) {
        renderSphere(ps, buffer, radius, 255, 255, 255, 255);
        float pulse = 1.0F + 0.08F * Mth.sin(time * 0.3F);
        renderGlow(ps, buffer, camera, radius * 0.95F, radius * 2.8F * pulse, 255, 255, 255, 0.85F);
    }

    /**
     * Metallic grey sphere (110, 115, 125) with a counter-rotating eclipse halo (white inner, pitch-black outer)
     * and two opposing orbital rings, all pulsing slowly.
     */
    public static void renderGreyHole(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, float radius,
                                      float time) {
        renderSphere(ps, buffer, radius, 110, 115, 125, 255);
        float pulse = 1.0F + 0.06F * Mth.sin(time * 0.12F);
        float counterPulse = 1.0F + 0.06F * Mth.sin(time * 0.12F + (float) Math.PI);

        renderArcHalo(ps, buffer, camera, radius * 1.10F * pulse, radius * 1.34F * pulse,
                time * 0.045F, 5, 0.62F, 255, 255, 255, 240);
        renderArcHalo(ps, buffer, camera, radius * 1.44F * counterPulse, radius * 1.80F * counterPulse,
                -time * 0.035F, 4, 0.68F, 8, 8, 12, 235);

        // Opposing orbits: mirrored tilt, opposite precession, opposite colors.
        renderOrbitRing(ps, buffer, radius * 1.95F, radius * 2.20F, 62.0F, time * 1.0F, time * 0.12F,
                245, 247, 252, 235, 200, 205, 215, 40);
        renderOrbitRing(ps, buffer, radius * 2.30F, radius * 2.58F, -62.0F, -time * 1.0F, -time * 0.12F + 3.1416F,
                14, 14, 20, 235, 14, 14, 20, 60);
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
        renderBlackHole(ps, buffer, camera, miniRadius, time, false);
        ps.popPose();

        ps.pushPose();
        ps.translate(offset.x, offset.y, offset.z);
        renderWhiteHole(ps, buffer, camera, miniRadius, time);
        ps.popPose();
    }

    // ------------------------------------------------------------------ primitives

    /** Fullbright UV sphere. Drawn opaque (writes depth) so rings that follow are occluded correctly. */
    public static void renderSphere(PoseStack ps, MultiBufferSource buffer, float radius, int r, int g, int b, int a) {
        RenderType type = RenderType.beaconBeam(WHITE_TEXTURE, false);
        VertexConsumer consumer = buffer.getBuffer(type);
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
        // Make sure the opaque core is on screen before any translucent ring is blended over it.
        if (buffer instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(type);
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

    /** Flat annulus quad strip. {@code horizontal} = XZ plane, otherwise XY plane. Both faces are emitted. */
    private static void ringQuads(VertexConsumer consumer, PoseStack.Pose pose, boolean horizontal, int segments,
                                  float inner, float outer, int ir, int ig, int ib, int ia, int or, int og, int ob,
                                  int oa, float phase, float modulation) {
        for (int i = 0; i < segments; i++) {
            double a0 = 2.0D * Math.PI * i / segments;
            double a1 = 2.0D * Math.PI * (i + 1) / segments;
            float m0 = 1.0F - modulation + modulation * (0.5F + 0.5F * Mth.cos((float) a0 - phase));
            float m1 = 1.0F - modulation + modulation * (0.5F + 0.5F * Mth.cos((float) a1 - phase));
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);

            // Front face.
            planarVertex(consumer, pose, horizontal, inner * c0, inner * s0, ir, ig, ib, ia * m0);
            planarVertex(consumer, pose, horizontal, outer * c0, outer * s0, or, og, ob, oa * m0);
            planarVertex(consumer, pose, horizontal, outer * c1, outer * s1, or, og, ob, oa * m1);
            planarVertex(consumer, pose, horizontal, inner * c1, inner * s1, ir, ig, ib, ia * m1);
            // Back face (reverse winding) so the ring is visible from both sides.
            planarVertex(consumer, pose, horizontal, inner * c1, inner * s1, ir, ig, ib, ia * m1);
            planarVertex(consumer, pose, horizontal, outer * c1, outer * s1, or, og, ob, oa * m1);
            planarVertex(consumer, pose, horizontal, outer * c0, outer * s0, or, og, ob, oa * m0);
            planarVertex(consumer, pose, horizontal, inner * c0, inner * s0, ir, ig, ib, ia * m0);
        }
    }

    private static void planarVertex(VertexConsumer consumer, PoseStack.Pose pose, boolean horizontal, float u,
                                     float v, int r, int g, int b, float a) {
        float x = u;
        float y = horizontal ? 0.0F : v;
        float z = horizontal ? v : 0.0F;
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, Mth.clamp((int) a, 0, 255))
                .setUv(0.5F, 0.5F)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** Double-sided camera-facing ring whose color and alpha blend from the inner edge to the outer edge. */
    public static void renderGradientRing(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, float inner,
                                          float outer, int ir, int ig, int ib, int ia, int or, int og, int ob,
                                          int oa) {
        ps.pushPose();
        ps.mulPose(camera);
        VertexConsumer consumer = buffer.getBuffer(RenderType.beaconBeam(WHITE_TEXTURE, true));
        ringQuads(consumer, ps.last(), false, RING_SEGMENTS, inner, outer, ir, ig, ib, ia, or, og, ob, oa, 0.0F, 0.0F);
        ps.popPose();
    }

    /**
     * Flat ring in 3D, tilted and precessing, with a bright arc that travels around it so the orbital motion is
     * visible. Double sided.
     */
    public static void renderOrbitRing(PoseStack ps, MultiBufferSource buffer, float inner, float outer,
                                       float tiltDegrees, float precessionDegrees, float phase, int ir, int ig,
                                       int ib, int ia, int or, int og, int ob, int oa) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(precessionDegrees));
        ps.mulPose(Axis.XP.rotationDegrees(tiltDegrees));
        VertexConsumer consumer = buffer.getBuffer(RenderType.beaconBeam(WHITE_TEXTURE, true));
        ringQuads(consumer, ps.last(), true, ORBIT_SEGMENTS, inner, outer, ir, ig, ib, ia, or, og, ob, oa, phase, 0.6F);
        ps.popPose();
    }

    /**
     * Camera-facing eclipse halo: a faint continuous ring plus {@code arcs} bright arcs that fade toward their
     * ends and toward the outer edge. {@code rotation} (radians) spins the arcs around the view axis.
     */
    public static void renderArcHalo(PoseStack ps, MultiBufferSource buffer, Quaternionf camera, float inner,
                                     float outer, float rotation, int arcs, float arcFill, int r, int g, int b,
                                     int a) {
        ps.pushPose();
        ps.mulPose(camera);
        ps.mulPose(Axis.ZP.rotation(rotation));
        VertexConsumer consumer = buffer.getBuffer(RenderType.beaconBeam(WHITE_TEXTURE, true));
        PoseStack.Pose pose = ps.last();

        // Thin continuous base ring hugging the inner edge.
        float baseOuter = inner + (outer - inner) * 0.22F;
        ringQuads(consumer, pose, false, RING_SEGMENTS, inner, baseOuter, r, g, b, (int) (a * 0.55F), r, g, b,
                (int) (a * 0.10F), 0.0F, 0.0F);

        double slot = 2.0D * Math.PI / arcs;
        double span = slot * arcFill;
        for (int k = 0; k < arcs; k++) {
            double start = k * slot;
            for (int s = 0; s < ARC_STEPS; s++) {
                double t0 = (double) s / ARC_STEPS;
                double t1 = (double) (s + 1) / ARC_STEPS;
                double a0 = start + span * t0;
                double a1 = start + span * t1;
                float f0 = (float) Math.sin(Math.PI * t0);
                float f1 = (float) Math.sin(Math.PI * t1);
                float c0 = (float) Math.cos(a0);
                float s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1);
                float s1 = (float) Math.sin(a1);
                arcVertex(consumer, pose, inner * c0, inner * s0, r, g, b, a * f0);
                arcVertex(consumer, pose, outer * c0, outer * s0, r, g, b, a * f0 * 0.2F);
                arcVertex(consumer, pose, outer * c1, outer * s1, r, g, b, a * f1 * 0.2F);
                arcVertex(consumer, pose, inner * c1, inner * s1, r, g, b, a * f1);
                arcVertex(consumer, pose, inner * c1, inner * s1, r, g, b, a * f1);
                arcVertex(consumer, pose, outer * c1, outer * s1, r, g, b, a * f1 * 0.2F);
                arcVertex(consumer, pose, outer * c0, outer * s0, r, g, b, a * f0 * 0.2F);
                arcVertex(consumer, pose, inner * c0, inner * s0, r, g, b, a * f0);
            }
        }
        ps.popPose();
    }

    private static void arcVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, int r, int g,
                                  int b, float a) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(r, g, b, Mth.clamp((int) a, 0, 255))
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
