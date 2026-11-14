package dev.stillworld.mixin;
import dev.stillworld.snapshot.SnapshotReadContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Level.class)
public abstract class SnapshotReadMixin {
    @Inject(method="getBlockState",at=@At("HEAD"),cancellable=true)
    private void stillworld$state(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        var world=SnapshotReadContext.current(); if(world!=null && world.level()==(Object)this) cir.setReturnValue(world.getBlockState(pos));
    }
    @Inject(method="getFluidState",at=@At("HEAD"),cancellable=true)
    private void stillworld$fluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        var world=SnapshotReadContext.current(); if(world!=null && world.level()==(Object)this) cir.setReturnValue(world.getFluidState(pos));
    }
    @Inject(method="getBlockEntity",at=@At("HEAD"),cancellable=true)
    private void stillworld$blockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        var world=SnapshotReadContext.current(); if(world!=null && world.level()==(Object)this) cir.setReturnValue(world.getBlockEntity(pos));
    }
}
