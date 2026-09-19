package org.bakingprocess.registry;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import org.dfood.block.ComplexFoodBlock;
import org.dfood.block.FoodBlock;
import org.dfood.block.SimpleFoodBlock;
import org.dfood.shape.FoodShapeHandle;
import org.dfood.sound.ModSoundGroups;
import org.dfood.util.DFoodUtils;
import org.dfood.util.IntPropertyManager;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.block.*;

import java.util.function.BiFunction;

public class ModBlocks {
    // 工作方块
    public static final Block GRINDING_STONE = registerBlock("grinding_stone",
            new GrindingStoneBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.STONE).strength(1.5F, 6.0F)
                    .requiresTool().mapColor(MapColor.STONE_GRAY)));
    public static final Block HEAT_RESISTANT_SLATE = registerBlock("heat_resistant_slate",
            new HeatResistantSlateBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.STONE).strength(1.0F, 6.0F)
                    .requiresTool().mapColor(MapColor.DEEPSLATE_GRAY)));
    public static final Block COMBUSTION_FIREWOOD = registerBlock("combustion_firewood",
            new CombustionFirewoodBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.WOOD).strength(0.5F, 0.5F)
                    .nonOpaque().luminance(state -> state.get(CombustionFirewoodBlock.COMBUSTION_STATE).isBurning()? 15: 0)));
    public static final Block FIREWOOD = registerBlock("firewood",
            FirewoodBlock.Builder.create()
                    .maxFood(6)
                    .targetBlock(COMBUSTION_FIREWOOD)
                    .settings(AbstractBlock.Settings.create()
                            .mapColor(MapColor.BROWN).nonOpaque()
                            .sounds(BlockSoundGroup.WOOD).strength(0.5F, 0.5F))
                    .build());
    public static final Block IRON_PLATE = registerBlock("iron_plate",
            new BasePlatableBlock(AbstractBlock.Settings.create().sounds(BlockSoundGroup.METAL)
                    .strength(0.2F, 0.6F).mapColor(MapColor.IRON_GRAY)));
    public static final Block FRYING_PAN = registerBlock("frying_pan",
            new BasePlatableBlock(AbstractBlock.Settings.create().sounds(BlockSoundGroup.METAL)
                    .strength(0.2F, 0.6F).mapColor(MapColor.IRON_GRAY)));
    public static final Block GRILL = registerBlock("grill",
            new GrillBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.METAL).strength(0.5F, 0.6F)
                    .nonOpaque().mapColor(MapColor.IRON_GRAY)
                    .luminance(state -> state.get(GrillBlock.LIT) ? 15 : 0)));

    // 工具
    public static final Block IRON_GARNISH_DISHES = registerBlock("iron_garnish_dishes",
            new GarnishDishesBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.METAL).strength(0.5F)
                    .pistonBehavior(PistonBehavior.DESTROY)));
    public static final Block CUTTING_BOARD = registerBlock("cutting_board",
            new CuttingBoardBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.WOOL).strength(0.2F)
                    .pistonBehavior(PistonBehavior.DESTROY)));
    public static final Block IRON_POTS = registerBlock("iron_pots",
            new PotsBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.METAL).strength(1.5F, 0.6F)));

    // 厨具
    public static final Block BREAD_SPATULA = registerBlock("bread_spatula",
            ComplexFoodBlock.Builder.create()
                    .maxFood(1)
                    .simpleShape(Block.createCuboidShape(0, 0, 0, 16, 1, 16))
                    .settings(AbstractBlock.Settings.create()
                            .sounds(BlockSoundGroup.STONE).strength(0.5F, 0.2F)
                            .nonOpaque())
                    .build());
    public static final Block KITCHEN_KNIFE = registerBlock("kitchen_knife",
            ComplexFoodBlock.Builder.create()
                    .maxFood(1)
                    .simpleShape(Block.createCuboidShape(2, 0, 2, 14, 1, 14))
                    .settings(AbstractBlock.Settings.create()
                            .sounds(ModBlockSoundGroup.KITCHEN_KNIFE).strength(0.5F, 0.2F)
                            .nonOpaque())
                    .build());

    // 粉尘袋
    public static final Block FLOUR_SACK = registerBlock("flour_sack",
            FlourSackBlock.Builder.create()
                    .maxFood(2)
                    .settings(AbstractBlock.Settings.create()
                            .sounds(BlockSoundGroup.WOOL).strength(0.5F)
                            .nonOpaque().pistonBehavior(PistonBehavior.DESTROY))
                    .build());

    // 奶制品
    public static final Block MILK_POTION = registerBlock("milk_potion",
            FoodBlock.Builder.create()
                    .maxFood(3)
                    .settings(DFoodUtils.getFoodBlockSettings()
                            .sounds(ModSoundGroups.POTION)
                            .mapColor(MapColor.WHITE))
                    .build());

    // 面食
    public static final Block DOUGH = registerBlock("dough",
            new SimpleFoodBlock(DFoodUtils.getFoodBlockSettings()
                    .sounds(BlockSoundGroup.WOOL)
                    .mapColor(MapColor.WHITE)));
    public static final Block HARD_BREAD = registerBlock("hard_bread",
            new SimpleFoodBlock(DFoodUtils.getFoodBlockSettings()
                    .sounds(BlockSoundGroup.WOOL)
                    .mapColor(MapColor.ORANGE)));
    public static final Block SMALL_BREAD_EMBRYO = registerBlock("small_bread_embryo",
            FoodBlock.Builder.create()
                    .maxFood(3)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings()
                            .sounds(BlockSoundGroup.WOOL)
                            .mapColor(MapColor.WHITE))
                    .build());
    public static final Block SMALL_BREAD = registerBlock("small_bread",
            FoodBlock.Builder.create()
                    .maxFood(2)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings()
                            .sounds(BlockSoundGroup.WOOL)
                            .mapColor(MapColor.ORANGE))
                    .build());
    public static final Block BAGUETTE = registerBlock("baguette",
            new SimpleFoodBlock(DFoodUtils.getFoodBlockSettings()
                    .sounds(BlockSoundGroup.WOOL)
                    .mapColor(MapColor.ORANGE)));
    public static final Block BAGUETTE_EMBRYO = registerBlock("baguette_embryo",
            new SimpleFoodBlock(DFoodUtils.getFoodBlockSettings()
                    .sounds(BlockSoundGroup.WOOL)
                    .mapColor(MapColor.WHITE)));
    public static final Block TOAST = registerBlock("toast",
            new SimpleFoodBlock(DFoodUtils.getFoodBlockSettings()
                    .sounds(BlockSoundGroup.WOOL)
                    .mapColor(MapColor.ORANGE)));
    public static final Block BAKED_CAKE_EMBRYO = registerBlock("baked_cake_embryo",
            new SimpleFoodBlock(DFoodUtils.getFoodBlockSettings()
                    .sounds(BlockSoundGroup.WOOL)
                    .mapColor(MapColor.WHITE)));
    public static final Block SALTY_DOUGH = registerBlock("salty_dough",
            new SimpleFoodBlock(DFoodUtils.getFoodBlockSettings()
                    .sounds(BlockSoundGroup.WOOL)
                    .mapColor(MapColor.WHITE)));
    public static final Block SOUP_HARD_BREAD_BOAT = registerEdibleContainerBlock("soup_hard_bread_boat",
            DFoodUtils.getFoodBlockSettings().sounds(BlockSoundGroup.WOOL).mapColor(MapColor.ORANGE),
            ((settings, maxUse) -> new BreadBoatBlock(settings, FoodShapeHandle.shapes.getShape(8),
                    maxUse, ModEnforceAsItems.HARD_BREAD_BOAT)), 4);
    public static final Block HARD_BREAD_BOAT = registerBlock("hard_bread_boat",
            new EmptyBreadBoatBlock(DFoodUtils.getFoodBlockSettings()
                    .sounds(BlockSoundGroup.WOOL).mapColor(MapColor.ORANGE),
                    (BreadBoatBlock) SOUP_HARD_BREAD_BOAT));

    // 切片食物
    public static final Block CARROT_SLICES = registerBlock("carrot_slices",
            FoodBlock.Builder.create()
                    .maxFood(3)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings())
                    .build());
    public static final Block BEETROOT_SLICES = registerBlock("beetroot_slices",
            FoodBlock.Builder.create()
                    .maxFood(1)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings())
                    .build());
    public static final Block SEPARATE_POTATO_CUBES = registerBlock("separate_potato_cubes",
            FoodBlock.Builder.create()
                    .maxFood(4)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings())
                    .build());
    public static final Block POTATO_CUBES = registerBlock("potato_cubes",
            FoodBlock.Builder.create()
                    .maxFood(1)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings())
                    .build());
    public static final Block SEPARATE_BAKED_POTATO_CUBES = registerBlock("separate_baked_potato_cubes",
            FoodBlock.Builder.create()
                    .maxFood(4)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings())
                    .build());
    public static final Block BAKED_POTATO_CUBES = registerBlock("baked_potato_cubes",
            FoodBlock.Builder.create()
                    .maxFood(1)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings())
                    .build());
    public static final Block APPLE_SLICES = registerBlock("apple_slices",
            FoodBlock.Builder.create()
                    .maxFood(2)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings())
                    .build());
    public static final Block COD_CUBES = registerBlock("cod_cubes",
            FoodBlock.Builder.create()
                    .maxFood(4)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings()
                            .sounds(ModSoundGroups.FISH))
                    .build());
    public static final Block COOKED_COD_CUBES = registerBlock("cooked_cod_cubes",
            FoodBlock.Builder.create()
                    .maxFood(4)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings()
                            .sounds(ModSoundGroups.FISH))
                    .build());
    public static final Block SALMON_CUBES = registerBlock("salmon_cubes",
            FoodBlock.Builder.create()
                    .maxFood(3)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings()
                            .sounds(ModSoundGroups.FISH))
                    .build());
    public static final Block COOKED_SALMON_CUBES = registerBlock("cooked_salmon_cubes",
            FoodBlock.Builder.create()
                    .maxFood(3)
                    .useItemTranslationKey(false)
                    .settings(DFoodUtils.getFoodBlockSettings()
                            .sounds(ModSoundGroups.FISH))
                    .build());
    public static final Block KITCHEN_WASTE = registerBlock("kitchen_waste",
            new SimpleFoodBlock(DFoodUtils.getFoodBlockSettings()
                    .sounds(ModSoundGroups.FISH)));

    // 模具
    public static final Block CAKE_EMBRYO_MOLD = registerBlock("cake_embryo_mold",
            new MoldBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.METAL).strength(0.5F).nonOpaque()));
    public static final Block TOAST_EMBRYO_MOLD = registerBlock("toast_embryo_mold",
            new MoldBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.METAL).strength(0.5F).nonOpaque()));

    // 调料
    public static final Block SALT_CUBES = registerBlock("salt_cubes",
            FoodBlock.Builder.create()
                    .maxFood(2)
                    .useItemTranslationKey(true)
                    .settings(DFoodUtils.getFoodBlockSettings())
                    .build());

    // 矿物
    public static final Block SALT_ORE = registerBlock("salt_ore",
            new ExperienceDroppingBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.STONE).strength(1.5F, 6.0F).requiresTool()));
    public static final Block DEEPSLATE_SALT_ORE = registerBlock("deepslate_salt_ore",
            new ExperienceDroppingBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.STONE).strength(2.0F, 6.0F).requiresTool()));

    // 园艺联动
    public static final Block CLAY_POTS_EMBRYO = registerBlock("clay_pots_embryo",
            new SimpleFoodBlock(DFoodUtils.getFoodBlockSettings(), false, PotsBlock.SHAPE));
    public static final Block CLAY_POTS = registerBlock("clay_pots",
            new PotsBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.STONE).strength(0.5F, 0.6F)));

    public static Block registerBlock(String name, Block block) {
        return Registry.register(Registries.BLOCK, new Identifier(BakingProcess.MOD_ID, name), block);
    }

    public static Block registerEdibleContainerBlock(String name, AbstractBlock.Settings settings,
                                                     BiFunction<AbstractBlock.Settings, Integer, BreadBoatBlock> blockCreator,
                                                     int maxUse) {
        IntPropertyManager.preCache("bites", 0, maxUse);
        Block block = blockCreator.apply(settings, maxUse);
        return registerBlock(name, block);
    }

    public static void registerAll() {}
}