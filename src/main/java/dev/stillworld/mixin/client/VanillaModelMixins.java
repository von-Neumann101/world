package dev.stillworld.mixin.client;

import dev.stillworld.client.ModelPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Fixed visual-only species field lists; no names or field paths arrive over the network. */
public final class VanillaModelMixins {
    @Mixin(net.minecraft.world.entity.monster.Creeper.class)
    public static abstract class CreeperPose implements ModelPose {
        @Shadow private int oldSwell;
        @Shadow private int swell;
        @Shadow private int maxSwell;
        @Override public String stillworld$model() { return "creeper"; }
        @Override public float[] stillworld$captureModel() { return new float[]{oldSwell, swell, maxSwell}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 3) return;
            oldSwell = (int)v[0];
            swell = (int)v[1];
            maxSwell = (int)v[2];
        }
    }
    @Mixin(net.minecraft.world.entity.monster.Guardian.class)
    public static abstract class GuardianPose implements ModelPose {
        @Shadow private float clientSideTailAnimation;
        @Shadow private float clientSideTailAnimationO;
        @Shadow private float clientSideTailAnimationSpeed;
        @Shadow private float clientSideSpikesAnimation;
        @Shadow private float clientSideSpikesAnimationO;
        @Shadow private int clientSideAttackTime;
        @Override public String stillworld$model() { return "guardian"; }
        @Override public float[] stillworld$captureModel() { return new float[]{clientSideTailAnimation, clientSideTailAnimationO, clientSideTailAnimationSpeed, clientSideSpikesAnimation, clientSideSpikesAnimationO, clientSideAttackTime}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 6) return;
            clientSideTailAnimation = v[0];
            clientSideTailAnimationO = v[1];
            clientSideTailAnimationSpeed = v[2];
            clientSideSpikesAnimation = v[3];
            clientSideSpikesAnimationO = v[4];
            clientSideAttackTime = (int)v[5];
        }
    }
    @Mixin(net.minecraft.world.entity.animal.PolarBear.class)
    public static abstract class PolarBearPose implements ModelPose {
        @Shadow private float clientSideStandAnimationO;
        @Shadow private float clientSideStandAnimation;
        @Override public String stillworld$model() { return "polar_bear"; }
        @Override public float[] stillworld$captureModel() { return new float[]{clientSideStandAnimationO, clientSideStandAnimation}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 2) return;
            clientSideStandAnimationO = v[0];
            clientSideStandAnimation = v[1];
        }
    }
    @Mixin(net.minecraft.world.entity.animal.Wolf.class)
    public static abstract class WolfPose implements ModelPose {
        @Shadow private float interestedAngle;
        @Shadow private float interestedAngleO;
        @Shadow private float shakeAnim;
        @Shadow private float shakeAnimO;
        @Shadow private boolean isWet;
        @Shadow private boolean isShaking;
        @Override public String stillworld$model() { return "wolf"; }
        @Override public float[] stillworld$captureModel() { return new float[]{interestedAngle, interestedAngleO, shakeAnim, shakeAnimO, isWet ? 1 : 0, isShaking ? 1 : 0}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 6) return;
            interestedAngle = v[0];
            interestedAngleO = v[1];
            shakeAnim = v[2];
            shakeAnimO = v[3];
            isWet = v[4] > .5f;
            isShaking = v[5] > .5f;
        }
    }
    @Mixin(net.minecraft.world.entity.animal.Cat.class)
    public static abstract class CatPose implements ModelPose {
        @Shadow private float lieDownAmount;
        @Shadow private float lieDownAmountO;
        @Shadow private float lieDownAmountTail;
        @Shadow private float lieDownAmountOTail;
        @Shadow private float relaxStateOneAmount;
        @Shadow private float relaxStateOneAmountO;
        @Override public String stillworld$model() { return "cat"; }
        @Override public float[] stillworld$captureModel() { return new float[]{lieDownAmount, lieDownAmountO, lieDownAmountTail, lieDownAmountOTail, relaxStateOneAmount, relaxStateOneAmountO}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 6) return;
            lieDownAmount = v[0];
            lieDownAmountO = v[1];
            lieDownAmountTail = v[2];
            lieDownAmountOTail = v[3];
            relaxStateOneAmount = v[4];
            relaxStateOneAmountO = v[5];
        }
    }
    @Mixin(net.minecraft.world.entity.animal.horse.AbstractHorse.class)
    public static abstract class HorsePose implements ModelPose {
        @Shadow private float eatAnim;
        @Shadow private float eatAnimO;
        @Shadow private float standAnim;
        @Shadow private float standAnimO;
        @Shadow private float mouthAnim;
        @Shadow private float mouthAnimO;
        @Shadow public int tailCounter;
        @Shadow public int sprintCounter;
        @Override public String stillworld$model() { return "horse"; }
        @Override public float[] stillworld$captureModel() { return new float[]{eatAnim, eatAnimO, standAnim, standAnimO, mouthAnim, mouthAnimO, tailCounter, sprintCounter}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 8) return;
            eatAnim = v[0];
            eatAnimO = v[1];
            standAnim = v[2];
            standAnimO = v[3];
            mouthAnim = v[4];
            mouthAnimO = v[5];
            tailCounter = (int)v[6];
            sprintCounter = (int)v[7];
        }
    }
    @Mixin(net.minecraft.world.entity.animal.Panda.class)
    public static abstract class PandaPose implements ModelPose {
        @Shadow private float sitAmount;
        @Shadow private float sitAmountO;
        @Shadow private float onBackAmount;
        @Shadow private float onBackAmountO;
        @Shadow private float rollAmount;
        @Shadow private float rollAmountO;
        @Shadow public int rollCounter;
        @Override public String stillworld$model() { return "panda"; }
        @Override public float[] stillworld$captureModel() { return new float[]{sitAmount, sitAmountO, onBackAmount, onBackAmountO, rollAmount, rollAmountO, rollCounter}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 7) return;
            sitAmount = v[0];
            sitAmountO = v[1];
            onBackAmount = v[2];
            onBackAmountO = v[3];
            rollAmount = v[4];
            rollAmountO = v[5];
            rollCounter = (int)v[6];
        }
    }
    @Mixin(net.minecraft.world.entity.animal.Fox.class)
    public static abstract class FoxPose implements ModelPose {
        @Shadow private float interestedAngle;
        @Shadow private float interestedAngleO;
        @Shadow private float crouchAmount;
        @Shadow private float crouchAmountO;
        @Override public String stillworld$model() { return "fox"; }
        @Override public float[] stillworld$captureModel() { return new float[]{interestedAngle, interestedAngleO, crouchAmount, crouchAmountO}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 4) return;
            interestedAngle = v[0];
            interestedAngleO = v[1];
            crouchAmount = v[2];
            crouchAmountO = v[3];
        }
    }
    @Mixin(net.minecraft.world.entity.animal.Bee.class)
    public static abstract class BeePose implements ModelPose {
        @Shadow private float rollAmount;
        @Shadow private float rollAmountO;
        @Override public String stillworld$model() { return "bee"; }
        @Override public float[] stillworld$captureModel() { return new float[]{rollAmount, rollAmountO}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 2) return;
            rollAmount = v[0];
            rollAmountO = v[1];
        }
    }
    @Mixin(net.minecraft.world.entity.animal.Rabbit.class)
    public static abstract class RabbitPose implements ModelPose {
        @Shadow private int jumpTicks;
        @Shadow private int jumpDuration;
        @Override public String stillworld$model() { return "rabbit"; }
        @Override public float[] stillworld$captureModel() { return new float[]{jumpTicks, jumpDuration}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 2) return;
            jumpTicks = (int)v[0];
            jumpDuration = (int)v[1];
        }
    }
    @Mixin(net.minecraft.world.entity.animal.Sheep.class)
    public static abstract class SheepPose implements ModelPose {
        @Shadow private int eatAnimationTick;
        @Override public String stillworld$model() { return "sheep"; }
        @Override public float[] stillworld$captureModel() { return new float[]{eatAnimationTick}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 1) return;
            eatAnimationTick = (int)v[0];
        }
    }
    @Mixin(net.minecraft.world.entity.monster.Shulker.class)
    public static abstract class ShulkerPose implements ModelPose {
        @Shadow private float currentPeekAmountO;
        @Shadow private float currentPeekAmount;
        @Override public String stillworld$model() { return "shulker"; }
        @Override public float[] stillworld$captureModel() { return new float[]{currentPeekAmountO, currentPeekAmount}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 2) return;
            currentPeekAmountO = v[0];
            currentPeekAmount = v[1];
        }
    }
    @Mixin(net.minecraft.world.entity.animal.goat.Goat.class)
    public static abstract class GoatPose implements ModelPose {
        @Shadow private boolean isLoweringHead;
        @Shadow private int lowerHeadTick;
        @Override public String stillworld$model() { return "goat"; }
        @Override public float[] stillworld$captureModel() { return new float[]{isLoweringHead ? 1 : 0, lowerHeadTick}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 2) return;
            isLoweringHead = v[0] > .5f;
            lowerHeadTick = (int)v[1];
        }
    }
    @Mixin(net.minecraft.world.entity.monster.warden.Warden.class)
    public static abstract class WardenPose implements ModelPose {
        @Shadow private int tendrilAnimation;
        @Shadow private int tendrilAnimationO;
        @Shadow private int heartAnimation;
        @Shadow private int heartAnimationO;
        @Override public String stillworld$model() { return "warden"; }
        @Override public float[] stillworld$captureModel() { return new float[]{tendrilAnimation, tendrilAnimationO, heartAnimation, heartAnimationO}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 4) return;
            tendrilAnimation = (int)v[0];
            tendrilAnimationO = (int)v[1];
            heartAnimation = (int)v[2];
            heartAnimationO = (int)v[3];
        }
    }
    @Mixin(net.minecraft.world.entity.animal.armadillo.Armadillo.class)
    public static abstract class ArmadilloPose implements ModelPose {
        @Shadow private long inStateTicks;
        @Shadow private boolean peekReceivedClient;
        @Override public String stillworld$model() { return "armadillo"; }
        @Override public float[] stillworld$captureModel() { return new float[]{(float)inStateTicks, peekReceivedClient ? 1 : 0}; }
        @Override public void stillworld$applyModel(float[] v) {
            if (v.length != 2) return;
            inStateTicks = (long)v[0];
            peekReceivedClient = v[1] > .5f;
        }
    }
    private VanillaModelMixins() { }
}
