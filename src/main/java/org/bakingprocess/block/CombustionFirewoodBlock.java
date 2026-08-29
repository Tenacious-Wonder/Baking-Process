package org.bakingprocess.block;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.dfood.block.FoodBlock;
import org.bakingprocess.block.entity.CombustionFirewoodBlockEntity;
import org.bakingprocess.registry.ModBlockEntityTypes;
import org.bakingprocess.registry.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 表示正在燃烧或已经燃尽的柴火堆方块。
 * <p>由未点燃的 {@link FirewoodBlock} 点燃转化而来。{@link #COMBUSTION_STATE} 承载 7 种
 * 模型外观，用于区分首次 / 非首次 / 再次添柴与各燃尽阶段的视觉；实际的燃烧阶段与循环归属
 * 由 {@link CombustionFirewoodBlockEntity} 依据能量推导。燃尽态被右键时破坏并掉落产物，
 * 燃烧中手持柴火右键可添柴。</p>
 *
 * @see FirewoodBlock
 */
public class CombustionFirewoodBlock extends BlockWithEntity {
    public static final DirectionProperty HORIZONTAL_FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<CombustionState> COMBUSTION_STATE = EnumProperty.of("combustion_state", CombustionState.class);

    public CombustionFirewoodBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(COMBUSTION_STATE, CombustionState.FIRST_IGNITED));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return FirewoodBlock.SHAPE;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (isCompletelyExtinguished(world, pos, state)) {
            // 客户端只返回成功，服务端执行实际破坏逻辑
            if (!world.isClient()) {
                world.breakBlock(pos, false, player);
                LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder((ServerWorld) world)
                        .add(LootContextParameters.ORIGIN, pos.toCenterPos())
                        .add(LootContextParameters.TOOL, Items.AIR.getDefaultStack())
                        .addOptional(LootContextParameters.THIS_ENTITY, player);
                List<ItemStack> drops = this.getDroppedStacks(state, builder);

                for (ItemStack foodItem : drops) {
                    // 尝试放入玩家物品栏，放不下则掉落在地上
                    if (!player.isCreative() && !player.giveItemStack(foodItem)) {
                        player.dropItem(foodItem, false);
                    }
                }
            }
            return ActionResult.SUCCESS;
        }

        // 如果不是熄灭状态，检查是否手持柴火尝试添柴
        ItemStack stack = player.getStackInHand(hand);
        if (stack.getItem() == ModItems.FIREWOOD) {
            return tryAddFirewood(world, pos, player, stack);
        }

        return ActionResult.PASS;
    }

    /**
     * 检查方块是否完全熄灭
     */
    private boolean isCompletelyExtinguished(World world, BlockPos pos, BlockState state) {
        // 客户端只检查方块状态
        if (world.isClient()) {
            CombustionState combustionState = state.get(COMBUSTION_STATE);
            return combustionState == CombustionState.FIRST_EXTINGUISHED ||
                    combustionState == CombustionState.AGAIN_EXTINGUISHED;
        }

        // 服务端检查方块实体状态
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof CombustionFirewoodBlockEntity firewoodEntity) {
            return firewoodEntity.isCompletelyExtinguished();
        }

        return false;
    }

    /**
     * 尝试添柴
     */
    private ActionResult tryAddFirewood(World world, BlockPos pos, PlayerEntity player, ItemStack stack) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof CombustionFirewoodBlockEntity firewoodEntity)) {
            return ActionResult.FAIL;
        }

        boolean success = firewoodEntity.addFirewood();
        if (!success) {
            return ActionResult.FAIL;
        }

        // 消耗物品并播放音效
        if (!player.isCreative()) {
            stack.decrement(1);
        }
        world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);

        return ActionResult.SUCCESS;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        CombustionState currentState = state.get(COMBUSTION_STATE);

        // 只有在燃烧状态下才显示粒子效果和声音
        if (!currentState.isBurning()) {
            return;
        }

        if (random.nextInt(5) == 0) {
            playCrackleSound(world, pos);
        }
        if (random.nextInt(5) == 0) {
            spawnSignalSmoke(world, pos, random);
        }
        if (random.nextInt(3) == 0) {
            spawnSparkParticles(world, pos, random);
        }
        if (random.nextInt(4) == 0) {
            spawnFlameParticles(world, pos, random);
        }
    }

    /**
     * 播放营火燃烧的噼啪声
     */
    private void playCrackleSound(World world, BlockPos pos) {
        world.playSound(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                SoundEvents.BLOCK_CAMPFIRE_CRACKLE,
                SoundCategory.BLOCKS,
                1.0F,
                1.0F,
                true
        );
    }

    /**
     * 生成上升的营火信号烟雾
     */
    private void spawnSignalSmoke(World world, BlockPos pos, Random random) {
        for (int i = 0; i < random.nextInt(1) + 1; ++i) {
            world.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    pos.getX() + 0.5 + random.nextDouble() / 3.0 * (random.nextBoolean() ? 1 : -1),
                    pos.getY() + random.nextDouble() + random.nextDouble(),
                    pos.getZ() + 0.5 + random.nextDouble() / 3.0 * (random.nextBoolean() ? 1 : -1),
                    0.0, 0.07, 0.0);
        }
    }

    /**
     * 生成四溅的火星
     */
    private void spawnSparkParticles(World world, BlockPos pos, Random random) {
        for (int i = 0; i < random.nextInt(2) + 1; ++i) {
            world.addParticle(ParticleTypes.LAVA,
                    pos.getX() + 0.5 + random.nextDouble() / 4.0 * (random.nextBoolean() ? 1 : -1),
                    pos.getY() + 0.4,
                    pos.getZ() + 0.5 + random.nextDouble() / 4.0 * (random.nextBoolean() ? 1 : -1),
                    random.nextFloat() / 2.0F, 0.04, random.nextFloat() / 2.0F);
        }
    }

    /**
     * 生成跳动的小火焰
     */
    private void spawnFlameParticles(World world, BlockPos pos, Random random) {
        for (int i = 0; i < random.nextInt(2) + 1; ++i) {
            world.addParticle(ParticleTypes.FLAME,
                    pos.getX() + 0.5 + random.nextDouble() / 2.0 * (random.nextBoolean() ? 1 : -1),
                    pos.getY() + 0.2,
                    pos.getZ() + 0.5 + random.nextDouble() / 2.0 * (random.nextBoolean() ? 1 : -1),
                    0.0, 0.04, 0.0);
        }
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        CombustionState currentState = state.get(COMBUSTION_STATE);

        if (currentState.isBurning() && entity instanceof LivingEntity && !EnchantmentHelper.hasFrostWalker((LivingEntity) entity)) {
            entity.damage(world.getDamageSources().inFire(), 1);
        }

        super.onEntityCollision(state, world, pos, entity);
    }

    @Override
    public BlockState getStateForNeighborUpdate(
            BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos
    ) {
        return !state.canPlaceAt(world, pos)
                ? Blocks.AIR.getDefaultState()
                : super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos downPos = pos.down();
        BlockState checkState = world.getBlockState(downPos);
        return !checkState.isReplaceable() && !(checkState.getBlock() instanceof FoodBlock);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(COMBUSTION_STATE, HORIZONTAL_FACING);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CombustionFirewoodBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, ModBlockEntityTypes.COMBUSTION_FIREWOOD, CombustionFirewoodBlockEntity::tick);
    }

    /**
     * 重写掉落物方法 - 只在熄灭状态时掉落
     */
    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        CombustionState combustionState = state.get(COMBUSTION_STATE);
        if (!combustionState.isBurning()) {
            // 只在熄灭状态时调用父类方法生成掉落物
            return super.getDroppedStacks(state, builder);
        }
        // 非熄灭状态不掉落任何物品
        return List.of();
    }

    @Override
    public Item asItem() {
        return ModItems.FIREWOOD;
    }

    public enum CombustionState implements StringIdentifiable {
        /**
         * 3: 首次点燃 - 燃烧上面两根木棍
         */
        FIRST_IGNITED("first_ignited", 0, true, 1.0f),
        /**
         * 4: 首次燃烧过半 - 上面两根木棍碳化
         */
        FIRST_HALF("first_half", 1, true, 0.5f),
        /**
         * 4燃尽: 首次燃尽 - 完全碳化
         */
        FIRST_EXTINGUISHED("first_extinguished", 2, false, 0.0f),
        /**
         * 5: 非首次点燃 - 在碳化木棍上添加新木棍
         */
        AGAIN_IGNITED("again_ignited", 3, true, 1.0f),
        /**
         * 6: 非首次燃烧过半 - 新添加的木棍碳化
         */
        AGAIN_HALF("again_half", 4, true, 0.5f),
        /**
         * 7: 再次添柴 - 在碳化木棍上再次添加新木棍
         */
        REIGNITED("reignited", 5, true, 1.0f),
        /**
         * 6燃尽: 非首次燃尽 - 完全碳化
         */
        AGAIN_EXTINGUISHED("again_extinguished", 6, false, 0.0f);

        /** 序列化使用的字符串标识 */
        private final String id;
        /** 渲染帧索引 */
        private final int index;
        /** 是否处于燃烧状态 */
        private final boolean burning;
        /** 粒子/视觉强度提示（当前未接入渲染） */
        private final float particleIntensity;

        CombustionState(String id, int index, boolean burning, float particleIntensity) {
            this.id = id;
            this.index = index;
            this.burning = burning;
            this.particleIntensity = particleIntensity;
        }

        @Override
        public String asString() {
            return this.id;
        }

        public int getIndex() {
            return this.index;
        }

        public boolean isBurning() {
            return burning;
        }

        public float getParticleIntensity() {
            return particleIntensity;
        }

        public static CombustionState byIndex(int index) {
            for (CombustionState state : values()) {
                if (state.getIndex() == index) {
                    return state;
                }
            }
            return FIRST_IGNITED;
        }
    }
}