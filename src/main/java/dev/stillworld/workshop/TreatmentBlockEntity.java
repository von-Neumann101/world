package dev.stillworld.workshop;

import dev.stillworld.server.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import java.util.UUID;

public final class TreatmentBlockEntity extends BlockEntity {
    public static final int TREATMENT_TICKS=200;
    private boolean activated;
    private int charge;
    private UUID occupant;
    private int progress;
    // Completion/abort leaves the door open and waits for the patient to leave before another cycle.
    private UUID released;
    public TreatmentBlockEntity(BlockPos pos,BlockState state) {super(WorkshopContent.TREATMENT_ENTITY.get(),pos,state);}
    public boolean activated() {return activated;}
    public int charge() {return charge;}
    public int progress() {return progress;}
    public boolean isOccupant(Player player) {return player.getUUID().equals(occupant);}
    public boolean activate(ItemStack stack,Player player) {
        if(!AnchorRecorderItem.isFull(stack)) {player.displayClientMessage(Component.translatable("message.stillworld.need_full_anchor"),true);return false;}
        activated=true;changed();player.displayClientMessage(Component.translatable("message.stillworld.treatment_activated"),true);return true;
    }
    public boolean addCharge() {
        if(charge>Integer.MAX_VALUE-4)return false;
        charge+=4;changed();return true;
    }
    public void toggleDoor(Player player) {
        if(occupant!=null)return;
        setDoor(!getBlockState().getValue(TreatmentBlock.OPEN),false);
        player.displayClientMessage(Component.translatable(activated?"message.stillworld.treatment_charge":"message.stillworld.need_full_anchor",charge),true);
    }
    private boolean inside(Player player) {
        var b=player.getBoundingBox();
        return b.minX>=worldPosition.getX()+.06&&b.maxX<=worldPosition.getX()+.94
                &&b.minZ>=worldPosition.getZ()+.06&&b.maxZ<=worldPosition.getZ()+.94
                &&player.getY()>=worldPosition.getY()&&player.getY()<worldPosition.getY()+.4;
    }
    public boolean begin(ServerPlayer player) {
        if(level==null||!activated||occupant!=null||charge<=0||!player.isAlive()||player.isSpectator()||player.isPassenger()
                ||PhaseManager.get(player)!=null||!inside(player)||TreatmentHealing.missing(player)<=1e-6)return false;
        if(!level.getBlockState(worldPosition.above()).is(getBlockState().getBlock()))return false;
        occupant=player.getUUID();progress=0;released=null;player.setShiftKeyDown(false);
        setDoor(false,true);changed();return true;
    }
    public void abort() {
        if(occupant!=null)released=occupant;
        occupant=null;progress=0;
        if(level!=null&&!level.isClientSide)setDoor(true,false);
        changed();
    }
    private void setDoor(boolean open,boolean treating) {
        if(level==null)return;
        var state=level.getBlockState(worldPosition);
        if(!state.is(getBlockState().getBlock())||!state.hasProperty(TreatmentBlock.OPEN))return;
        boolean moved=state.getValue(TreatmentBlock.OPEN)!=open;
        level.setBlock(worldPosition,state.setValue(TreatmentBlock.OPEN,open).setValue(TreatmentBlock.ACTIVE,treating),3);
        var upper=level.getBlockState(worldPosition.above());
        if(upper.is(state.getBlock()))level.setBlock(worldPosition.above(),state.setValue(TreatmentBlock.HALF,DoubleBlockHalf.UPPER).setValue(TreatmentBlock.OPEN,open).setValue(TreatmentBlock.ACTIVE,treating),3);
        if(moved)level.playSound(null,worldPosition,open?SoundEvents.IRON_DOOR_OPEN:SoundEvents.IRON_DOOR_CLOSE,SoundSource.BLOCKS,.6f,1);
    }
    public static void tick(Level level,BlockPos pos,BlockState state,TreatmentBlockEntity be) {
        if(be.occupant!=null) {
            var found=level.getPlayerByUUID(be.occupant);
            if(!(found instanceof ServerPlayer player)||!player.isAlive()||player.isRemoved()||!be.inside(player)||PhaseManager.get(player)!=null) {be.abort();return;}
            player.setShiftKeyDown(false);
            if(++be.progress>=TREATMENT_TICKS) {
                int spent=TreatmentHealing.repair(player,be.charge);be.charge-=spent;
                be.abort();player.displayClientMessage(Component.translatable("message.stillworld.treatment_complete",spent),true);
            }
            return;
        }
        // A loaded in-progress chamber has no live patient/timer; release safely rather than resume a stale lock.
        if(state.getValue(TreatmentBlock.ACTIVE))be.setDoor(true,false);
        if(be.released!=null) {
            var player=level.getPlayerByUUID(be.released);
            if(player!=null) {
                if(!be.inside(player)) {be.released=null;be.setChanged();}
                else return;
            }
        }
        if(!be.activated||be.charge<=0||!be.getBlockState().getValue(TreatmentBlock.OPEN))return;
        for(var player:level.getEntitiesOfClass(ServerPlayer.class,new AABB(pos).expandTowards(0,1,0)))if(be.begin(player))break;
    }
    private void changed() {
        setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);tag.putBoolean("activated",activated);tag.putInt("charge",charge);
        // Keep only a release guard, never a resumable treatment lock or timer.
        UUID lastPatient=occupant!=null?occupant:released;
        if(lastPatient!=null)tag.putUUID("ReleasedPatient",lastPatient);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);activated=tag.getBoolean("activated");charge=Math.max(0,tag.getInt("charge"));
        occupant=tag.hasUUID("patient")?tag.getUUID("patient"):null;
        progress=0;released=tag.hasUUID("ReleasedPatient")?tag.getUUID("ReleasedPatient"):null;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag=saveWithoutMetadata(registries);if(occupant!=null)tag.putUUID("patient",occupant);return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {return ClientboundBlockEntityDataPacket.create(this);}
}
