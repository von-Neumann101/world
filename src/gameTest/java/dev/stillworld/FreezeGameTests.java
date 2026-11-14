package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.mixin.CreeperSnapshotAccessor;
import dev.stillworld.server.PhaseManager;
import dev.stillworld.server.PhaseSkills;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class FreezeGameTests {
    @GameTest(template = "empty")
    public static void creeperRewindsFuseAndClearsLaterIgnition(GameTestHelper h) {
        var p = SessionGameTests.player(h);
        SessionGameTests.equip(p, Rank.RANK4);
        var creeper = h.spawnWithNoFreeWill(EntityType.CREEPER, new BlockPos(5, 2, 3));
        creeper.setNoGravity(true);
        var fuse = (CreeperSnapshotAccessor) creeper;
        fuse.stillworld$swell(5);
        fuse.stillworld$oldSwell(4);
        fuse.stillworld$maxSwell(40);
        fuse.stillworld$explosionRadius(4);
        creeper.setSwellDir(1);
        creeper.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        PhaseManager.enter(p);
        try {
            fuse.stillworld$swell(20);
            fuse.stillworld$oldSwell(19);
            fuse.stillworld$maxSwell(80);
            fuse.stillworld$explosionRadius(8);
            creeper.setSwellDir(-1);
            creeper.ignite();
            creeper.getEntityData().set(CreeperSnapshotAccessor.stillworld$poweredData(), true);
            creeper.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
            SkillGameTests.aim(p, creeper.getBoundingBox().getCenter());
            h.assertTrue(PhaseSkills.mark(PhaseManager.get(p), creeper.getUUID()), "captured creeper can be marked");
            h.assertTrue(fuse.stillworld$swell() == 5, "mark rewinds actual fuse from 20 to entry progress 5");
            h.assertTrue(fuse.stillworld$oldSwell() == 4, "mark restores previous fuse interpolation progress");
            h.assertTrue(fuse.stillworld$maxSwell() == 40 && fuse.stillworld$explosionRadius() == 4,
                    "mark restores entry fuse length and explosion radius");
            h.assertTrue(creeper.getSwellDir() == 1, "mark restores entry swelling direction");
            h.assertTrue(!creeper.isIgnited() && !creeper.isPowered(), "mark clears ignition and charge acquired after entry");
            h.assertTrue(creeper.getMainHandItem().is(Items.DIAMOND), "mark does not rewind equipment");
            h.succeed();
        } finally {
            PhaseManager.exit(p);
            creeper.discard();
        }
    }

    @GameTest(template = "empty")
    public static void ignitedCreeperAndEffectFreezeWhileWorldAdvances(GameTestHelper h) {
        var p = SessionGameTests.player(h);
        SessionGameTests.equip(p, Rank.RANK4);
        var creeper = h.spawnWithNoFreeWill(EntityType.CREEPER, new BlockPos(5, 2, 3));
        creeper.setNoGravity(true);
        var fuse = (CreeperSnapshotAccessor) creeper;
        fuse.stillworld$swell(5);
        fuse.stillworld$oldSwell(4);
        creeper.ignite();
        creeper.getEntityData().set(CreeperSnapshotAccessor.stillworld$poweredData(), true);
        creeper.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400));
        PhaseManager.enter(p);
        SkillGameTests.aim(p, creeper.getBoundingBox().getCenter());
        h.assertTrue(PhaseSkills.mark(PhaseManager.get(p), creeper.getUUID()), "ignited entry creeper can be marked");
        long time = h.getLevel().getGameTime();
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(fuse.stillworld$swell() == 5 && fuse.stillworld$oldSwell() == 4,
                        "canceled entity ticks keep actual and previous fuse progress frozen");
                h.assertTrue(creeper.isIgnited() && creeper.isPowered(), "entry ignition and powered state survive freezing");
                h.assertTrue(creeper.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() == 400,
                        "frozen target effect duration does not decrease");
                h.assertTrue(h.getLevel().getGameTime() >= time + 6, "real world time keeps advancing");
            } finally {
                PhaseManager.exit(p);
            }
            h.runAfterDelay(3, () -> {
                try {
                    h.assertTrue(fuse.stillworld$swell() > 5 && fuse.stillworld$swell() <= 9,
                            "exit resumes the old fuse rather than restarting or using later progress");
                    h.assertTrue(creeper.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() < 400,
                            "exit resumes effect countdown");
                    h.succeed();
                } finally {
                    creeper.discard();
                }
            });
        });
    }

    @GameTest(template = "empty")
    public static void rank4DamageSurvivesFrozenTicksAndInvulnerabilityCountsDown(GameTestHelper h) {
        var p = SessionGameTests.player(h);
        SessionGameTests.equip(p, Rank.RANK4);
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
        var cow = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(5, 2, 3));
        cow.setNoGravity(true);
        PhaseManager.enter(p);
        SkillGameTests.aim(p, cow.getBoundingBox().getCenter());
        h.assertTrue(PhaseSkills.mark(PhaseManager.get(p), cow.getUUID()), "rank4 target can be marked");
        h.assertTrue(PhaseSkills.attack(PhaseManager.get(p), cow.getUUID()), "rank4 attack damages a frozen target");
        float damagedHealth = cow.getHealth();
        int invulnerability = cow.invulnerableTime;
        h.assertTrue(damagedHealth < cow.getMaxHealth() && invulnerability > 0, "attack creates damage and temporary immunity");
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(cow.getHealth() == damagedHealth, "frozen ticks must not reload entry health over rank4 damage");
                h.assertTrue(cow.invulnerableTime < invulnerability, "hit invulnerability still counts down while frozen");
                h.succeed();
            } finally {
                PhaseManager.exit(p);
                cow.discard();
            }
        });
    }

    @GameTest(template = "empty")
    public static void witherRewindsOwnInvulnerabilityCountdown(GameTestHelper h) {
        var p = SessionGameTests.player(h);
        SessionGameTests.equip(p, Rank.RANK4);
        var wither = h.spawnWithNoFreeWill(EntityType.WITHER, new BlockPos(5, 2, 3));
        wither.setNoGravity(true);
        wither.setInvulnerableTicks(100);
        PhaseManager.enter(p);
        try {
            wither.setInvulnerableTicks(20);
            SkillGameTests.aim(p, wither.getBoundingBox().getCenter());
            h.assertTrue(PhaseSkills.mark(PhaseManager.get(p), wither.getUUID()), "captured wither can be marked");
            h.assertTrue(wither.getInvulnerableTicks() == 100, "mark restores wither entry Invul countdown");
            h.succeed();
        } finally {
            PhaseManager.exit(p);
            wither.discard();
        }
    }
}
