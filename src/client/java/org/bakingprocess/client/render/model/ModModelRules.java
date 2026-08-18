package org.bakingprocess.client.render.model;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.content.ShapedDoughContent;
import org.bakingprocess.item.FlourItem;
import org.bakingprocess.registry.ModContents;
import org.bakingprocess.registry.ModItems;
import org.dfood.block.FoodBlocks;
import org.twcore.TWCore;
import org.twcore.api.config.TwConfig;
import org.twcore.api.process.PlayerAction;
import org.twcore.client.api.render.ModelRule;
import org.twcore.content.Content;
import org.twcore.process.playeraction.impl.AddContentPlayerAction;
import org.twcore.process.playeraction.impl.AddItemPlayerAction;
import org.twcore.registry.Contents;
import org.twcore.registry.TWRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 本模组模型加载规则的注册入口，调用的是 TW Core 的 {@link ModelRule} API（配合
 * {@link TwConfig} 默认值影响器）。目前注册的规则类型（{@code 规则类型 ：格式}）：
 * <ul>
 *   <li>烘烤食物模型 {@code cooking} ： {@code <blockPath>|<maxFood>}；</li>
 *   <li>面粉袋物品模型 {@code flour_sack} ： {@code <itemPath>...} ；</li>
 *   <li>流程模型 {@code process_model} ： {@code <name>...} ；</li>
 *   <li>切割模型 {@code cutting} ： {@code <itemId>|<maxCuts>} ；</li>
 *   <li>菜肴放置模型 {@code dishes} ： {@code <containerId>|<dishId>} ；</li>
 *   <li>食用阶段模型 {@code dishes_eat} ： {@code <containerId>|<dishId>|<maxEaten>} ；</li>
 *   <li>定型面团模型 {@code shaped_dough} ： {@code <contentId>} ；</li>
 *   <li>摆盘流程模型 {@code plating} ： {@code <containerId>|<dishId>|<action1>;<action2>;...} 规则顺序不可调整）；</li>
 * </ul>
 */
public final class ModModelRules {
    /** TW Core 模型加载规则配置的名称。 */
    private static final String MODEL_LOADING_RULES_CONFIG = "model_loading_rules";

    private ModModelRules() {
    }

    public static void register() {
        registerRuleTypes();
        TwConfig.forMod(BakingProcess.MOD_ID)
                .addDefaultOverride(TWCore.MOD_ID, MODEL_LOADING_RULES_CONFIG, createDefaultRules());
    }

    // ==================== 规则类型注册 ====================

    private static void registerRuleTypes() {
        // 烘烤
        ModelRule.register("cooking", params -> {
            requireParams(params, 2, "cooking|<blockPath>|<maxFood>");
            String blockPath = params.get(0);
            int maxFood = parseInt(params.get(1), "max food");
            if (maxFood < 2) {
                throw new IllegalArgumentException("Max food must be at least 2: " + maxFood);
            }
            List<Identifier> models = new ArrayList<>();
            for (int foodValue = 2; foodValue <= maxFood; foodValue++) {
                models.add(ModModelId.createCookingModelId(blockPath, foodValue));
            }
            return models;
        });

        // 面粉袋
        ModelRule.register("flour_sack", params -> {
            requireParams(params, 1, "flour_sack|<itemPath>...");
            List<Identifier> models = new ArrayList<>();
            for (String itemPath : params) {
                models.add(ModModelId.createItemModelId(itemPath + "_sack"));
            }
            return models;
        });

        // 流程模型
        ModelRule.register("process_model", params -> {
            requireParams(params, 1, "process_model|<name>...");
            List<Identifier> models = new ArrayList<>();
            for (String name : params) {
                models.add(ModModelId.createProcessModelId(name));
            }
            return models;
        });

        // 切割
        ModelRule.register("cutting", params -> {
            requireParams(params, 2, "cutting|<itemId>|<maxCuts>");
            Identifier itemId = parseIdentifier(params.get(0), "item id");
            int maxCuts = parseInt(params.get(1), "max cuts");
            if (maxCuts < 1) {
                throw new IllegalArgumentException("Max cuts must be at least 1: " + maxCuts);
            }
            List<Identifier> models = new ArrayList<>();
            for (int cutCount = 1; cutCount <= maxCuts; cutCount++) {
                models.add(ModModelId.createCuttingModelId(itemId, cutCount));
            }
            return models;
        });

        // 菜肴放置
        ModelRule.register("dishes", params -> {
            requireParams(params, 2, "dishes|<containerId>|<dishId>");
            Item container = requireItem(params.get(0));
            Identifier dishId = parseIdentifier(params.get(1), "dish id");
            return List.of(ModModelId.createDishesModelId(container, dishId));
        });

        // 食用阶段
        ModelRule.register("dishes_eat", params -> {
            requireParams(params, 3, "dishes_eat|<containerId>|<dishId>|<maxEaten>");
            Item container = requireItem(params.get(0));
            Identifier dishId = parseIdentifier(params.get(1), "dish id");
            int maxEaten = parseInt(params.get(2), "max eaten count");
            if (maxEaten < 1) {
                throw new IllegalArgumentException("Max eaten count must be at least 1: " + maxEaten);
            }
            List<Identifier> models = new ArrayList<>();
            for (int eaten = 1; eaten <= maxEaten; eaten++) {
                models.add(ModModelId.createEatStageModelId(container, dishId, eaten));
            }
            return models;
        });

        // 定型面团
        ModelRule.register("shaped_dough", params -> {
            requireParams(params, 1, "shaped_dough|<contentId>");
            Content content = requireContent(params.get(0));
            if (!(content instanceof ShapedDoughContent shapedDough)) {
                throw new IllegalArgumentException("Not a shaped dough content: " + params.get(0));
            }
            return List.of(ModModelId.createShapedDoughModelId(shapedDough));
        });

        // 摆盘
        ModelRule.register("plating", params -> {
            requireParams(params, 3, "plating|<containerId>|<dishId>|<action1>;<action2>;...");
            Item container = requireItem(params.get(0));
            Identifier dishId = params.get(1).isEmpty() ? null : parseIdentifier(params.get(1), "dish id");
            List<PlayerAction> actions = parseActions(params.get(2));

            PlatingModelManager modelManager = PlatingModelManager.getInstance();
            if (dishId != null) {
                modelManager.registerRecipeModel(container, actions, dishId);
            }
            return modelManager.generateAllPrefixModels(container, actions);
        });

    }

