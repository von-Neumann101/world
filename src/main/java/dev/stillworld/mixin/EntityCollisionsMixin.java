package dev.stillworld.mixin;
import dev.stillworld.server.PhaseManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(EntityGetter.class)
public interface EntityCollisionsMixin {
    @Inject(method = "getEntityCollisions", at = @At("HEAD"), cancellable = true)
    default void stillworld$entityCollision(Entity entity, AABB box, CallbackInfoReturnable<List<VoxelShape>> cir) {
        if (entity != null && PhaseManager.isPhased(entity)) cir.setReturnValue(List.of());
    }
}
