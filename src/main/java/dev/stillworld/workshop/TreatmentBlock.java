package dev.stillworld.workshop;

import dev.stillworld.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class TreatmentBlock extends Block implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN=BlockStateProperties.OPEN;
    public static final BooleanProperty ACTIVE=BlockStateProperties.POWERED;
    private static final VoxelShape[][][] SHAPES=new VoxelShape[4][2][2];
    static {
        for(Direction facing:Direction.Plane.HORIZONTAL)for(int half=0;half<2;half++)for(int open=0;open<2;open++) {
            VoxelShape shape=half==0?box(0,0,0,16,1,16):box(0,15,0,16,16,16);
            for(Direction side:Direction.Plane.HORIZONTAL) {
                if(open==1&&side==facing)continue;
                shape=Shapes.or(shape,switch(side) {
                    case NORTH->box(0,0,0,16,16,1);
                    case SOUTH->box(0,0,15,16,16,16);
                    case WEST->box(0,0,0,1,16,16);
                    default->box(15,0,0,16,16,16);
                });
            }
            SHAPES[facing.get2DDataValue()][half][open]=shape.optimize();
        }
    }
    public TreatmentBlock() {
        super(Properties.of().strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(HALF,DoubleBlockHalf.LOWER).setValue(FACING,Direction.NORTH).setValue(OPEN,false).setValue(ACTIVE,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {builder.add(HALF,FACING,OPEN,ACTIVE);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var pos=context.getClickedPos();
        if(pos.getY()>=context.getLevel().getMaxBuildHeight()-1||!context.getLevel().getBlockState(pos.above()).canBeReplaced(context))return null;
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity player,ItemStack stack) {level.setBlock(pos.above(),state.setValue(HALF,DoubleBlockHalf.UPPER),3);}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        if(state.getValue(HALF)==DoubleBlockHalf.UPPER)return level.getBlockState(pos.below()).is(this)&&level.getBlockState(pos.below()).getValue(HALF)==DoubleBlockHalf.LOWER;
        return level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP);
    }
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other) {
        var half=state.getValue(HALF);
        if(side.getAxis()==Direction.Axis.Y&&((half==DoubleBlockHalf.LOWER)==(side==Direction.UP))) {
            return neighbor.is(this)&&neighbor.getValue(HALF)!=half?neighbor.setValue(HALF,half):Blocks.AIR.defaultBlockState();
        }
        return half==DoubleBlockHalf.LOWER&&side==Direction.DOWN&&!state.canSurvive(level,pos)?Blocks.AIR.defaultBlockState():super.updateShape(state,side,neighbor,level,pos,other);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if(!level.isClientSide&&state.getValue(HALF)==DoubleBlockHalf.UPPER&&(player.isCreative()||!player.hasCorrectToolForDrops(state,level,pos))) {
            var bottom=pos.below();var lower=level.getBlockState(bottom);
            if(lower.is(this)&&lower.getValue(HALF)==DoubleBlockHalf.LOWER)level.setBlock(bottom,Blocks.AIR.defaultBlockState(),35);
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof TreatmentBlockEntity be)be.abort();
        super.onRemove(state,level,pos,next,moving);
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {return SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(HALF)==DoubleBlockHalf.LOWER?0:1][state.getValue(OPEN)?1:0];}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {return getShape(state,level,pos,context);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation) {return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror) {return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {return state.getValue(HALF)==DoubleBlockHalf.LOWER?new TreatmentBlockEntity(pos,state):null;}
    @Override @SuppressWarnings("unchecked") public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return !level.isClientSide&&state.getValue(HALF)==DoubleBlockHalf.LOWER&&type==WorkshopContent.TREATMENT_ENTITY.get()?(world,pos,s,be)->TreatmentBlockEntity.tick(world,pos,s,(TreatmentBlockEntity)be):null;
    }
    public static TreatmentBlockEntity at(Level level,BlockPos pos) {
        var state=level.getBlockState(pos);
        if(!state.is(ModContent.TREATMENT_MACHINE.get()))return null;
        return level.getBlockEntity(state.getValue(HALF)==DoubleBlockHalf.UPPER?pos.below():pos) instanceof TreatmentBlockEntity be?be:null;
    }
    public static boolean isTreating(Player player) {
        var be=at(player.level(),player.blockPosition());
        return be!=null&&be.isOccupant(player);
    }
    /** Abort before capturing the phase snapshot so its door is not frozen shut around the player. */
    public static void beforePhaseEntry(Player player) {
        var be=at(player.level(),player.blockPosition());if(be!=null&&be.isOccupant(player))be.abort();
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(stack.is(WorkshopContent.ANCHOR_RECORDER.get())) {
            if(!level.isClientSide) {var be=at(level,pos);if(be!=null)be.activate(stack,player);}
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if(stack.is(WorkshopContent.STABLE_MATERIAL.get())) {
            if(!level.isClientSide) { var be=at(level,pos); if(be==null)return ItemInteractionResult.FAIL;
                if(be.addCharge()) {stack.shrink(1);player.displayClientMessage(Component.translatable("message.stillworld.treatment_charge",be.charge()),true);}
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!level.isClientSide) {var be=at(level,pos);if(be!=null)be.toggleDoor(player);}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
