package dev.stillworld.workshop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class AuxiliaryBlock extends Block implements EntityBlock {
    public enum Kind { OVERWORLD, NETHER, END }
    private final Kind kind;
    public AuxiliaryBlock(Kind kind) {
        super(BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops().noOcclusion());
        this.kind = kind;
    }
    public Kind kind() { return kind; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AuxiliaryBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != WorkshopContent.AUXILIARY_ENTITY.get()) return null;
        return (l, p, s, be) -> AuxiliaryBlockEntity.tick(l, p, s, (AuxiliaryBlockEntity) be);
    }
    @Override protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!oldState.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof AuxiliaryBlockEntity auxiliary) auxiliary.invalidateOwner();
        super.onRemove(oldState, level, pos, newState, moving);
    }
}
