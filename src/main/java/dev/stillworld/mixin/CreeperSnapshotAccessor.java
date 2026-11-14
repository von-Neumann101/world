package dev.stillworld.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface CreeperSnapshotAccessor {
    @Accessor("swell") int stillworld$swell();
    @Accessor("swell") void stillworld$swell(int value);
    @Accessor("oldSwell") int stillworld$oldSwell();
    @Accessor("oldSwell") void stillworld$oldSwell(int value);
    @Accessor("maxSwell") int stillworld$maxSwell();
    @Accessor("maxSwell") void stillworld$maxSwell(int value);
    @Accessor("explosionRadius") int stillworld$explosionRadius();
    @Accessor("explosionRadius") void stillworld$explosionRadius(int value);

    @Accessor("DATA_IS_IGNITED")
    static EntityDataAccessor<Boolean> stillworld$ignitedData() { throw new AssertionError(); }

    @Accessor("DATA_IS_POWERED")
    static EntityDataAccessor<Boolean> stillworld$poweredData() { throw new AssertionError(); }
}
