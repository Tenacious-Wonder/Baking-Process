package org.bakingprocess.integration.dfood;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.FoodComponents;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import org.dfood.replace.FoodBlocks;
import org.dfood.sound.ModSoundGroups;
import org.dfood.util.IntPropertyManager;
import org.bakingprocess.BakingProcess;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;

public class AssistedBlocks {
    public static Set<Block> assistedBlocks = new HashSet<>();

    // 汤类
    public static final Block CRIPPLED_RABBIT_STEW = registerAssistedStewBlock("crippled_rabbit_stew",
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.BROWN).strength(0.1F, 0.1F).nonOpaque()
                    .sounds(BlockSoundGroup.DECORATED_POT).pistonBehavior(PistonBehavior.DESTROY),
            (settings, maxUse) -> new CrippledStewBlock(settings, maxUse, FoodComponents.RABBIT_STEW, FoodBlocks.RABBIT_STEW));

    public static final Block CRIPPLED_MUSHROOM_STEW = registerAssistedStewBlock("crippled_mushroom_stew",
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.BROWN).strength(0.1F, 0.1F).nonOpaque()
                    .sounds(BlockSoundGroup.DECORATED_POT).pistonBehavior(PistonBehavior.DESTROY),
            (settings, maxUse) -> new CrippledStewBlock(settings, maxUse, FoodComponents.MUSHROOM_STEW, FoodBlocks.MUSHROOM_STEW));

    public static final Block CRIPPLED_BEETROOT_SOUP = registerAssistedStewBlock("crippled_beetroot_soup",
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.BROWN).strength(0.1F, 0.1F).nonOpaque()
                    .sounds(BlockSoundGroup.DECORATED_POT).pistonBehavior(PistonBehavior.DESTROY),
            (settings, maxUse) -> new CrippledStewBlock(settings, maxUse, FoodComponents.BEETROOT_SOUP, FoodBlocks.BEETROOT_SOUP));

    public static final Block CRIPPLED_SUSPICIOUS_STEW = registerAssistedStewBlock("crippled_suspicious_stew",
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.BROWN).strength(0.1F, 0.1F).nonOpaque()
                    .sounds(BlockSoundGroup.DECORATED_POT).pistonBehavior(PistonBehavior.DESTROY),
            (settings, maxUse) -> new CrippledSuspiciousStewBlock(settings, maxUse, FoodComponents.SUSPICIOUS_STEW, FoodBlocks.SUSPICIOUS_STEW));

    // 桶类
    public static final Block CRIPPLED_WATER_BUCKET = registerAssistedBlock("crippled_water_bucket",
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.BLUE).strength(0.2F, 0.2F).nonOpaque()
                    .sounds(ModSoundGroups.WATER_BUCKET).pistonBehavior(PistonBehavior.DESTROY),
            (settings, maxUse) -> new CrippledBucketBlock(settings, maxUse, FoodBlocks.WATER_BUCKET, Potions.WATER), 3);
    public static final Block CRIPPLED_MILK_BUCKET = registerAssistedBlock("crippled_milk_bucket",
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.BLUE).strength(0.2F, 0.2F).nonOpaque()
                    .sounds(ModSoundGroups.WATER_BUCKET).pistonBehavior(PistonBehavior.DESTROY),
            (settings, maxUse) -> new CrippledBucketBlock(settings, maxUse, FoodBlocks.MILK_BUCKET, null), 3);

    public static Block registerAssistedStewBlock(String name, AbstractBlock.Settings settings,
                                                  BiFunction<AbstractBlock.Settings, Integer, Block> blockCreator) {
        return registerAssistedBlock(name, settings, blockCreator, 4);
    }

    /**
     * 注册残缺的方块
     * @param name 方块id
     * @param settings 方块设置
     * @param blockCreator 残缺方块的构造函数
     * @param maxUse 最大使用次数
     * @return 注册后的方块
     */
    public static Block registerAssistedBlock(String name, AbstractBlock.Settings settings,
                                              BiFunction<AbstractBlock.Settings, Integer, Block> blockCreator, int maxUse) {
        IntPropertyManager.preCache("number_of_use", maxUse);
        Block block = blockCreator.apply(settings, maxUse);
        assistedBlocks.add(block);
        return Registry.register(Registries.BLOCK, new Identifier(BakingProcess.MOD_ID, name), block);
    }

    public static void registerAssistedBlocks() {}
}