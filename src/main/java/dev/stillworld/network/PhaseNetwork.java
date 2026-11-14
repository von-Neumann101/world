package dev.stillworld.network;
import dev.stillworld.server.PhaseManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class PhaseNetwork {
    public static Consumer<CompoundTag> clientHandler = data -> {};
    public static Predicate<Entity> clientPhased = entity -> false;
    public static void register(IEventBus bus) { bus.addListener(PhaseNetwork::payloads); }
    private static void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(StatePayload.TYPE, StatePayload.CODEC, (payload, context) -> clientHandler.accept(payload.data()));
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) PhaseManager.handle(player, payload.data());
        });
    }
    public static CompoundTag message(String operation) { var tag = new CompoundTag(); tag.putString("op", operation); return tag; }
    public static void sendState(ServerPlayer player, CompoundTag data) {
        if (player.connection != null) PacketDistributor.sendToPlayer(player, new StatePayload(data));
    }
    public static void sendAction(CompoundTag data) { PacketDistributor.sendToServer(new ActionPayload(data)); }
    private PhaseNetwork() { }
}
