package dev.stillworld.mixin;
import dev.stillworld.server.PhaseManager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemEntity.class)
public abstract class PhaseItemMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void stillworld$pickup(Player player, CallbackInfo ci) { if (PhaseManager.isPhased(player)) ci.cancel(); }
}
