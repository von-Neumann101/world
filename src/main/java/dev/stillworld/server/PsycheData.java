package dev.stillworld.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Saved on the player and copied on death; timers count online server ticks. */
public final class PsycheData implements INBTSerializable<CompoundTag> {
    public int energy=100;
    public double healthScale=1;
    public int cooldownTicks;
    public int doomTicks=-1;

    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        var tag=new CompoundTag();
        tag.putInt("energy",energy);tag.putDouble("healthScale",healthScale);
        tag.putInt("cooldownTicks",cooldownTicks);tag.putInt("doomTicks",doomTicks);
        return tag;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider,CompoundTag tag) {
        energy=tag.contains("energy")?Math.clamp(tag.getInt("energy"),0,100):100;
        double scale=tag.contains("healthScale")?tag.getDouble("healthScale"):1;
        healthScale=Double.isFinite(scale)?Math.clamp(scale,0,1):1;
        cooldownTicks=Math.clamp(tag.getInt("cooldownTicks"),0,1400);
        doomTicks=tag.contains("doomTicks")?Math.clamp(tag.getInt("doomTicks"),-1,800):-1;
    }
}
