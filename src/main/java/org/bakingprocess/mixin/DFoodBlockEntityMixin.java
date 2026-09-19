package org.bakingprocess.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import org.dfood.block.entity.ComplexFoodBlockEntity;
import org.dfood.block.entity.ModBlockEntityTypes;
import org.dfood.block.entity.SuspiciousStewBlockEntity;
import org.dfood.replace.FoodBlocks;
import org.bakingprocess.integration.dfood.AssistedBlocks;
import org.bakingprocess.registry.ModBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ModBlockEntityTypes.class)
public class DFoodBlockEntityMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Lorg/dfood/block/entity/ModBlockEntityTypes;create(Ljava/lang/String;Lnet/minecraft/block/entity/BlockEntityType$Builder;)Lnet/minecraft/block/entity/BlockEntityType;",
            ordinal = 2), index = 1)
    private static <T extends BlockEntity> BlockEntityType.Builder<?> registerStewEntities(BlockEntityType.Builder<T> builder) {
        return BlockEntityType.Builder.create(SuspiciousStewBlockEntity::new, FoodBlocks.SUSPICIOUS_STEW, AssistedBlocks.CRIPPLED_SUSPICIOUS_STEW);
    }

    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Lorg/dfood/block/entity/ModBlockEntityTypes;create(Ljava/lang/String;Lnet/minecraft/block/entity/BlockEntityType$Builder;)Lnet/minecraft/block/entity/BlockEntityType;",
            ordinal = 0), index = 1)
    private static <T extends BlockEntity> BlockEntityType.Builder<?> registerComplexEntities(BlockEntityType.Builder<T> builder) {
        return BlockEntityType.Builder.create(ComplexFoodBlockEntity::new, ModBlocks.BREAD_SPATULA);
    }
}
