package org.bakingprocess.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.block.pattern.BlockPatternBuilder;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.bakingprocess.block.entity.CombustionFirewoodBlockEntity;
import org.bakingprocess.block.entity.HeatResistantSlateBlockEntity;
import org.bakingprocess.registry.ModBlockEntityTypes;
import org.bakingprocess.registry.ModBlocks;
import org.bakingprocess.registry.ModItems;
import org.bakingprocess.registry.ModSounds;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.block.UpPlaceBlock;
import org.twcore.api.block.UpPlaceBlockEntity;
import org.twcore.api.blockvolume.BlockRange;
import org.twcore.api.blockvolume.BlockVolume;
import org.twcore.api.blockvolume.BlockVolumeChangeType;
import org.twcore.api.blockvolume.BlockVolumeRegistry;

import java.util.function.Predicate;

public class HeatResistantSlateBlock extends UpPlaceBlock {
    protected static final VoxelShape BASE_SHAPE = Block.createCuboidShape(0,0,0,16,2,16);

    public static final BlockPattern stove1x1;
    public static final BlockPattern stove1x2;
    public static final BlockPattern stove2x2;
    public static final BlockPattern stove2x3;

    public HeatResistantSlateBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof Inventory inventory) {
                ItemScatterer.spawn(world, pos, inventory);
                world.updateComparators(pos, this);
            }
        }

        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockEntity blockEntity = world.getBlockEntity(pos);

        // 调用父类交互方法
        ActionResult result = super.onUse(state, world, pos, player, hand, hit);

        // 如果交互失败，则尝试交互绑定的篝火
        if (!result.isAccepted() && blockEntity instanceof HeatResistantSlateBlockEntity heatResistantSlateBlockEntity) {
            for (CombustionFirewoodBlockEntity firewoodEntity : heatResistantSlateBlockEntity.getFirewoodEntities()) {
                BlockState firewoodState = firewoodEntity.getCachedState();
                ActionResult firewoodResult = firewoodState.getBlock().onUse(firewoodState, world, firewoodEntity.getPos(), player, hand, hit);
                if (firewoodResult.isAccepted()) {
                    return firewoodResult;
                }
            }
        }

        return result;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof HeatResistantSlateBlockEntity heatResistantSlateBlockEntity
                    && heatResistantSlateBlockEntity.isBaking()) {
            playBakingEffects(world, pos, pos, random);
        }
    }

    /**
     * 烘烤表现：从烹饪表面冒出蒸汽粒子，并随机播放烹饪声。
     * 由正在烘烤的方块（耐热石板自身、烤架）在 {@code randomDisplayTick} 中调用，
     * 调用方需先确认其方块实体处于烘烤中（{@code isBaking()}）。
     *
     * @param world    客户端世界
     * @param soundPos 声音播放位置（设备方块自身）
     * @param steamPos 蒸汽冒出的基准位置（烹饪表面；烤架为其上方盘子所在层）
     * @param random   随机源
     */
    public static void playBakingEffects(World world, BlockPos soundPos, BlockPos steamPos, Random random) {
        if (random.nextInt(5) == 0) {
            for (int i = 0; i < random.nextInt(1) + 1; ++i) {
                world.addParticle(ParticleTypes.CLOUD,
                        steamPos.getX() + 0.5 + random.nextDouble() / 3.0 * (random.nextBoolean() ? 1 : -1),
                        steamPos.getY() + random.nextDouble() + random.nextDouble(),
                        steamPos.getZ() + 0.5 + random.nextDouble() / 3.0 * (random.nextBoolean() ? 1 : -1),
                        0.0, 0.07, 0.0);
            }
        }
        if (random.nextInt(5) == 0) {
            world.playSound(
                    soundPos.getX() + 0.5,
                    soundPos.getY() + 0.5,
                    soundPos.getZ() + 0.5,
                    ModSounds.COOKING_SOUND,
                    SoundCategory.BLOCKS,
                    1.0F,
                    1.0F,
                    true
            );
        }
    }

    @Override
    public VoxelShape getBaseShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return BASE_SHAPE;
    }

    @Override
    public boolean canFetched(UpPlaceBlockEntity blockEntity, ItemStack handStack) {
        if (blockEntity.isEmpty()) {
            return false;
        }
        // 平底锅带长柄：台面高温时可直接空手端起；其余台面物品需用面包铲
        if (blockEntity.getStack(0).getItem() == ModItems.FRYING_PAN) {
            return true;
        }
        return handStack.getItem().equals(ModItems.BREAD_SPATULA);
    }

    @Override
    public boolean canPlace(UpPlaceBlockEntity blockEntity, ItemStack handStack) {
        return blockEntity.isValidItem(handStack);
    }

    @Nullable
    public static BlockPattern getStovePattern(int index){
        switch (index) {
            case 1 -> {
                return stove1x1;
            }
            case 2 -> {
                return stove1x2;
            }
            case 3 -> {
                return stove2x2;
            }
            case 4 -> {
                return stove2x3;
            }
        }
        return null;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new HeatResistantSlateBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, (BlockEntityType<? extends HeatResistantSlateBlockEntity>) ModBlockEntityTypes.HEAT_RESISTANT_SLATE, HeatResistantSlateBlockEntity::tick);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    /**
     * 方块体变化事件回调：结构增删时刷新该结构范围内所有耐热石板方块实体的结构快照。
     */
    private static void onBlockVolumeChanged(ServerWorld world, BlockVolume volume, BlockVolumeChangeType type) {
        if (volume.baseBlock() != ModBlocks.HEAT_RESISTANT_SLATE) {
            return;
        }
        BlockRange range = volume.range();
        BlockPos start = range.start();
        BlockPos end = range.end();
        for (int x = start.getX(); x <= end.getX(); x++) {
            for (int y = start.getY(); y <= end.getY(); y++) {
                for (int z = start.getZ(); z <= end.getZ(); z++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    // 未加载区块内的方块实体不可读（且读取会触发区块加载），跳过
                    if (!world.isChunkLoaded(checkPos)) {
                        continue;
                    }
                    if (world.getBlockEntity(checkPos) instanceof HeatResistantSlateBlockEntity blockEntity) {
                        blockEntity.refreshBlockVolume();
                    }
                }
            }
        }
    }

    static {
        // 监听方块体变化事件：结构增删时刷新受影响的耐热石板方块实体（替代周期轮询发现结构变化）
        BlockVolumeRegistry.CHANGE.register(HeatResistantSlateBlock::onBlockVolumeChanged);

        Predicate<CachedBlockPosition> heatResistantSlatePredicate = cachedBlockPosition -> cachedBlockPosition.getBlockState().getBlock() instanceof HeatResistantSlateBlock;
        Predicate<CachedBlockPosition> firewoodPredicate = cachedBlockPosition -> cachedBlockPosition.getBlockState().isAir() ||
                cachedBlockPosition.getBlockState().getBlock() instanceof FirewoodBlock ||
                cachedBlockPosition.getBlockState().getBlock() instanceof CombustionFirewoodBlock;

        stove1x1 = BlockPatternBuilder.start()
                .aisle("?#?", "#|#")
                .aisle("#^#","#~#")
                .aisle("?#?", "?#?")
                .where('^', cachedBlockPosition -> cachedBlockPosition.getBlockState().isAir())
                .where('#', cachedBlockPosition -> !cachedBlockPosition.getBlockState().isAir())
                .where('|', heatResistantSlatePredicate)
                .where('~', firewoodPredicate)
                .where('?', cachedBlockPosition -> true)
                .build();
        stove1x2 = BlockPatternBuilder.start()
                .aisle("?#?", "#|#")
                .aisle("?#?", "#|#")
                .aisle("#^#","#~#")
                .aisle("?#?", "?#?")
                .where('^', cachedBlockPosition -> cachedBlockPosition.getBlockState().isAir())
                .where('#', cachedBlockPosition -> !cachedBlockPosition.getBlockState().isAir())
                .where('|', heatResistantSlatePredicate)
                .where('~', firewoodPredicate)
                .where('?', cachedBlockPosition -> true)
                .build();
        stove2x2 = BlockPatternBuilder.start()
                .aisle("?##?", "#||#")
                .aisle("?##?", "#||#")
                .aisle("#^^#","#~~#")
                .aisle("?##?", "?##?")
                .where('^', cachedBlockPosition -> cachedBlockPosition.getBlockState().isAir())
                .where('#', cachedBlockPosition -> !cachedBlockPosition.getBlockState().isAir())
                .where('|', heatResistantSlatePredicate)
                .where('~', firewoodPredicate)
                .where('?', cachedBlockPosition -> true)
                .build();
        stove2x3 = BlockPatternBuilder.start()
                .aisle("?##?", "#||#")
                .aisle("?##?", "#||#")
                .aisle("?##?", "#||#")
                .aisle("#^^#","#~~#")
                .aisle("?##?", "?##?")
                .where('^', cachedBlockPosition -> cachedBlockPosition.getBlockState().isAir())
                .where('#', cachedBlockPosition -> !cachedBlockPosition.getBlockState().isAir())
                .where('|', heatResistantSlatePredicate)
                .where('~', firewoodPredicate)
                .where('?', cachedBlockPosition -> true)
                .build();
    }
}