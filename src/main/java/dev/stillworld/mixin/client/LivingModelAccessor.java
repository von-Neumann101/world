package dev.stillworld.mixin.client;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(LivingEntity.class)
public interface LivingModelAccessor {
    @Accessor("swimAmount") void stillworld$swim(float value);
    @Accessor("swimAmountO") void stillworld$swimOld(float value);
    @Accessor("fallFlyTicks") void stillworld$fallFly(int value);
    @Accessor("useItemRemaining") void stillworld$useRemaining(int value);
    @Accessor("useItem") void stillworld$useItem(ItemStack stack);
    @Invoker("setLivingEntityFlag") void stillworld$useFlag(int flag, boolean value);
}
