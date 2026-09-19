package org.bakingprocess.client.render.item.renderer;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.bakingprocess.block.BasePlatableBlock;
import org.bakingprocess.block.entity.BasePlatableBlockEntity;
import org.bakingprocess.culinary.Culinary;
import org.jetbrains.annotations.Nullable;

/**
 * <h1>可摆盘容器物品渲染器</h1>
 * <p>铁盘等 {@link BasePlatableBlock} 容器的物品形态（手持 / 物品栏 / 掉落物）渲染器，
 * 采用与石磨相同的「复用方块实体渲染器」思路：物品渲染时把数据同步到一个共享的
 * {@link BasePlatableBlockEntity}（无世界上下文），再经 {@link BlockEntityRenderDispatcher}
 * 交给 {@link org.bakingprocess.client.render.block.blockentity.BasePlatableBlockEntityRenderer}，
 * 使物品形态与方块实体渲染同源、效果一致。</p>
 *
 * <p>状态约定：物品 NBT 带菜（成品态）时按世界中「盖盖态」展示（空盘 + 铁盖
 * {@link org.bakingprocess.registry.ModItems#PLATE_LID}），不带菜时展示空盘本体。
 * 盖与内容均由方块实体渲染器统一绘制，为平底锅等后续容器复用铺路：
 * 新容器只需注册同一渲染器即可获得与铁盘一致的物品形态。</p>
 */
public final class PlatableItemRenderer {
    /** 物品渲染共用的方块实体（仅客户端渲染线程访问；每次渲染前按堆栈同步状态）。 */
    @Nullable
    private static BasePlatableBlockEntity plateEntity;

    private PlatableItemRenderer() {
    }

    /**
     * BuiltinItemRendererRegistry 的渲染入口。
     *
     * @param stack           被渲染的物品堆栈
     * @param mode            渲染模式（GUI / 手持等），仅为匹配接口签名；视角由外层物品模型 display 处理
     * @param matrices        矩阵栈
     * @param vertexConsumers 顶点消费者提供器
     * @param light           光照
     * @param overlay         覆盖纹理
     */
    @SuppressWarnings("deprecation") // 共享实体缓存需在渲染前切换方块状态，setCachedState 是唯一途径
    public static void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                              VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof BasePlatableBlock block)) {
            return;
        }

        BasePlatableBlockEntity entity = getEntity(block);
        Culinary dish = BasePlatableBlock.readCulinaryFromStack(stack);

        // 带菜成品盘（物品 NBT 有菜）与世界中盖盖态一致：只展示盘体 + 盖
        BlockState state = block.getDefaultState().with(BasePlatableBlock.IS_COVERED, dish != null);
        if (entity.getCachedState() != state) {
            entity.setCachedState(state);
        }
        syncCulinary(entity, dish);

        MinecraftClient.getInstance().getBlockEntityRenderDispatcher()
                .renderEntity(entity, matrices, vertexConsumers, light, overlay);
    }

    /** 取共享方块实体（按当前容器方块初始化；容器切换时只需更新其方块状态）。 */
    private static BasePlatableBlockEntity getEntity(BasePlatableBlock block) {
        if (plateEntity == null) {
            plateEntity = new BasePlatableBlockEntity(BlockPos.ORIGIN,
                    block.getDefaultState().with(BasePlatableBlock.IS_COVERED, false));
        }
        return plateEntity;
    }

    /** 把堆栈上的菜同步到共享方块实体（供渲染器按菜标识 / 食用阶段分派模型）。 */
    private static void syncCulinary(BasePlatableBlockEntity entity, @Nullable Culinary dish) {
        if (dish != null) {
            entity.tryAddCulinary(dish);
        } else {
            entity.clearCulinary();
        }
    }
}
