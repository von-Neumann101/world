package dev.stillworld.client;

import dev.stillworld.mixin.client.AnimationStateAccessor;
import dev.stillworld.mixin.client.LivingModelAccessor;
import dev.stillworld.mixin.client.WalkAnimationAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.player.AbstractClientPlayer;
import java.lang.reflect.Modifier;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Fixed cosmetic wire schema. Applying this data is reserved for clones, never live-world entities. */
final class ClientPose {
    private static final ClassValue<List<Field>> ANIMATIONS = new ClassValue<>() {
        @Override protected List<Field> computeValue(Class<?> type) {
            return Arrays.stream(type.getFields())
                    .filter(f -> !Modifier.isStatic(f.getModifiers()) && f.getType() == AnimationState.class && f.getDeclaringClass().getName().startsWith("net.minecraft.world.entity."))
                    .sorted(Comparator.comparing(f -> f.getDeclaringClass().getName() + "." + f.getName())).limit(32).toList();
        }
    };
    static CompoundTag capture(Entity entity) {
        var tag = new CompoundTag();
        tag.putInt("v", 1); tag.putInt("tick", entity.tickCount); tag.putString("pose", entity.getPose().name());
        tag.putBoolean("crouch", entity.isShiftKeyDown());
        if (entity instanceof LivingEntity living) {
            tag.putFloat("bodyYaw", living.yBodyRot); tag.putFloat("headYaw", living.yHeadRot);
            tag.putFloat("attack", living.attackAnim); tag.putInt("hurt", living.hurtTime); tag.putInt("death", living.deathTime);
            tag.putFloat("walkPosition", living.walkAnimation.position()); tag.putFloat("walkSpeed", living.walkAnimation.speed());
            tag.putFloat("swim", living.getSwimAmount(1)); tag.putInt("fallFly", living.getFallFlyingTicks());
            tag.putInt("useHand", living.isUsingItem() ? (living.getUsedItemHand() == InteractionHand.MAIN_HAND ? 0 : 1) : -1);
            tag.putInt("useTicks", living.getUseItemRemainingTicks());
            tag.putInt("swingHand", living.swingingArm == InteractionHand.OFF_HAND ? 1 : 0);
        }
        if (entity instanceof AbstractClientPlayer player) {
            tag.putFloat("elytraX",player.elytraRotX);tag.putFloat("elytraY",player.elytraRotY);tag.putFloat("elytraZ",player.elytraRotZ);
        }
        String model = "";
        float[] values = new float[0];
        if (entity instanceof ModelPose parts) { model = parts.stillworld$model(); values = parts.stillworld$captureModel(); }
        else if (entity instanceof Slime slime) { model = "slime"; values = new float[]{slime.targetSquish, slime.squish, slime.oSquish}; }
        else if (entity instanceof Squid squid) { model = "squid"; values = new float[]{squid.xBodyRot,squid.xBodyRotO,squid.zBodyRot,squid.zBodyRotO,
                squid.tentacleMovement,squid.oldTentacleMovement,squid.tentacleAngle,squid.oldTentacleAngle}; }
        else if (entity instanceof Chicken chicken) { model = "chicken"; values = new float[]{chicken.flap,chicken.flapSpeed,chicken.oFlapSpeed,chicken.oFlap}; }
        else if (entity instanceof Parrot parrot) { model = "parrot"; values = new float[]{parrot.flap,parrot.flapSpeed,parrot.oFlapSpeed,parrot.oFlap}; }
        else if (entity instanceof EnderDragon dragon) {
            model = "dragon"; values = new float[197];
            for (int i = 0; i < 64; i++) for (int j = 0; j < 3; j++) values[i * 3 + j] = (float)dragon.positions[i][j];
            values[192] = dragon.posPointer; values[193] = dragon.oFlapTime; values[194] = dragon.flapTime;
            values[195] = dragon.dragonDeathTime; values[196] = dragon.yRotA;
        }
        tag.putString("model", model);
        int[] bits = new int[values.length];
        for (int i = 0; i < values.length; i++) bits[i] = Float.floatToRawIntBits(values[i]);
        tag.putIntArray("values", bits);
        var fields = ANIMATIONS.get(entity.getClass());
        long[] animations = new long[fields.size() * 2];
        for (int i = 0; i < fields.size(); i++) {
            try {
                var animation = (AnimationStateAccessor)fields.get(i).get(entity);
                animations[i * 2] = animation.stillworld$lastTime(); animations[i * 2 + 1] = animation.stillworld$accumulatedTime();
            } catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot capture vanilla animation", exception); }
        }
        tag.putLongArray("animations", animations);
        return tag;
    }