    // ==================== 默认规则生成 ====================

    /** 生成与原 {@code ModModelLoader} 注册结果等价的默认规则列表，{@code plating} 规则顺序不可调整。 */
    private static List<String> createDefaultRules() {
        List<String> rules = new ArrayList<>();

        // 面粉袋：为每种面粉生成物品模型
        for (FlourItem flourItem : FlourItem.FLOURS) {
            rules.add("flour_sack|" + Registries.ITEM.getId(flourItem).getPath());
        }

        // 烘烤：食物方块按份数展开
        addCookingRule(rules, FoodBlocks.POTATO, 4);
        addCookingRule(rules, FoodBlocks.BAKED_POTATO, 4);
        addCookingRule(rules, FoodBlocks.BEEF, 2);
        addCookingRule(rules, FoodBlocks.COOKED_BEEF, 2);
        addCookingRule(rules, FoodBlocks.MUTTON, 2);
        addCookingRule(rules, FoodBlocks.COOKED_MUTTON, 2);
        addCookingRule(rules, FoodBlocks.PORKCHOP, 2);
        addCookingRule(rules, FoodBlocks.COOKED_PORKCHOP, 2);

        // 揉面：固定的流程模型
        rules.add("process_model|knead_add_flour_1|knead_add_flour_2|knead_add_flour_3"
                + "|knead_add_liquid_1|knead_add_liquid_2|knead_add_liquid_3"
                + "|knead_knead_1|knead_knead_2");

        // 切割：按物品与次数展开
        addCuttingRule(rules, Items.CARROT, 12);
        addCuttingRule(rules, Items.APPLE, 6);
        addCuttingRule(rules, Items.COD, 9);
        addCuttingRule(rules, Items.COOKED_COD, 9);
        addCuttingRule(rules, Items.SALMON, 7);
        addCuttingRule(rules, Items.COOKED_SALMON, 7);
        addCuttingRule(rules, Items.POTATO, 1);
        addCuttingRule(rules, Items.BAKED_POTATO, 1);
        addCuttingRule(rules, Items.BEETROOT, 9);
        rules.add("cutting|baking_process:hard_bread|1");

        // 摆盘菜肴放置模型
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("beef_berries"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("cooked_beef_berries"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("roasted_mushrooms"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("cooked_roasted_mushrooms"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("honey_roasted_beef"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("cooked_honey_roasted_beef"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("fry_salmon_cubes"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("cooked_fry_salmon_cubes"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("grilled_fish_potatoes"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("cooked_grilled_fish_potatoes"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("deluxe_roasted_rabbit"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("cooked_deluxe_roasted_rabbit"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("honey_roasted_mutton"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("cooked_honey_roasted_mutton"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("deluxe_roast_chicken"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("cooked_deluxe_roast_chicken"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("salt_baked_lamb_chops"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("cooked_salt_baked_lamb_chops"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("honey_made_rabbit_leg"));
        addDishesRule(rules, ModItems.IRON_PLATE, dishId("cooked_honey_made_rabbit_leg"));

        // 食用阶段模型（maxEaten = 熟菜口数 - 1，与配方 json 的 eat_count 对应）
        addEatRule(rules, ModItems.IRON_PLATE, dishId("cooked_beef_berries"), 1);
        addEatRule(rules, ModItems.IRON_PLATE, dishId("cooked_roasted_mushrooms"), 3);
        addEatRule(rules, ModItems.IRON_PLATE, dishId("cooked_honey_roasted_beef"), 2);
        addEatRule(rules, ModItems.IRON_PLATE, dishId("cooked_fry_salmon_cubes"), 3);
        addEatRule(rules, ModItems.IRON_PLATE, dishId("cooked_grilled_fish_potatoes"), 3);
        addEatRule(rules, ModItems.IRON_PLATE, dishId("cooked_deluxe_roasted_rabbit"), 3);
        addEatRule(rules, ModItems.IRON_PLATE, dishId("cooked_honey_roasted_mutton"), 3);
        addEatRule(rules, ModItems.IRON_PLATE, dishId("cooked_deluxe_roast_chicken"), 3);
        addEatRule(rules, ModItems.IRON_PLATE, dishId("cooked_salt_baked_lamb_chops"), 4);
        addEatRule(rules, ModItems.IRON_PLATE, dishId("cooked_honey_made_rabbit_leg"), 4);

        // 摆盘流程：注册配方映射并生成全部前缀模型
        addPlatingRule(rules, ModItems.IRON_PLATE,
                List.of(new AddItemPlayerAction(Items.BEEF),
                        new AddItemPlayerAction(Items.SWEET_BERRIES)),
                dishId("beef_berries"));

        addPlatingRule(rules, ModItems.IRON_PLATE,
                List.of(new AddItemPlayerAction(Items.RED_MUSHROOM),
                        new AddItemPlayerAction(Items.BROWN_MUSHROOM),
                        new AddItemPlayerAction(Items.BROWN_MUSHROOM),
                        new AddItemPlayerAction(Items.BROWN_MUSHROOM),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR)),
                dishId("roasted_mushrooms"));

        addPlatingRule(rules, ModItems.IRON_PLATE,
                List.of(new AddItemPlayerAction(Items.BEEF),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddContentPlayerAction(Contents.HONEY),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES)),
                dishId("honey_roasted_beef"));

        addPlatingRule(rules, ModItems.IRON_PLATE,
                List.of(new AddItemPlayerAction(ModItems.SALMON_CUBES),
                        new AddItemPlayerAction(ModItems.SALMON_CUBES),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddItemPlayerAction(Items.GLOW_BERRIES),
                        new AddItemPlayerAction(Items.GLOW_BERRIES),
                        new AddItemPlayerAction(Items.GLOW_BERRIES),
                        new AddItemPlayerAction(Items.GLOW_BERRIES)),
                dishId("fry_salmon_cubes"));

        addPlatingRule(rules, ModItems.IRON_PLATE,
                List.of(new AddItemPlayerAction(ModItems.POTATO_CUBES),
                        new AddItemPlayerAction(ModItems.POTATO_CUBES),
                        new AddItemPlayerAction(Items.COD)),
                dishId("grilled_fish_potatoes"));

        addPlatingRule(rules, ModItems.IRON_PLATE,
                List.of(new AddItemPlayerAction(Items.RABBIT),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddContentPlayerAction(Contents.HONEY),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(Items.SWEET_BERRIES)),
                dishId("deluxe_roasted_rabbit"));

        addPlatingRule(rules, ModItems.IRON_PLATE,
                List.of(new AddItemPlayerAction(Items.MUTTON),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddContentPlayerAction(Contents.HONEY),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.CARROT_HEAD)),
                dishId("honey_roasted_mutton"));

        addPlatingRule(rules, ModItems.IRON_PLATE,
                List.of(new AddItemPlayerAction(Items.CHICKEN),
                        new AddItemPlayerAction(Items.SWEET_BERRIES),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddContentPlayerAction(Contents.HONEY),
                        new AddItemPlayerAction(ModItems.CARROT_HEAD)),
                dishId("deluxe_roast_chicken"));

        addPlatingRule(rules, ModItems.IRON_PLATE,
                List.of(new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddItemPlayerAction(Items.MUTTON),
                        new AddItemPlayerAction(ModItems.POTATO_CUBES),
                        new AddItemPlayerAction(ModItems.SALT_CUBES),
                        new AddItemPlayerAction(ModItems.SALT_CUBES),
                        new AddItemPlayerAction(ModItems.SALT_CUBES),
                        new AddItemPlayerAction(ModItems.SALT_CUBES),
                        new AddItemPlayerAction(ModItems.CARROT_HEAD)),
                dishId("salt_baked_lamb_chops"));

        addPlatingRule(rules, ModItems.IRON_PLATE,
                List.of(new AddItemPlayerAction(ModItems.CARROT_SLICES),
                        new AddItemPlayerAction(ModItems.CARROT_HEAD),
                        new AddItemPlayerAction(Items.RABBIT_FOOT),
                        new AddItemPlayerAction(Items.RABBIT_FOOT),
                        new AddItemPlayerAction(Items.RABBIT_FOOT),
                        new AddItemPlayerAction(Items.RABBIT_FOOT),
                        new AddContentPlayerAction(Contents.HONEY),
                        new AddItemPlayerAction(ModItems.SALT_FLOUR),
                        new AddItemPlayerAction(Items.SWEET_BERRIES)),
                dishId("honey_made_rabbit_leg"));

        // 定型面团
        addShapedDoughRule(rules, ModContents.TOAST_EMBRYO);
        addShapedDoughRule(rules, ModContents.TOAST);
        addShapedDoughRule(rules, ModContents.CAKE_EMBRYO);
        addShapedDoughRule(rules, ModContents.BAKED_CAKE_EMBRYO);

        // 案板菜刀
        rules.add("plain_model|" + BakingProcess.MOD_ID + "|other/on_board_kitchen_knife");

        return rules;
    }

    private static void addCookingRule(List<String> rules, Block block, int maxFood) {
        rules.add("cooking|" + Registries.BLOCK.getId(block).getPath() + "|" + maxFood);
    }

    private static void addCuttingRule(List<String> rules, Item item, int maxCuts) {
        rules.add("cutting|" + Registries.ITEM.getId(item) + "|" + maxCuts);
    }

    private static void addDishesRule(List<String> rules, Item container, Identifier dishId) {
        rules.add("dishes|" + Registries.ITEM.getId(container) + "|" + dishId);
    }

    private static void addEatRule(List<String> rules, Item container, Identifier dishId, int maxEaten) {
        if (maxEaten < 1) {
            return;
        }
        rules.add("dishes_eat|" + Registries.ITEM.getId(container) + "|" + dishId + "|" + maxEaten);
    }

    private static void addPlatingRule(List<String> rules, Item container,
                                       List<PlayerAction> actions, Identifier dishId) {
        String encodedActions = actions.stream()
                .map(ModModelRules::encodeAction)
                .collect(Collectors.joining(";"));
        rules.add("plating|" + Registries.ITEM.getId(container) + "|" + dishId + "|" + encodedActions);
    }

    /** 构造本模组命名空间下的菜标识。 */
    private static Identifier dishId(String path) {
        return new Identifier(BakingProcess.MOD_ID, path);
    }

    private static void addShapedDoughRule(List<String> rules, Content content) {
        rules.add("shaped_dough|" + TWRegistries.CONTENT.getId(content));
    }

    // ==================== 参数解析辅助 ====================

    /**
     * 将摆盘操作序列编码为规则参数：{@code item:<id>} / {@code content:<id>}，以 {@code ;} 分隔。
     * <p>不使用 {@link PlayerAction#toString()} 的原始格式，避免规则参数中出现 {@code |} 分隔符。</p>
     */
    private static String encodeAction(PlayerAction action) {
        String[] parts = action.toString().split("\\|");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid player action string: " + action);
        }
        return switch (parts[0]) {
            case AddItemPlayerAction.TYPE -> "item:" + parts[1];
            case AddContentPlayerAction.TYPE -> "content:" + parts[1];
            default -> throw new IllegalArgumentException("Unsupported player action type: " + parts[0]);
        };
    }

    private static List<PlayerAction> parseActions(String encoded) {
        List<PlayerAction> actions = new ArrayList<>();
        for (String token : encoded.split(";")) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.startsWith("item:")) {
                actions.add(new AddItemPlayerAction(requireItem(value.substring("item:".length()))));
            } else if (value.startsWith("content:")) {
                actions.add(new AddContentPlayerAction(requireContent(value.substring("content:".length()))));
            } else {
                throw new IllegalArgumentException("Unknown plating action token: " + value);
            }
        }
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("Plating action sequence is empty");
        }
        return actions;
    }

    private static Item requireItem(String value) {
        Identifier id = parseIdentifier(value, "item id");
        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalArgumentException("No item found: " + value);
        }
        return item;
    }

    private static Content requireContent(String value) {
        Identifier id = parseIdentifier(value, "content id");
        Content content = TWRegistries.CONTENT.get(id);
        if (content == null) {
            throw new IllegalArgumentException("No content found: " + value);
        }
        return content;
    }

    private static Identifier parseIdentifier(String value, String description) {
        Identifier id = Identifier.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid " + description + ": " + value);
        }
        return id;
    }

    private static int parseInt(String value, String description) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + description + ": " + value);
        }
    }

    private static void requireParams(List<String> params, int count, String usage) {
        if (params.size() < count) {
            throw new IllegalArgumentException(
                    "Expected at least " + count + " parameter(s), got " + params.size() + ". Usage: " + usage);
        }
    }
}
