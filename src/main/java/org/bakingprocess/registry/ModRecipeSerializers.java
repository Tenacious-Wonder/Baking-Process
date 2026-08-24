package org.bakingprocess.registry;

import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.recipe.serializer.*;

public class ModRecipeSerializers {
    public static final RecipeSerializer<?> GRINDING = register("grinding", new GrindingRecipeSerializer());
    public static final RecipeSerializer<?> STOVE = register("stove", new StoveRecipeSerializer());
    public static final RecipeSerializer<?> CUT = register("cut", new CutRecipeSerializer());
    public static final RecipeSerializer<?> DOUGH_MAKING = register("dough_making", new DoughRecipeSerializer());
    public static final RecipeSerializer<?> PLATING = register("plating", new PlatingRecipeSerializer());
    public static final RecipeSerializer<?> GENERIC_PLATING = register("generic_plating", new GenericPlatingRecipeSerializer());

    private static <S extends RecipeSerializer<?>> S register(String id, S serializer) {
        return Registry.register(Registries.RECIPE_SERIALIZER, new Identifier(BakingProcess.MOD_ID, id), serializer);
    }

    public static void registerAll() {}
}