    static void apply(Entity clone, CompoundTag tag) {
        if (tag.getInt("v") != 1) return;
        clone.tickCount = tag.getInt("tick");
        try { clone.setPose(Pose.valueOf(tag.getString("pose"))); } catch (IllegalArgumentException ignored) { }
        clone.setShiftKeyDown(tag.getBoolean("crouch"));
        if (clone instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = tag.getFloat("bodyYaw"); living.yHeadRot = living.yHeadRotO = tag.getFloat("headYaw");
            living.attackAnim = living.oAttackAnim = tag.getFloat("attack"); living.hurtTime = tag.getInt("hurt"); living.deathTime = tag.getInt("death");
            living.swingingArm=tag.getInt("swingHand")==1?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
            var walk = (WalkAnimationAccessor)(Object)living.walkAnimation;
            walk.stillworld$position(tag.getFloat("walkPosition")); walk.stillworld$speed(tag.getFloat("walkSpeed")); walk.stillworld$speedOld(tag.getFloat("walkSpeed"));
            var extra = (LivingModelAccessor)living;
            extra.stillworld$swim(tag.getFloat("swim")); extra.stillworld$swimOld(tag.getFloat("swim")); extra.stillworld$fallFly(tag.getInt("fallFly"));
            int hand = tag.getInt("useHand");
            if (hand == 0 || hand == 1) {
                extra.stillworld$useItem(living.getItemInHand(hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND).copy());
                extra.stillworld$useRemaining(tag.getInt("useTicks"));
                extra.stillworld$useFlag(1, true); extra.stillworld$useFlag(2, hand == 1);
            } else { extra.stillworld$useItem(ItemStack.EMPTY); extra.stillworld$useRemaining(0); extra.stillworld$useFlag(1, false); }
        }
        if (clone instanceof AbstractClientPlayer player) {
            player.elytraRotX=tag.getFloat("elytraX");player.elytraRotY=tag.getFloat("elytraY");player.elytraRotZ=tag.getFloat("elytraZ");
        }
        int[] bits = tag.getIntArray("values");
        if (bits.length > 197) return;
        float[] values = new float[bits.length];
        for (int i = 0; i < bits.length; i++) { values[i] = Float.intBitsToFloat(bits[i]); if (!Float.isFinite(values[i])) return; }
        String model = tag.getString("model");
        if (clone instanceof ModelPose parts && model.equals(parts.stillworld$model())) parts.stillworld$applyModel(values);
        else if (clone instanceof Slime slime && model.equals("slime") && values.length == 3) {
            slime.targetSquish=values[0]; slime.squish=values[1]; slime.oSquish=values[2];
        } else if (clone instanceof Squid squid && model.equals("squid") && values.length == 8) {
            squid.xBodyRot=values[0]; squid.xBodyRotO=values[1]; squid.zBodyRot=values[2]; squid.zBodyRotO=values[3];
            squid.tentacleMovement=values[4]; squid.oldTentacleMovement=values[5]; squid.tentacleAngle=values[6]; squid.oldTentacleAngle=values[7];
        } else if (clone instanceof Chicken chicken && model.equals("chicken") && values.length == 4) {
            chicken.flap=values[0]; chicken.flapSpeed=values[1]; chicken.oFlapSpeed=values[2]; chicken.oFlap=values[3];
        } else if (clone instanceof Parrot parrot && model.equals("parrot") && values.length == 4) {
            parrot.flap=values[0]; parrot.flapSpeed=values[1]; parrot.oFlapSpeed=values[2]; parrot.oFlap=values[3];
        } else if (clone instanceof EnderDragon dragon && model.equals("dragon") && values.length == 197) {
            for (int i=0;i<64;i++) for (int j=0;j<3;j++) dragon.positions[i][j]=values[i*3+j];
            dragon.posPointer=Math.clamp((int)values[192],-1,63); dragon.oFlapTime=values[193]; dragon.flapTime=values[194];
            dragon.dragonDeathTime=(int)values[195]; dragon.yRotA=values[196];
        }
        long[] animations = tag.getLongArray("animations");
        var fields = ANIMATIONS.get(clone.getClass());
        if (animations.length != fields.size() * 2) return;
        for (int i=0;i<fields.size();i++) {
            try {
                var animation = (AnimationStateAccessor)fields.get(i).get(clone);
                animation.stillworld$lastTime(animations[i*2]); animation.stillworld$accumulatedTime(animations[i*2+1]);
            } catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot restore vanilla animation", exception); }
        }
    }
    private ClientPose() { }
}
