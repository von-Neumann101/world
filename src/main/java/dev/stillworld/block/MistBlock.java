package dev.stillworld.block;
import dev.stillworld.server.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class MistBlock extends Block {
    private final boolean slowing;
    public MistBlock(boolean slowing) {
        super(BlockBehaviour.Properties.of().strength(-1, 3600000).noCollission().noOcclusion().noLootTable());
        this.slowing = slowing;
    }
    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (slowing && !PhaseManager.isPhased(entity)) entity.makeStuckInBlock(state, new Vec3(0.25, 0.05, 0.25));
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,net.minecraft.util.RandomSource random) {
        float shade=slowing?0.035f:0.45f;
        var dust=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(shade,shade,shade),1.8f);
        for(int i=0;i<4;i++)level.addParticle(dust,pos.getX()+random.nextDouble(),pos.getY()+random.nextDouble(),pos.getZ()+random.nextDouble(),0,0.015,0);
    }
}
