package dev.stillworld.client;

import dev.stillworld.mixin.client.AnimationStateAccessor;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * NBT omits vanilla model progress (creeper swell, squid tentacles, horse rear, slime squish,
 * dragon flight-history arrays, and others). Copy only values owned by vanilla entity classes
 * into an isolated visual clone. Field objects survive runtime name remapping; names are never looked up.
 * No gameplay object, controller, inventory, capability or relation is shared with the live entity.
 */
final class VanillaAnimationSnapshot {
    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
        @Override protected List<Field> computeValue(Class<?> type) {
            var result = new ArrayList<Field>();
            for (var owner = type; owner != null && owner != Entity.class; owner = owner.getSuperclass()) {
                if (!owner.getName().startsWith("net.minecraft.world.entity.")) continue;
                for (var field : owner.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    var value = field.getType();
                    boolean animation = value == AnimationState.class;
                    boolean scalar = value.isPrimitive() && !Modifier.isFinal(field.getModifiers());
                    boolean array = value.isArray() && primitiveArray(value);
                    boolean item = value == ItemStack.class && !Modifier.isFinal(field.getModifiers());
                    if (animation || scalar || array || item) { field.setAccessible(true); result.add(field); }
                }
            }
            return List.copyOf(result);
        }
    };
    private static boolean primitiveArray(Class<?> type) {
        while (type.isArray()) type = type.getComponentType();
        return type.isPrimitive();
    }
    private static Object copyArray(Object value) {
        if (value == null) return null;
        int size = Array.getLength(value);
        var result = Array.newInstance(value.getClass().getComponentType(), size);
        if (value.getClass().getComponentType().isArray()) {
            for (int i = 0; i < size; i++) Array.set(result, i, copyArray(Array.get(value, i)));
        } else System.arraycopy(value, 0, result, 0, size);
        return result;
    }
    static void copy(Entity source, Entity target) {
        if (source.getType() != target.getType()) return;
        var synced = source.getEntityData().getNonDefaultValues();
        if (synced != null) target.getEntityData().assignValues(synced);
        for (var field : FIELDS.get(source.getClass())) {
            if (!field.getDeclaringClass().isInstance(target)) continue;
            try {
                var value = field.get(source);
                if (value instanceof AnimationState) {
                    var from = (AnimationStateAccessor)value;
                    var to = (AnimationStateAccessor)field.get(target);
                    to.stillworld$lastTime(from.stillworld$lastTime());
                    to.stillworld$accumulatedTime(from.stillworld$accumulatedTime());
                } else if (field.getType().isArray()) {
                    if (Modifier.isFinal(field.getModifiers())) {
                        var destination = field.get(target);
                        if (value != null && destination != null && Array.getLength(value) == Array.getLength(destination)) {
                            var copied = copyArray(value);
                            System.arraycopy(copied, 0, destination, 0, Array.getLength(destination));
                        }
                    } else field.set(target, copyArray(value));
                }
                else if (value instanceof ItemStack stack) field.set(target, stack.copy());
                else field.set(target, value);
            } catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot preserve vanilla model progress", exception); }
        }
    }
    private VanillaAnimationSnapshot() { }
}
