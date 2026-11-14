package dev.stillworld.network;
import dev.stillworld.Stillworld;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record StatePayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<StatePayload> TYPE = new Type<>(Stillworld.id("state"));
    public static final StreamCodec<ByteBuf, StatePayload> CODEC = ByteBufCodecs.COMPOUND_TAG.map(StatePayload::new, StatePayload::data);
    @Override public Type<StatePayload> type() { return TYPE; }
}
