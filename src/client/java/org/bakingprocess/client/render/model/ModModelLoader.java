package org.bakingprocess.client.render.model;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.Block;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.dfood.block.FoodBlocks;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.content.DishesContent;
import org.bakingprocess.content.ShapedDoughContent;
import org.bakingprocess.registry.ModContents;
import org.bakingprocess.item.FlourItem;
import org.bakingprocess.registry.ModItems;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.twcore.api.process.PlayerAction;
import org.twcore.content.Content;
import org.twcore.process.playeraction.impl.AddContentPlayerAction;
import org.twcore.process.playeraction.impl.AddItemPlayerAction;
import org.twcore.registry.Contents;
import org.twcore.registry.TWRegistries;

import java.util.*;

public class ModModelLoader implements ModelLoadingPlugin {
    private static final Logger LOGGER = BakingProcess.LOGGER;

    /**
     * 存储所有需要加载的模型标识符。
     */
    private static final List<Identifier> MODELS_TO_LOAD = new ArrayList<>();

    /** 菜刀插在案板上的效果 。*/
    public static final Identifier BOARD_KITCHEN_KNIFE = new Identifier(BakingProcess.MOD_ID, "other/on_board_kitchen_knife");

    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        MODELS_TO_LOAD.clear();

        // 注册所有模型
        registerAllFlourSackModels();
        registerAllCookingModels();
        registerDoughKneadingModel();
        registerCuttingModels();
        registryDishesModels();
        registerPlatingProcessModels();
        registryEatModels();
        registerShapedDoughAll();
        MODELS_TO_LOAD.add(BOARD_KITCHEN_KNIFE);

