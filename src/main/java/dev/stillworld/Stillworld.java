package dev.stillworld;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.minecraft.resources.ResourceLocation;
import dev.stillworld.network.PhaseNetwork;
import dev.stillworld.server.PhaseManager;

@Mod(Stillworld.ID)
public final class Stillworld {
    public static final String ID = "stillworld";
    public Stillworld(IEventBus bus) { ModContent.register(bus); PhaseNetwork.register(bus); PhaseManager.register(); }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
}
