package com.angelofdeath.mod.network;

import com.angelofdeath.mod.AngelOfDeathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: left-click while holding right-click on the weapon (place the singularity). */
public record PlaceSingularityPayload() implements CustomPacketPayload {
    public static final Type<PlaceSingularityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AngelOfDeathMod.MOD_ID, "place_singularity"));
    public static final StreamCodec<ByteBuf, PlaceSingularityPayload> STREAM_CODEC =
            StreamCodec.unit(new PlaceSingularityPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
