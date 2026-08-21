package com.hekuo.swillvariants;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SwillBucketVariants implements ModInitializer {
    public static final String MOD_ID = "swill_bucket_variants";
    public static final Map<String, SwillBucketBlock> BLOCKS = new LinkedHashMap<>();

    public static final SwillBucketBlock SWILL_BUCKET = register("swill_bucket", Oxidation.UNAFFECTED, false);
    public static final SwillBucketBlock EXPOSED = register("exposed_swill_bucket", Oxidation.EXPOSED, false);
    public static final SwillBucketBlock WEATHERED = register("weathered_swill_bucket", Oxidation.WEATHERED, false);
    public static final SwillBucketBlock OXIDIZED = register("oxidized_swill_bucket", Oxidation.OXIDIZED, false);
    public static final SwillBucketBlock WAXED = register("waxed_swill_bucket", Oxidation.UNAFFECTED, true);
    public static final SwillBucketBlock WAXED_EXPOSED = register("waxed_exposed_swill_bucket", Oxidation.EXPOSED, true);
    public static final SwillBucketBlock WAXED_WEATHERED = register("waxed_weathered_swill_bucket", Oxidation.WEATHERED, true);
    public static final SwillBucketBlock WAXED_OXIDIZED = register("waxed_oxidized_swill_bucket", Oxidation.OXIDIZED, true);

    public static BlockEntityType<SwillBucketBlockEntity> BLOCK_ENTITY;

    private static SwillBucketBlock register(String name, Oxidation oxidation, boolean waxed) {
        SwillBucketBlock block = new SwillBucketBlock(AbstractBlock.Settings.create()
                .mapColor(MapColor.ORANGE).strength(2.0F, 6.0F).sounds(BlockSoundGroup.COPPER)
                .requiresTool().ticksRandomly(), oxidation, waxed);
        BLOCKS.put(name, block);
        Registry.register(Registries.BLOCK, id(name), block);
        Registry.register(Registries.ITEM, id(name), new BlockItem(block, new Item.Settings()));
        return block;
    }

    @Override
    public void onInitialize() {
        BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("swill_bucket"),
                FabricBlockEntityTypeBuilder.create(SwillBucketBlockEntity::new,
                        BLOCKS.values().toArray(Block[]::new)).build());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries ->
                BLOCKS.values().forEach(entries::add));
    }

    public static Identifier id(String path) { return Identifier.of(MOD_ID, path); }

    public enum Oxidation { UNAFFECTED, EXPOSED, WEATHERED, OXIDIZED }

    public static SwillBucketBlock version(Oxidation oxidation, boolean waxed) {
        return switch (oxidation) {
            case UNAFFECTED -> waxed ? WAXED : SWILL_BUCKET;
            case EXPOSED -> waxed ? WAXED_EXPOSED : EXPOSED;
            case WEATHERED -> waxed ? WAXED_WEATHERED : WEATHERED;
            case OXIDIZED -> waxed ? WAXED_OXIDIZED : OXIDIZED;
        };
    }
}
