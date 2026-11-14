package dev.stillworld.network;

import dev.stillworld.Stillworld;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.*;

import java.util.Objects;
import java.util.UUID;

/** The original world's terrain updates, kept separate from the frozen client stream. */
public record RealityPayload(UUID session, Packet<?> packet) implements CustomPacketPayload {
    public static final Type<RealityPayload> TYPE = new Type<>(Stillworld.id("reality"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RealityPayload> CODEC = StreamCodec.ofMember(RealityPayload::write, RealityPayload::read);

    public RealityPayload {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(packet, "packet");
        if (!supported(packet)) throw new IllegalArgumentException("Unsupported reality packet: " + packet.getClass().getName());
    }

    public static boolean supported(Packet<?> packet) {
        return switch (packet) {
            case ClientboundLevelChunkWithLightPacket ignored -> true;
            case ClientboundBlockUpdatePacket ignored -> true;
            case ClientboundSectionBlocksUpdatePacket ignored -> true;
            case ClientboundLightUpdatePacket ignored -> true;
            case ClientboundBlockEntityDataPacket ignored -> true;
            case ClientboundBlockEventPacket ignored -> true;
            case ClientboundSetTimePacket ignored -> true;
            case ClientboundForgetLevelChunkPacket ignored -> true;
            case ClientboundGameEventPacket event -> event.getEvent() == ClientboundGameEventPacket.START_RAINING
                    || event.getEvent() == ClientboundGameEventPacket.STOP_RAINING
                    || event.getEvent() == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE
                    || event.getEvent() == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE;
            case null, default -> false;
        };
    }

    private static RealityPayload read(RegistryFriendlyByteBuf buffer) {
        UUID session = buffer.readUUID();
        Packet<?> packet = switch (buffer.readUnsignedByte()) {
            case 0 -> ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(buffer);
            case 1 -> ClientboundBlockUpdatePacket.STREAM_CODEC.decode(buffer);
            case 2 -> ClientboundSectionBlocksUpdatePacket.STREAM_CODEC.decode(buffer);
            case 3 -> ClientboundLightUpdatePacket.STREAM_CODEC.decode(buffer);
            case 4 -> ClientboundBlockEntityDataPacket.STREAM_CODEC.decode(buffer);
            case 5 -> ClientboundBlockEventPacket.STREAM_CODEC.decode(buffer);
            case 6 -> ClientboundSetTimePacket.STREAM_CODEC.decode(buffer);
            case 7 -> ClientboundGameEventPacket.STREAM_CODEC.decode(buffer);
            case 8 -> ClientboundForgetLevelChunkPacket.STREAM_CODEC.decode(buffer);
            default -> throw new DecoderException("Unknown reality packet type");
        };
        if (!supported(packet)) throw new DecoderException("Unsupported reality game event");
        return new RealityPayload(session, packet);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(session);
        switch (packet) {
            case ClientboundLevelChunkWithLightPacket value -> { buffer.writeByte(0); ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(buffer, value); }
            case ClientboundBlockUpdatePacket value -> { buffer.writeByte(1); ClientboundBlockUpdatePacket.STREAM_CODEC.encode(buffer, value); }
            case ClientboundSectionBlocksUpdatePacket value -> { buffer.writeByte(2); ClientboundSectionBlocksUpdatePacket.STREAM_CODEC.encode(buffer, value); }
            case ClientboundLightUpdatePacket value -> { buffer.writeByte(3); ClientboundLightUpdatePacket.STREAM_CODEC.encode(buffer, value); }
            case ClientboundBlockEntityDataPacket value -> { buffer.writeByte(4); ClientboundBlockEntityDataPacket.STREAM_CODEC.encode(buffer, value); }
            case ClientboundBlockEventPacket value -> { buffer.writeByte(5); ClientboundBlockEventPacket.STREAM_CODEC.encode(buffer, value); }
            case ClientboundSetTimePacket value -> { buffer.writeByte(6); ClientboundSetTimePacket.STREAM_CODEC.encode(buffer, value); }
            case ClientboundGameEventPacket value -> { buffer.writeByte(7); ClientboundGameEventPacket.STREAM_CODEC.encode(buffer, value); }
            case ClientboundForgetLevelChunkPacket value -> { buffer.writeByte(8); ClientboundForgetLevelChunkPacket.STREAM_CODEC.encode(buffer, value); }
            default -> throw new IllegalArgumentException("Unsupported reality packet");
        }
    }

    @Override public Type<RealityPayload> type() { return TYPE; }
}
