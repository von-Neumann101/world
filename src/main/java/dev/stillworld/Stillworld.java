package dev.stillworld;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.minecraft.resources.ResourceLocation;

@Mod(Stillworld.ID)
public final class Stillworld {
    public static final String ID = "stillworld";
    public Stillworld(IEventBus bus) { ModContent.register(bus); }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
}
