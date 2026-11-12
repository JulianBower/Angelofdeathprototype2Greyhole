package com.angelofdeath.mod.network;

import com.angelofdeath.mod.AngelOfDeathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: the player pressed the "Switch Singularity Mode" key. */
public record SwitchModePayload() implements CustomPacketPayload {
    public static final Type<SwitchModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AngelOfDeathMod.MOD_ID, "switch_mode"));
    public static final StreamCodec<ByteBuf, SwitchModePayload> STREAM_CODEC = StreamCodec.unit(new SwitchModePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
