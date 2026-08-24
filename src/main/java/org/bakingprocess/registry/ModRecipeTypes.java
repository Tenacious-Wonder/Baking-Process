package org.bakingprocess.registry;

import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.recipe.*;

public class ModRecipeTypes {
    public static final RecipeType<GrindingRecipe> GRINDING = register("grinding");
    public static final RecipeType<StoveRecipe> STOVE = register("stove");
    public static final RecipeType<CutRecipe> CUT = register("cut");
    public static final RecipeType<DoughRecipe> DOUGH_MAKING = register("dough_making");
    public static final RecipeType<PlatingRecipe> PLATING = register("plating");
    public static final RecipeType<GenericPlatingRecipe> GENERIC_PLATING = register("generic_plating");

    static <T extends Recipe<?>> RecipeType<T> register(String id) {
        return Registry.register(Registries.RECIPE_TYPE, new Identifier(BakingProcess.MOD_ID, id), new RecipeType<T>() {
            public String toString() {
                return id;
            }
        });
    }

    public static void registerAll() {
       ModRecipeSerializers.registerAll();
    }
}
