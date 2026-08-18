package org.bakingprocess.client.render.item.replacer;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import org.dfood.util.DFoodUtils;
import org.bakingprocess.block.PlateBlock;
import org.twcore.client.api.render.ReplaceItemModel;

import java.util.Objects;

public class PlateModelReplacer {

    public static BakedModel ReplaceModel(ReplaceItemModel.ReplaceContext context) {
        // 手持盘子的菜数据在自定义 NBT 中（Culinary），有菜时显示带盖模型
        if (PlateBlock.readCulinaryFromStack(context.stack()) != null) {
            BlockState renderState = Objects.requireNonNull(DFoodUtils.getBlockStateFromItem(context.stack().getItem()))
                    .with(PlateBlock.IS_COVERED, true);

            return context.modelManager().getBlockModels().getModel(renderState);
        }

        return context.originalModel();
    }
}
