package org.bakingprocess.integration.dfood;

import org.dfood.shape.Shapes;

public class DFoodInit {
    public static void init() {
        FoodBlocksModifier.FoodBlockAdd();
        AssistedBlocks.registerAssistedBlocks();

        // 使用后的方块
        Shapes.shapeMap.put("baking_process:crippled_rabbit_stew",new int[][]{
                {1, 4, 8}
        });
        Shapes.shapeMap.put("baking_process:crippled_mushroom_stew",new int[][]{
                {1, 4, 8}
        });
        Shapes.shapeMap.put("baking_process:crippled_beetroot_soup",new int[][]{
                {1, 4, 8}
        });
        Shapes.shapeMap.put("baking_process:crippled_suspicious_stew",new int[][]{
                {1, 4, 8}
        });
        Shapes.shapeMap.put("baking_process:crippled_milk_bucket", new int[][]{
                {1, 3, 8}
        });
        Shapes.shapeMap.put("baking_process:crippled_water_bucket", new int[][]{
                {1, 3, 8}
        });

        // 粉尘袋
        Shapes.shapeMap.put("baking_process:flour_sack",new int[][]{
                {1, 2, 8}
        });

        // 奶制品
        Shapes.shapeMap.put("baking_process:milk_potion",new int[][]{
                {1, 2, 8}, {3, 3, 1}
        });

        // 面包
        Shapes.shapeMap.put("baking_process:small_bread_embryo",new int[][]{
                {1, 1, 12}
        });
        Shapes.shapeMap.put("baking_process:small_bread",new int[][]{
                {1, 1, 12}
        });
        Shapes.shapeMap.put("baking_process:baguette_embryo",new int[][]{
                {1, 1, 2}
        });
        Shapes.shapeMap.put("baking_process:baguette",new int[][]{
                {1, 1, 2}
        });
        Shapes.shapeMap.put("baking_process:hard_bread",new int[][]{
                {1, 1, 8}
        });
        Shapes.shapeMap.put("baking_process:dough",new int[][]{
                {1, 1, 12}
        });
        Shapes.shapeMap.put("baking_process:cake_embryo",new int[][]{
                {1, 1, 1}
        });
        Shapes.shapeMap.put("baking_process:hard_bread_boat",new int[][]{
                {1, 1, 8}
        });
        Shapes.shapeMap.put("baking_process:mushroom_stew_hard_bread_boat",new int[][]{
                {1, 1, 8}
        });
        Shapes.shapeMap.put("baking_process:beetroot_soup_hard_bread_boat",new int[][]{
                {1, 1, 8}
        });

        //其他
        Shapes.shapeMap.put("baking_process:firewood",new int[][]{
                {1, 1, 1},{2, 2, 2},{3, 3, 3},{4, 4, 4},{5, 6, 5}
        });

        // 陶艺品胚
        Shapes.shapeMap.put("baking_process:flower_pot_embryo",new int[][]{
                {1, 1, 7}, {2, 4, 1}
        });

        // 切片食物
        Shapes.shapeMap.put("baking_process:carrot_slices", new int[][]{
                {1, 1, 11}, {2, 3, 12}
        });
        Shapes.shapeMap.put("baking_process:beetroot_slices", new int[][]{
                {1, 1, 9}, {2, 2, 11}, {3, 10, 12}
        });
        Shapes.shapeMap.put("baking_process:separate_potato_cubes", new int[][]{
                {1, 1, 11}, {2, 3, 12}, {4, 4, 2}
        });
        Shapes.shapeMap.put("baking_process:potato_cubes", new int[][]{
                {1, 1, 12}
        });
        Shapes.shapeMap.put("baking_process:separate_baked_potato_cubes", new int[][]{
                {1, 1, 11}, {2, 3, 12}, {4, 4, 2}
        });
        Shapes.shapeMap.put("baking_process:baked_potato_cubes", new int[][]{
                {1, 1, 12}
        });
        Shapes.shapeMap.put("baking_process:apple_slices", new int[][]{
                {1, 2, 2}
        });
        Shapes.shapeMap.put("baking_process:cod_cubes", new int[][]{
                {1, 1, 11}, {2, 4, 12}
        });
        Shapes.shapeMap.put("baking_process:cooked_cod_cubes", new int[][]{
                {1, 1, 11}, {2, 4, 12}
        });
        Shapes.shapeMap.put("baking_process:salmon_cubes", new int[][]{
                {1, 1, 11}, {2, 2, 12}, {3, 3, 2}
        });
        Shapes.shapeMap.put("baking_process:cooked_salmon_cubes", new int[][]{
                {1, 1, 11}, {2, 2, 12}, {3, 3, 2}
        });
        Shapes.shapeMap.put("baking_process:kitchen_waste", new int[][]{
                {1, 1, 2}
        });

        // 调料
        Shapes.shapeMap.put("baking_process:salt_cubes", new int[][]{
                {1, 1, 9}, {2, 2, 11}, {3, 10, 12}
        });
    }
}
