package dev.stillworld.mixin;
import dev.stillworld.server.PhaseManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CollisionGetter.class)
public interface SnapshotCollisionMixin {
    @Inject(method = "getBlockCollisions", at = @At("HEAD"), cancellable = true)
    default void stillworld$collision(Entity entity, AABB box, CallbackInfoReturnable<Iterable<VoxelShape>> cir) {
        if (entity instanceof ServerPlayer player) {
            var session = PhaseManager.get(player);
            if (session != null) cir.setReturnValue(session.snapshot().collisions(entity, box));
        }
    }
}
