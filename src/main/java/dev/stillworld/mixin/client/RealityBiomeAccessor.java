package dev.stillworld.mixin.client;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BiomeManager.class)
public interface RealityBiomeAccessor {
    @Accessor("biomeZoomSeed")
    long stillworld$biomeZoomSeed();
}
