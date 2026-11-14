package dev.stillworld.server;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class EntityVisual {
    public static CompoundTag capture(Entity entity) {
        var result = new CompoundTag();
        result.putUUID("uuid", entity.getUUID());
        result.putInt("entityId", entity.getId());
        var saved = new CompoundTag();
        entity.saveWithoutId(saved);
        saved.putString("id", EntityType.getKey(entity.getType()).toString());
        saved.remove("Passengers");
        result.put("entity", saved);
        result.putInt("tick", entity.tickCount);
        result.putString("pose", entity.getPose().name());
        if (entity instanceof Player player) {
            var publicData = new CompoundTag();
            for (String key : new String[]{"id", "UUID", "Pos", "Motion", "Rotation", "OnGround", "Health", "HurtTime", "DeathTime", "Air", "Fire"})
                if (saved.contains(key)) publicData.put(key, saved.get(key).copy());
            var visibleInventory = new net.minecraft.nbt.ListTag();
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                if (slot == net.minecraft.world.entity.EquipmentSlot.BODY || player.getItemBySlot(slot).isEmpty()) continue;
                var item = (CompoundTag) player.getItemBySlot(slot).saveOptional(player.registryAccess());
                int index = slot == net.minecraft.world.entity.EquipmentSlot.MAINHAND ? 0
                        : slot == net.minecraft.world.entity.EquipmentSlot.OFFHAND ? 150 : 100 + slot.getIndex();
                item.putByte("Slot", (byte) index); visibleInventory.add(item);
            }
            publicData.put("Inventory", visibleInventory); publicData.putInt("SelectedItemSlot", 0);
            result.put("entity", publicData);
            result.putBoolean("player", true);
            result.putString("name", player.getGameProfile().getName());
        }
        if (entity instanceof LivingEntity living) {
            result.putFloat("bodyYaw", living.yBodyRot);
            result.putFloat("headYaw", living.yHeadRot);
            result.putFloat("attack", living.attackAnim);
            result.putInt("hurt", living.hurtTime);
            result.putInt("death", living.deathTime);
            result.putFloat("walkPosition", living.walkAnimation.position());
            result.putFloat("walkSpeed", living.walkAnimation.speed());
        }
        return result;
    }
    private EntityVisual() { }
}