        // 将所有模型添加到加载上下文
        pluginContext.addModels(MODELS_TO_LOAD.toArray(new Identifier[0]));
    }

    // =========== 食物烘烤模型 ===========

    /**
     * 注册所有需要加载烘烤模型的食物方块。
     */
    private void registerAllCookingModels() {
        registerCookingModelsForBlock(FoodBlocks.POTATO, 4);
        registerCookingModelsForBlock(FoodBlocks.BAKED_POTATO, 4);
        registerCookingModelsForBlock(FoodBlocks.BEEF, 2);
        registerCookingModelsForBlock(FoodBlocks.COOKED_BEEF, 2);
        registerCookingModelsForBlock(FoodBlocks.MUTTON, 2);
        registerCookingModelsForBlock(FoodBlocks.COOKED_MUTTON, 2);
        registerCookingModelsForBlock(FoodBlocks.PORKCHOP, 2);
        registerCookingModelsForBlock(FoodBlocks.COOKED_PORKCHOP, 2);
    }

    /**
     * 为指定的FoodBlock注册所有烘烤模型。
     * @param block FoodBlock实例
     * @param maxFood 最大食物数量
     */
    public static void registerCookingModelsForBlock(Block block, int maxFood) {
        if (maxFood < 2) {
            LOGGER.warn("Max food value {} is less than 2 for block {}, skipping cooking models",
                    maxFood, Registries.BLOCK.getId(block));
            return;
        }

        String blockPath = Registries.BLOCK.getId(block).getPath();

        for (int foodValue = 2; foodValue <= maxFood; foodValue++) {
            Identifier modelId = createCookingModel(blockPath, foodValue);
            MODELS_TO_LOAD.add(modelId);
            LOGGER.debug("Registered cooking model: {} for food value {}",
                    modelId, foodValue);
        }
    }

    /**
     * 创建烘烤食物模型的标识符。
     */
    public static Identifier createCookingModel(String blockPath, int foodValue) {
        String modelPath = blockPath + "_cooking_" + foodValue;
        return new Identifier(BakingProcess.MOD_ID, "other/" + modelPath);
    }

    // =========== 粉尘袋模型 ===========

    /**
     * 注册所有粉尘袋模型。
     */
    private void registerAllFlourSackModels() {
        for (FlourItem flourItem : FlourItem.FLOURS) {
            Identifier itemId = Registries.ITEM.getId(flourItem);

            String flourSackModelName = itemId.getPath() + "_sack";
            ModelIdentifier modelId = createItemModel(flourSackModelName);
            MODELS_TO_LOAD.add(modelId);
            LOGGER.debug("Dynamically registered flour sack model: {}", modelId);
        }
    }

    // =========== 揉面流程 ===========

    public static void registerDoughKneadingModel() {
        MODELS_TO_LOAD.add(createProcessModel("knead_add_flour_1"));
        MODELS_TO_LOAD.add(createProcessModel("knead_add_flour_2"));
        MODELS_TO_LOAD.add(createProcessModel("knead_add_flour_3"));

        MODELS_TO_LOAD.add(createProcessModel("knead_add_liquid_1"));
        MODELS_TO_LOAD.add(createProcessModel("knead_add_liquid_2"));
        MODELS_TO_LOAD.add(createProcessModel("knead_add_liquid_3"));

        MODELS_TO_LOAD.add(createProcessModel("knead_knead_1"));
        MODELS_TO_LOAD.add(createProcessModel("knead_knead_2"));
    }

    // =========== 切割流程 ===========

    /**
     * 注册所有切割模型。
     */
    private void registerCuttingModels() {
        registerCuttingModelsForItem(new Identifier("carrot"), 12);
        registerCuttingModelsForItem(new Identifier("apple"), 6);
        registerCuttingModelsForItem(new Identifier("cod"), 9);
        registerCuttingModelsForItem(new Identifier("cooked_cod"), 9);
        registerCuttingModelsForItem(new Identifier("salmon"), 7);
        registerCuttingModelsForItem(new Identifier("cooked_salmon"), 7);
        registerCuttingModelsForItem(new Identifier("potato"), 1);
        registerCuttingModelsForItem(new Identifier("baked_potato"), 1);
        registerCuttingModelsForItem(new Identifier(BakingProcess.MOD_ID, "hard_bread"), 1);
    }

    /**
     * 为指定物品注册所有切割模型。
     */
    public static void registerCuttingModelsForItem(Identifier itemId, int maxCuts) {
        if (maxCuts < 1) {
            LOGGER.warn("Max cuts {} is less than 1 for item {}, skipping cutting models",
                    maxCuts, itemId);
            return;
        }

        // 为每个切割次数注册模型
        for (int cutCount = 1; cutCount <= maxCuts; cutCount++) {
            Identifier modelId = createCuttingModel(itemId, cutCount);
            MODELS_TO_LOAD.add(modelId);
            LOGGER.debug("Registered cutting model: {} for item {} at cut {}",
                    modelId, itemId, cutCount);
        }
    }

    /**
     * 创建切割模型标识符。
     * <p>格式：baking_process:process/cut_{namespace}_{itemPath}_{cutCount}</p>
     */
    public static Identifier createCuttingModel(Identifier itemId, int cutCount) {
        String modelPath = String.format("cut_%s_%s_%d",
                itemId.getNamespace(), itemId.getPath(), cutCount);
        return new Identifier(BakingProcess.MOD_ID, "process/" + modelPath);
    }

    // =========== 摆盘菜肴 ===========

    /**
     * 注册所有菜肴的放置模型。
     */
    private static void registryDishesModels() {
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.BEEF_BERRIES));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.COOKED_BEEF_BERRIES));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.ROASTED_MUSHROOMS));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.COOKED_ROASTED_MUSHROOMS));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.HONEY_ROASTED_BEEF));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.COOKED_HONEY_ROASTED_BEEF));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.FRY_SALMON_CUBES));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.COOKED_FRY_SALMON_CUBES));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.GRILLED_FISH_POTATOES));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.COOKED_GRILLED_FISH_POTATOES));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.DELUXE_ROASTED_RABBIT));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.COOKED_DELUXE_ROASTED_RABBIT));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.HONEY_ROASTED_MUTTON));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.COOKED_HONEY_ROASTED_MUTTON));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.DELUXE_ROAST_CHICKEN));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.COOKED_DELUXE_ROAST_CHICKEN));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.SALT_BAKED_LAMB_CHOPS));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.COOKED_SALT_BAKED_LAMB_CHOPS));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.HONEY_MADE_RABBIT_LEG));
        MODELS_TO_LOAD.add(createDishesModel(ModItems.IRON_PLATE, ModContents.COOKED_HONEY_MADE_RABBIT_LEG));
    }

    /**
     * 注册所有食用过程模型。
     */
    private static void registryEatModels() {
        registerEatStageModels(ModItems.IRON_PLATE, ModContents.COOKED_BEEF_BERRIES);
        registerEatStageModels(ModItems.IRON_PLATE, ModContents.COOKED_ROASTED_MUSHROOMS);
        registerEatStageModels(ModItems.IRON_PLATE, ModContents.COOKED_HONEY_ROASTED_BEEF);
        registerEatStageModels(ModItems.IRON_PLATE, ModContents.COOKED_FRY_SALMON_CUBES);
        registerEatStageModels(ModItems.IRON_PLATE, ModContents.COOKED_GRILLED_FISH_POTATOES);
        registerEatStageModels(ModItems.IRON_PLATE, ModContents.COOKED_DELUXE_ROASTED_RABBIT);
        registerEatStageModels(ModItems.IRON_PLATE, ModContents.COOKED_HONEY_ROASTED_MUTTON);
        registerEatStageModels(ModItems.IRON_PLATE, ModContents.COOKED_DELUXE_ROAST_CHICKEN);
        registerEatStageModels(ModItems.IRON_PLATE, ModContents.COOKED_SALT_BAKED_LAMB_CHOPS);
        registerEatStageModels(ModItems.IRON_PLATE, ModContents.COOKED_HONEY_MADE_RABBIT_LEG);
    }

    /**
     * 注册摆盘流程模型
     */
    private void registerPlatingProcessModels() {
        registerPlatingSequenceModels(
                ModItems.IRON_PLATE,
                Arrays.asList(
                        new AddItemPlayerAction(Items.BEEF),
                        new AddItemPlayerAction(Items.SWEET_BERRIES)
                ),
                ModContents.BEEF_BERRIES);

        registerPlatingSequenceModels(
                ModItems.IRON_PLATE,
                Arrays.asList(
                        new AddItemPlayerAction(Items.RED_MUSHROOM),
                        new AddItemPlayerAction(Items.BROWN_MUSHROOM),
                        new AddItemPlayerAction(Items.BROWN_MUSHROOM),
                        new AddItemPlayerAction(Items.BROWN_MUSHROOM),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR)
                ),
                ModContents.ROASTED_MUSHROOMS);

        registerPlatingSequenceModels(
                ModItems.IRON_PLATE,
                Arrays.asList(
                        new AddItemPlayerAction(Items.BEEF),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddContentPlayerAction(Contents.HONEY),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES)
                ),
                ModContents.HONEY_ROASTED_BEEF);

        registerPlatingSequenceModels(
                ModItems.IRON_PLATE,
                Arrays.asList(
                        new AddItemPlayerAction(ModItems.SALMON_CUBES),
                        new AddItemPlayerAction(ModItems.SALMON_CUBES),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddItemPlayerAction(Items.GLOW_BERRIES),
                        new AddItemPlayerAction(Items.GLOW_BERRIES),
                        new AddItemPlayerAction(Items.GLOW_BERRIES),
                        new AddItemPlayerAction(Items.GLOW_BERRIES)
                ),
                ModContents.FRY_SALMON_CUBES);

        registerPlatingSequenceModels(
                ModItems.IRON_PLATE,
                Arrays.asList(
                        new AddItemPlayerAction(ModItems.POTATO_CUBES),
                        new AddItemPlayerAction(ModItems.POTATO_CUBES),
                        new AddItemPlayerAction(Items.COD)
                ),
                ModContents.GRILLED_FISH_POTATOES);

        registerPlatingSequenceModels(
                ModItems.IRON_PLATE,
                Arrays.asList(
                        new AddItemPlayerAction(Items.RABBIT),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddContentPlayerAction(Contents.HONEY),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(Items.SWEET_BERRIES)
                ),
                ModContents.DELUXE_ROASTED_RABBIT);

        registerPlatingSequenceModels(
                ModItems.IRON_PLATE,
                Arrays.asList(
                        new AddItemPlayerAction(Items.MUTTON),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddContentPlayerAction(Contents.HONEY),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.CARROT_HEAD)
                ),
                ModContents.HONEY_ROASTED_MUTTON);

        registerPlatingSequenceModels(
                ModItems.IRON_PLATE,
                Arrays.asList(
                        new AddItemPlayerAction(Items.CHICKEN),
                        new AddItemPlayerAction(Items.SWEET_BERRIES),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddContentPlayerAction(Contents.HONEY),
                        new AddItemPlayerAction(ModItems.CARROT_HEAD)
                ),
                ModContents.DELUXE_ROAST_CHICKEN);
        registerPlatingSequenceModels(
                ModItems.IRON_PLATE,
                Arrays.asList(
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddItemPlayerAction(Items.MUTTON),
                        new AddItemPlayerAction(ModItems.POTATO_CUBES),
                        new AddItemPlayerAction(ModItems.SALT_CUBES),
                        new AddItemPlayerAction(ModItems.SALT_CUBES),
                        new AddItemPlayerAction(ModItems.SALT_CUBES),
                        new AddItemPlayerAction(ModItems.SALT_CUBES),
                        new AddItemPlayerAction(ModItems.CARROT_HEAD)
                ),
                ModContents.SALT_BAKED_LAMB_CHOPS);
        registerPlatingSequenceModels(
                ModItems.IRON_PLATE,
                Arrays.asList(
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.CARROT_HEAD),
                        new AddItemPlayerAction(Items.RABBIT_FOOT),
                        new AddItemPlayerAction(Items.RABBIT_FOOT),
                        new AddItemPlayerAction(Items.RABBIT_FOOT),
                        new AddItemPlayerAction(Items.RABBIT_FOOT),
                        new AddContentPlayerAction(Contents.HONEY),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddItemPlayerAction(Items.SWEET_BERRIES)
                ),
                ModContents.HONEY_MADE_RABBIT_LEG);
    }

    /**
     * 注册一个摆盘配方的所有模型（包括所有前缀步骤）
     *
     * @param container 容器物品
     * @param actionSequence 完整的操作序列
     * @param dish 最终菜肴（可选，用于注册配方映射）
     */
    public static void registerPlatingSequenceModels(Item container,
                                                     List<PlayerAction> actionSequence,
                                                     @Nullable Content dish) {
        PlatingModelManager modelManager = PlatingModelManager.getInstance();

        // 注册配方映射和食用过程
        if (dish != null) {
            modelManager.registerRecipeModel(container, actionSequence, dish);
        }

        // 生成并注册所有前缀模型
        List<Identifier> prefixModels = modelManager.generateAllPrefixModels(container, actionSequence);

        for (Identifier modelId : prefixModels) {
            if (modelId != null && !MODELS_TO_LOAD.contains(modelId)) {
                MODELS_TO_LOAD.add(modelId);
            }
        }
    }

    /**
     * 注册一个菜肴在容器中的所有食用过程模型。
     * @param container 容器
     * @param content 菜肴
     */
    public static void registerEatStageModels(Item container, Content content) {
        if (!(content instanceof DishesContent dish)) {
            return;
        }

        if (!dish.canEat()) {
            LOGGER.warn("{} is inedible", dish);
            return;
        }

        for (int eaten = 1; eaten < dish.getEatCount(); eaten++) {
            Identifier modelId = createEatStageModel(container, dish, eaten);
            if (!MODELS_TO_LOAD.contains(modelId)) {
                MODELS_TO_LOAD.add(modelId);
            }
        }
    }

    /**
     * 创建菜肴的放置模型标识符。
     * @param baseContainer 基础容器
     * @param dishes 菜肴
     * @return 对应的放置模型标识符
     */
    public static Identifier createDishesModel(Item baseContainer, Content dishes) {
        String containerId = Registries.ITEM.getId(baseContainer).getPath();
        String dishesId = Objects.requireNonNull(TWRegistries.CONTENT.getId(dishes)).getPath();

        return new Identifier(BakingProcess.MOD_ID, "dishes/" + containerId + "_" + dishesId);
    }

    /**
     * 创建已食用菜肴的放置模型标识符。
     * @param container 基础容器
     * @param dish 菜肴
     * @param eatenCount 已食用次数
     * @return 对应的放置模型标识符
     */
    public static Identifier createEatStageModel(Item container, DishesContent dish, int eatenCount) {
        String containerPath = Registries.ITEM.getId(container).getPath();
        String dishPath = Objects.requireNonNull(TWRegistries.CONTENT.getId(dish)).getPath();
        return new Identifier(BakingProcess.MOD_ID, "dishes/eat/" + containerPath + "_" + dishPath + "_" + eatenCount);
    }

    // =========== 定型面团 ===========

    public static void registerShapedDoughAll() {
        MODELS_TO_LOAD.add(createShapedDoughModel((ShapedDoughContent) ModContents.TOAST_EMBRYO));
        MODELS_TO_LOAD.add(createShapedDoughModel((ShapedDoughContent) ModContents.TOAST));
        MODELS_TO_LOAD.add(createShapedDoughModel((ShapedDoughContent) ModContents.CAKE_EMBRYO));
        MODELS_TO_LOAD.add(createShapedDoughModel((ShapedDoughContent) ModContents.BAKED_CAKE_EMBRYO));
    }

    /**
     * 创建定型面团的模型标识符。
     *
     * @param content 定型面团
     * @return 对应的模型标识符
     */
    public static Identifier createShapedDoughModel(ShapedDoughContent content) {
        Identifier contentId = Objects.requireNonNull(TWRegistries.CONTENT.getId(content));
        return new Identifier(contentId.getNamespace(), "block/" + contentId.getPath());
    }

    // =========== 辅助方法 ===========

    /**
     * 创建物品模型的标识符。
     */
    public static ModelIdentifier createItemModel(String itemPath) {
        return new ModelIdentifier(new Identifier(BakingProcess.MOD_ID, itemPath), "inventory");
    }

    /**
     * 创建步骤所需的额外模型的标识符。
     */
    public static Identifier createProcessModel(String blockPath) {
        return new Identifier(BakingProcess.MOD_ID, "process/" + blockPath);
    }

    /**
     * 获取已注册的所有模型。
     */
    public static List<Identifier> getRegisteredModels() {
        return new ArrayList<>(MODELS_TO_LOAD);
    }

    /**
     * 获取已注册的模型数量。
     */
    public static int getRegisteredModelCount() {
        return MODELS_TO_LOAD.size();
    }
}