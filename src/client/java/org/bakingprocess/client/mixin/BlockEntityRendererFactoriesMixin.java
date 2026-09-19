package org.bakingprocess.client.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import org.bakingprocess.client.render.block.blockentity.*;
import org.bakingprocess.registry.ModBlockEntityTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRendererFactories.class)
public abstract class BlockEntityRendererFactoriesMixin {
    @Shadow
    public static <T extends BlockEntity> void register(BlockEntityType<? extends T> type, BlockEntityRendererFactory<T> factory) {}

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void registerBlockEntityRenderers(CallbackInfo ci) {
        register(ModBlockEntityTypes.GRINDING_STONE, GrindingStoneBlockEntityRenderer::new);
        register(ModBlockEntityTypes.GARNISH_DISHES, GarnishDishesBlockEntityRenderer::new);
        register(ModBlockEntityTypes.HEAT_RESISTANT_SLATE, HeatResistantSlateBlockEntityRenderer::new);
        register(ModBlockEntityTypes.MOLD, MoldBlockEntityRenderer::new);
        register(ModBlockEntityTypes.CUTTING_BOARD, CuttingBoardBlockEntityRenderer::new);
        register(ModBlockEntityTypes.POTS, PotsBlockEntityRenderer::new);
        register(ModBlockEntityTypes.PLATE, PlateBlockEntityRenderer::new);
    }
}
