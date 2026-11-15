package dev.stillworld.workshop;

import dev.stillworld.Stillworld;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** The order is the persisted bit order and the auxiliary display contract. */
public enum BiomeAnchor {
    MOUNTAIN(Level.OVERWORLD, "mountain"),
    ARID(Level.OVERWORLD, "arid"),
    OCEAN(Level.OVERWORLD, "ocean"),
    FOREST(Level.OVERWORLD, "forest"),
    WARPED_FOREST(Level.NETHER, "warped_forest"),
    CRIMSON_FOREST(Level.NETHER, "crimson_forest"),
    THE_END(Level.END, "the_end"),
    END_HIGHLANDS(Level.END, "end_highlands");

    private final ResourceKey<Level> dimension;
    private final String id;
    private final TagKey<Biome> tag;

    BiomeAnchor(ResourceKey<Level> dimension, String id) {
        this.dimension = dimension;
        this.id = id;
        this.tag = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Stillworld.ID, "anchor/" + id));
    }

    public boolean matches(ResourceKey<Level> dimension, Holder<Biome> biome) {
        return this.dimension.equals(dimension) && biome.is(tag);
    }
    public int bit() { return 1 << ordinal(); }
    public String translationKey() { return "anchor.stillworld." + id; }
}
