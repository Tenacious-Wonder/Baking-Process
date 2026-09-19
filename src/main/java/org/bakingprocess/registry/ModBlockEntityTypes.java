package org.bakingprocess.registry;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.block.entity.*;

public class ModBlockEntityTypes {
    // 工作方块
    public static final BlockEntityType<GrindingStoneBlockEntity> GRINDING_STONE = create("grinding_stone",
            BlockEntityType.Builder.create(
                    GrindingStoneBlockEntity::new,
                    ModBlocks.GRINDING_STONE
            )
    );
    public static final BlockEntityType<PotsBlockEntity> POTS = create("pots",
            BlockEntityType.Builder.create(
                    PotsBlockEntity::new,
                    ModBlocks.IRON_POTS,
                    ModBlocks.CLAY_POTS
            )
    );
    public static final BlockEntityType<BasePlatableBlockEntity> PLATE = create("plate",
            BlockEntityType.Builder.create(
                    BasePlatableBlockEntity::new,
                    ModBlocks.IRON_PLATE,
                    ModBlocks.FRYING_PAN
            )
    );
    public static final BlockEntityType<GrillBlockEntity> GRILL = create("grill",
            BlockEntityType.Builder.create(
                    GrillBlockEntity::new,
                    ModBlocks.GRILL
            )
    );

    // UpPlaceBlock
    public static final BlockEntityType<HeatResistantSlateBlockEntity> HEAT_RESISTANT_SLATE = create("heat_resistant_slate",
            BlockEntityType.Builder.create(
                    HeatResistantSlateBlockEntity::new,
                    ModBlocks.HEAT_RESISTANT_SLATE
            )
    );
    public static final BlockEntityType<DishesBlockEntity> GARNISH_DISHES = create("garnish_dishes",
            BlockEntityType.Builder.create(
                    DishesBlockEntity::new,
                    ModBlocks.IRON_GARNISH_DISHES
            )
    );
    public static final BlockEntityType<MoldBlockEntity> MOLD = create("mold",
            BlockEntityType.Builder.create(
                    MoldBlockEntity::new,
                    ModBlocks.CAKE_EMBRYO_MOLD,
                    ModBlocks.TOAST_EMBRYO_MOLD
            )
    );
    public static final BlockEntityType<CuttingBoardBlockEntity> CUTTING_BOARD = create("cutting_board",
            BlockEntityType.Builder.create(
                    CuttingBoardBlockEntity::new,
                    ModBlocks.CUTTING_BOARD
            )
    );

    // FoodBlock
    public static final BlockEntityType<FlourSackBlockEntity> FLOUR_SACK = create("flour_sack",
            BlockEntityType.Builder.create(
                    FlourSackBlockEntity::new,
                    ModBlocks.FLOUR_SACK
            )
    );

    // 其他
    public static final BlockEntityType<CombustionFirewoodBlockEntity> COMBUSTION_FIREWOOD = create("combustion_firewood",
            BlockEntityType.Builder.create(
                    CombustionFirewoodBlockEntity::new,
                    ModBlocks.COMBUSTION_FIREWOOD
            )
    );

    private static <T extends BlockEntity> BlockEntityType<T> create(String id, BlockEntityType.Builder<T> builder) {
        return Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(BakingProcess.MOD_ID, id), builder.build(null));
    }

    public static void registerAll() {}
}
