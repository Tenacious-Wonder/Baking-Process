package org.bakingprocess.registry;

import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.content.DishesContent;
import org.bakingprocess.content.ShapedDoughContent;
import org.bakingprocess.food.ModFoodComponents;
import org.twcore.content.Content;
import org.twcore.registry.TWRegistries;

public class ModContents {
    public static final String DISHES = "dishes";
    public static final String SHAPED_DOUGH = "shaped_dough";

    // 菜肴
    public static final Content BEEF_BERRIES = registerContent("beef_berries",
            new DishesContent(DISHES));

    public static final Content COOKED_BEEF_BERRIES = registerContent("cooked_beef_berries",
            new DishesContent(DISHES, ModFoodComponents.COOKED_BEEF_BERRIES, 2));

    public static final Content ROASTED_MUSHROOMS = registerContent("roasted_mushrooms",
            new DishesContent(DISHES));

    public static final Content COOKED_ROASTED_MUSHROOMS = registerContent("cooked_roasted_mushrooms",
            new DishesContent(DISHES, ModFoodComponents.COOKED_ROASTED_MUSHROOMS, 4));

    public static final Content HONEY_ROASTED_BEEF = registerContent("honey_roasted_beef",
            new DishesContent(DISHES));

    public static final Content COOKED_HONEY_ROASTED_BEEF = registerContent("cooked_honey_roasted_beef",
            new DishesContent(DISHES, ModFoodComponents.COOKED_HONEY_ROASTED_BEEF, 3));

    public static final Content FRY_SALMON_CUBES = registerContent("fry_salmon_cubes",
            new DishesContent(DISHES));

    public static final Content COOKED_FRY_SALMON_CUBES = registerContent("cooked_fry_salmon_cubes",
            new DishesContent(DISHES, ModFoodComponents.COOKED_FRY_SALMON_CUBES, 4));

    public static final Content GRILLED_FISH_POTATOES = registerContent("grilled_fish_potatoes",
            new DishesContent(DISHES));

    public static final Content COOKED_GRILLED_FISH_POTATOES = registerContent("cooked_grilled_fish_potatoes",
            new DishesContent(DISHES, ModFoodComponents.COOKED_GRILLED_FISH_POTATOES, 4));

    public static final Content DELUXE_ROASTED_RABBIT = registerContent("deluxe_roasted_rabbit",
            new DishesContent(DISHES));

    public static final Content COOKED_DELUXE_ROASTED_RABBIT = registerContent("cooked_deluxe_roasted_rabbit",
            new DishesContent(DISHES, ModFoodComponents.COOKED_DELUXE_ROASTED_RABBIT, 4));

    public static final Content HONEY_ROASTED_MUTTON = registerContent("honey_roasted_mutton",
            new DishesContent(DISHES));

    public static final Content COOKED_HONEY_ROASTED_MUTTON = registerContent("cooked_honey_roasted_mutton",
            new DishesContent(DISHES, ModFoodComponents.COOKED_HONEY_ROASTED_MUTTON, 4));

    public static final Content DELUXE_ROAST_CHICKEN = registerContent("deluxe_roast_chicken",
            new DishesContent(DISHES));

    public static final Content COOKED_DELUXE_ROAST_CHICKEN = registerContent("cooked_deluxe_roast_chicken",
            new DishesContent(DISHES, ModFoodComponents.COOKED_DELUXE_ROAST_CHICKEN, 4));

    public static final Content SALT_BAKED_LAMB_CHOPS = registerContent("salt_baked_lamb_chops",
            new DishesContent(DISHES));

    public static final Content COOKED_SALT_BAKED_LAMB_CHOPS = registerContent("cooked_salt_baked_lamb_chops",
            new DishesContent(DISHES, ModFoodComponents.COOKED_SALT_BAKED_LAMB_CHOPS, 5));

    public static final Content HONEY_MADE_RABBIT_LEG = registerContent("honey_made_rabbit_leg",
            new DishesContent(DISHES));

    public static final Content COOKED_HONEY_MADE_RABBIT_LEG = registerContent("cooked_honey_made_rabbit_leg",
            new DishesContent(DISHES, ModFoodComponents.COOKED_HONEY_MADE_RABBIT_LEG, 5));

    // 定型面团
    public static final Content TOAST_EMBRYO = registerContent("toast_embryo", new ShapedDoughContent(
            SHAPED_DOUGH, createModId("toast_dough"), createModId("toast_embryo_mold")));

    public static final Content TOAST = registerContent("toast", new ShapedDoughContent(
            SHAPED_DOUGH, createModId("toast"), createModId("toast_embryo_mold")));

    public static final Content CAKE_EMBRYO = registerContent("cake_embryo", new ShapedDoughContent(
            SHAPED_DOUGH, createModId("cake_dough"), createModId("cake_embryo_mold")));

    public static final Content BAKED_CAKE_EMBRYO = registerContent("baked_cake_embryo", new ShapedDoughContent(
            SHAPED_DOUGH, createModId("baked_cake_embryo"), createModId("cake_embryo_mold")));

    private static Content registerContent(String name, Content content){
        return Registry.register(TWRegistries.CONTENT, new Identifier(BakingProcess.MOD_ID, name), content);
    }

    private static Identifier createModId(String path) {
        return new Identifier(BakingProcess.MOD_ID, path);
    }

    public static void registerAll() {}
}