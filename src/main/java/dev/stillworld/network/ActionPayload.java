package dev.stillworld.network;
import dev.stillworld.Stillworld;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record ActionPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<ActionPayload> TYPE = new Type<>(Stillworld.id("action"));
    public static final StreamCodec<ByteBuf, ActionPayload> CODEC = ByteBufCodecs.COMPOUND_TAG.map(ActionPayload::new, ActionPayload::data);
    @Override public Type<ActionPayload> type() { return TYPE; }
}
