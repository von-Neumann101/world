package dev.stillworld;

import dev.stillworld.workshop.*;
import dev.stillworld.server.PsycheManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class TreatmentGameTests {
    private static TreatmentBlockEntity chamber(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(3,2,3));
        h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        var state=ModContent.TREATMENT_MACHINE.get().defaultBlockState().setValue(TreatmentBlock.OPEN,true);
        h.getLevel().setBlock(pos,state,3);
        h.getLevel().setBlock(pos.above(),state.setValue(TreatmentBlock.HALF,DoubleBlockHalf.UPPER),3);
        return (TreatmentBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    private static ServerPlayer patient(GameTestHelper h,TreatmentBlockEntity be) {
        var p=SessionGameTests.player(h);var pos=be.getBlockPos();p.setPos(pos.getX()+.5,pos.getY()+.0625,pos.getZ()+.5);
        h.getLevel().addNewPlayer(p);return p;
    }
    private static ItemStack fullRecorder() {
        var stack=new ItemStack(WorkshopContent.ANCHOR_RECORDER.get());stack.set(WorkshopContent.ANCHOR_VISITS.get(),255);return stack;
    }
    private static void advance(TreatmentBlockEntity be,int count) {
        for(int i=0;i<count;i++)TreatmentBlockEntity.tick(be.getLevel(),be.getBlockPos(),be.getBlockState(),be);
    }
    private static void close(GameTestHelper h,double value,double expected,String message) {h.assertTrue(Math.abs(value-expected)<.001,message+": "+value+" expected "+expected);}
    @GameTest(template="empty") public static void treatmentIsTwoBlockChamber(GameTestHelper h) {
        var state=ModContent.TREATMENT_MACHINE.get().defaultBlockState();
        h.assertTrue(state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF),"Treatment machine must have lower and upper halves");
        h.assertTrue(state.hasProperty(BlockStateProperties.OPEN),"Treatment machine must have an operable door");
        h.succeed();
    }
    @GameTest(template="empty") public static void partialTreatmentWaitsTenSecondsAndSpendsOnlyCharge(GameTestHelper h) {
        var be=chamber(h);var p=patient(h,be);
        try {
            var recorder=fullRecorder();
            h.assertTrue(!be.activate(new ItemStack(WorkshopContent.ANCHOR_RECORDER.get()),p),"Partial anchor cannot activate treatment");
            h.assertTrue(be.activate(recorder,p)&&recorder.getCount()==1&&AnchorRecorderItem.isFull(recorder),"Full recorder activates without consumption");
            PsycheManager.loseFraction(p,.5);p.setHealth(6);PsycheManager.setEnergy(p,37);
            h.assertTrue(!be.begin(p),"Empty charge cannot start");be.addCharge();
            advance(be,1);
            h.assertTrue(be.isOccupant(p)&&!be.getBlockState().getValue(TreatmentBlock.OPEN),"Entering automatically closes and starts chamber");
            p.setShiftKeyDown(true);h.assertTrue(!p.isShiftKeyDown(),"Patient cannot crouch");
            advance(be,199);close(h,p.getMaxHealth(),10,"No early heal at199 ticks");h.assertTrue(be.charge()==4,"Charge not spent before completion");
            advance(be,1);close(h,p.getMaxHealth(),14,"One material supplies4HP of partial repair");
            h.assertTrue(be.charge()==0&&!be.isOccupant(p)&&be.getBlockState().getValue(TreatmentBlock.OPEN),"Completion releases player and spends4");
            close(h,p.getHealth(),6,"Current health unchanged");h.assertTrue(PsycheManager.data(p).energy==37,"Psyche unchanged");
            p.setShiftKeyDown(true);h.assertTrue(p.isShiftKeyDown(),"Crouching restored after treatment");
        } finally {p.discard();}
        h.succeed();
    }
    @GameTest(template="empty") public static void completeTreatmentPreservesOtherModifiersAndUnusedCharge(GameTestHelper h) {
        var be=chamber(h);var p=patient(h,be);
        try {
            var bonus=Stillworld.id("treatment_test_bonus");p.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(bonus,10,AttributeModifier.Operation.ADD_VALUE));
            PsycheManager.loseFraction(p,.2); // 30 ->24; two materials store8,6spent
            be.activate(fullRecorder(),p);be.addCharge();be.addCharge();h.assertTrue(be.begin(p),"Funded patient starts");advance(be,200);
            close(h,p.getMaxHealth(),30,"All Stillworld loss restored");close(h,PsycheManager.data(p).healthScale,1,"Penalty removed");
            h.assertTrue(p.getAttribute(Attributes.MAX_HEALTH).getModifier(bonus)!=null&&be.charge()==2,"Unrelated bonus and surplus charge retained");
            h.assertTrue(!be.begin(p),"Healthy player cannot consume charge");
            var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());var copy=new TreatmentBlockEntity(be.getBlockPos(),be.getBlockState());copy.loadWithComponents(saved,h.getLevel().registryAccess());
            h.assertTrue(copy.activated()&&copy.charge()==2&&!copy.isOccupant(p),"Activation and remaining charge persist, occupancy does not");
        } finally {p.discard();}
        h.succeed();
    }
    @GameTest(template="empty") public static void interruptionDoesNotHealOrConsumeAndUpperRemovalReleases(GameTestHelper h) {
        var be=chamber(h);var p=patient(h,be);
        try {
            be.activate(fullRecorder(),p);be.addCharge();PsycheManager.loseFraction(p,.5);h.assertTrue(be.begin(p),"Starts");advance(be,80);
            p.setPos(p.position().add(2,0,0));advance(be,1);
            h.assertTrue(!be.isOccupant(p)&&be.charge()==4&&be.getBlockState().getValue(TreatmentBlock.OPEN),"Leaving interrupts without cost");close(h,p.getMaxHealth(),10,"No heal on interruption");
            var pos=be.getBlockPos();p.setPos(pos.getX()+.5,pos.getY()+.0625,pos.getZ()+.5);h.assertTrue(be.begin(p),"Can restart");
            h.getLevel().destroyBlock(pos.above(),false);
            h.assertTrue(h.getLevel().getBlockState(pos).isAir()&&!TreatmentBlock.isTreating(p),"Removing upper half removes lower and releases crouch lock");
            close(h,p.getMaxHealth(),10,"Breaking does not heal");
        } finally {p.discard();}
        h.succeed();
    }
    @GameTest(template="empty") public static void partialFractionRepairAndDeathCountdownRemainIndependent(GameTestHelper h) {
        var p=SessionGameTests.player(h);PsycheManager.loseFraction(p,.43);PsycheManager.data(p).doomTicks=600;PsycheManager.data(p).cooldownTicks=900;
        h.assertTrue(TreatmentHealing.repair(p,3)==3,"Partial charge cost");close(h,p.getMaxHealth(),14.4,"Partial healing preserves fractional remainder");
        h.assertTrue(TreatmentHealing.repair(p,8)==6,"Final fractional HP rounds charge upward");close(h,p.getMaxHealth(),20,"Full repair");
        h.assertTrue(PsycheManager.data(p).doomTicks==600&&PsycheManager.data(p).cooldownTicks==900,"Repair only affects maximum health, not existing timers");h.succeed();
    }
    @GameTest(template="empty") public static void reloadReleasesPatientWithoutRestartingTreatment(GameTestHelper h) {
        var be=chamber(h);var p=patient(h,be);
        try {
            be.activate(fullRecorder(),p);be.addCharge();PsycheManager.loseFraction(p,.5);h.assertTrue(be.begin(p),"Starts before reload");advance(be,75);
            var pos=be.getBlockPos();var state=be.getBlockState();var tag=be.saveWithoutMetadata(h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(pos);var loaded=new TreatmentBlockEntity(pos,state);loaded.loadWithComponents(tag,h.getLevel().registryAccess());h.getLevel().setBlockEntity(loaded);
            advance(loaded,240);
            h.assertTrue(!loaded.isOccupant(p)&&loaded.getBlockState().getValue(TreatmentBlock.OPEN),"Reload opens door and does not relock the still-inside patient");
            close(h,p.getMaxHealth(),10,"Reload never finishes the interrupted treatment");h.assertTrue(loaded.charge()==4,"Reload consumes no charge");
            p.setPos(p.position().add(2,0,0));advance(loaded,1);p.setPos(pos.getX()+.5,pos.getY()+.0625,pos.getZ()+.5);advance(loaded,1);
            h.assertTrue(loaded.isOccupant(p),"Leaving and entering permits a fresh treatment");loaded.abort();
        } finally {p.discard();}
        h.succeed();
    }
}
