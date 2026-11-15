package dev.stillworld.workshop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class StabilizerBlockEntity extends BaseContainerBlockEntity {
    public static final int PROCESSING_TICKS = 200;
    // Clockwise, starting north. The diagonal order starts northwest so both
    // Nether miniatures stay on one side, with both End miniatures opposite.
    private static final int[][] OFFSETS = {{0,-3},{3,0},{0,3},{-3,0},{-2,-2},{2,-2},{2,2},{-2,2}};
    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int progress;
    private boolean authorized;
    private boolean active;
    private int rotation = -1;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return switch (index) { case 0 -> progress; case 1 -> active ? 1 : 0; case 2 -> authorized ? 1 : 0; default -> 0; }; }
        @Override public void set(int index, int value) { }
        @Override public int getCount() { return 3; }
    };

    public StabilizerBlockEntity(BlockPos pos, BlockState state) { super(WorkshopContent.STABILIZER_ENTITY.get(), pos, state); }
    public boolean authorized() { return authorized; }
    public boolean active() { return active; }
    public int progress() { return progress; }
    public ContainerData data() { return data; }

    public boolean activate(ItemStack recorder, Player player) {
        if (level == null || level.isClientSide) return false;
        if (!AnchorRecorderItem.isFull(recorder)) {
            player.displayClientMessage(Component.translatable("message.stillworld.stabilizer.incomplete"), true);
            return false;
        }
        if (findRotation() < 0) {
            player.displayClientMessage(Component.translatable("message.stillworld.stabilizer.invalid_structure"), true);
            return false;
        }
        authorized = true;
        setChanged();
        refreshStructure();
        player.displayClientMessage(Component.translatable("message.stillworld.stabilizer.activated"), true);
        return true;
    }

    private BlockPos auxiliaryPos(int index, int rotation) {
        int x = OFFSETS[index][0], z = OFFSETS[index][1];
        for (int i = 0; i < rotation; i++) { int next = -z; z = x; x = next; }
        return worldPosition.offset(x, 0, z);
    }
    private AuxiliaryBlock.Kind expectedKind(int index) {
        return index < 4 ? AuxiliaryBlock.Kind.OVERWORLD : index < 6 ? AuxiliaryBlock.Kind.NETHER : AuxiliaryBlock.Kind.END;
    }
    private boolean matches(int rotation, boolean checkOwnership) {
        if (level == null) return false;
        for (int i = 0; i < 8; i++) {
            BlockPos pos = auxiliaryPos(i, rotation);
            if (!level.hasChunkAt(pos)) return false;
            if (!(level.getBlockState(pos).getBlock() instanceof AuxiliaryBlock block) || block.kind() != expectedKind(i)) return false;
            if (!(level.getBlockEntity(pos) instanceof AuxiliaryBlockEntity auxiliary)
                    || checkOwnership && !auxiliary.availableFor(worldPosition)) return false;
        }
        return true;
    }
    private int findRotation() {
        for (int i = 0; i < 4; i++) if (matches(i, true)) return i;
        return -1;
    }
    public boolean ownsActive(BlockPos pos) {
        if (!active || !authorized || rotation < 0 || !matches(rotation, false)) return false;
        for (int i = 0; i < 8; i++) if (auxiliaryPos(i, rotation).equals(pos)) return true;
        return false;
    }
    public boolean refreshStructure() {
        if (level == null || level.isClientSide) return false;
        int nextRotation = authorized ? findRotation() : -1;
        if (nextRotation < 0) { structureBroken(); return false; }
        if (rotation != nextRotation) clearDisplays();
        rotation = nextRotation;
        active = true;
        for (int i = 0; i < 8; i++) {
            var auxiliary = (AuxiliaryBlockEntity) level.getBlockEntity(auxiliaryPos(i, rotation));
            auxiliary.display(worldPosition, i);
        }
        return true;
    }
    /** Called synchronously on center/auxiliary replacement, preserving processing progress. */
    public void structureBroken() {
        active = false;
        rotation = -1;
        clearDisplays();
    }
    private void clearDisplays() {
        if (level == null || level.isClientSide) return;
        // All rotations share these eight positions, so clearing this footprint
        // also releases the old mapping after reorientation or a broken structure.
        for (int i = 0; i < 8; i++) {
            BlockPos pos = auxiliaryPos(i, 0);
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof AuxiliaryBlockEntity auxiliary) auxiliary.clear(worldPosition);
        }
    }
    public static void tick(Level level, BlockPos pos, BlockState state, StabilizerBlockEntity machine) {
        if (level.isClientSide || !machine.refreshStructure()) return;
        ItemStack input = machine.items.get(0), output = machine.items.get(1);
        if (!input.is(WorkshopContent.UNSTABLE_MATERIAL.get())) {
            if (machine.progress != 0) { machine.progress = 0; machine.setChanged(); }
            return;
        }
        if (!output.isEmpty() && (!output.is(WorkshopContent.STABLE_MATERIAL.get())
                || output.getCount() >= Math.min(machine.getMaxStackSize(), output.getMaxStackSize()))) return;
        if (++machine.progress >= PROCESSING_TICKS) {
            input.shrink(1);
            if (output.isEmpty()) machine.items.set(1, new ItemStack(WorkshopContent.STABLE_MATERIAL.get()));
            else output.grow(1);
            machine.progress = 0;
        }
        machine.setChanged();
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && stack.is(WorkshopContent.UNSTABLE_MATERIAL.get()); }
    @Override public int getContainerSize() { return 2; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override protected Component getDefaultName() { return Component.translatable("container.stillworld.stabilizer"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new StabilizerMenu(id, inventory, this, data); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress);
        tag.putBoolean("Authorized", authorized);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = Math.clamp(tag.getInt("Progress"), 0, PROCESSING_TICKS - 1);
        authorized = tag.getBoolean("Authorized");
        active = false;
        rotation = -1;
    }
}
