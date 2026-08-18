package org.bakingprocess.registry;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.FoodComponent;

public class ModFoodComponents {
    // 面食
    public static final FoodComponent HARD_BREAD = new FoodComponent.Builder()
            .hunger(6).saturationModifier(0.6f).build();
    public static final FoodComponent SMALL_BREAD = new FoodComponent.Builder()
            .hunger(2).saturationModifier(0.4f).build();
    public static final FoodComponent BAGUETTE = new FoodComponent.Builder()
            .hunger(8).saturationModifier(0.7f).build();
    public static final FoodComponent TOAST = new FoodComponent.Builder()
            .hunger(5).saturationModifier(0.8f).build();

    // 可食用容器
    public static final FoodComponent HARD_BREAD_BOAT = new FoodComponent.Builder()
            .hunger(3).saturationModifier(0.6f).build();

    // 切片食物
    public static final FoodComponent CARROT_SLICES = new FoodComponent.Builder()
            .hunger(1).saturationModifier(0.2f).build();
    public static final FoodComponent BEETROOT_SLICES = new FoodComponent.Builder()
            .hunger(1).saturationModifier(0.15f).build();
    public static final FoodComponent CARROT_HEAD = new FoodComponent.Builder()
            .hunger(1).saturationModifier(0.1f).build();
    public static final FoodComponent COD_CUBES = new FoodComponent.Builder()
            .hunger(1).saturationModifier(0.05f).build();
    public static final FoodComponent COD_HEAD = new FoodComponent.Builder()
            .hunger(1).saturationModifier(0.05f).build();
    public static final FoodComponent COOKED_COD_CUBES = new FoodComponent.Builder()
            .hunger(2).saturationModifier(0.3f).build();
    public static final FoodComponent COOKED_COD_HEAD = new FoodComponent.Builder()
            .hunger(1).saturationModifier(0.3f).build();
    public static final FoodComponent SALMON_CUBES = new FoodComponent.Builder()
            .hunger(1).saturationModifier(0.1f).build();
    public static final FoodComponent COOKED_SALMON_CUBES = new FoodComponent.Builder()
            .hunger(2).saturationModifier(0.4f).build();
    public static final FoodComponent SEPARATE_POTATO_CUBES = new FoodComponent.Builder()
            .hunger(1).saturationModifier(0.15f).build();
    public static final FoodComponent POTATO_CUBES = new FoodComponent.Builder()
            .hunger(1).saturationModifier(0.3f)
            .statusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0), 0.3f)
            .build();
    public static final FoodComponent SEPARATE_COOKED_POTATO_CUBES = new FoodComponent.Builder()
            .hunger(3).saturationModifier(0.4f).build();
    public static final FoodComponent COOKED_POTATO_CUBES = new FoodComponent.Builder()
            .hunger(5).saturationModifier(0.6f).build();

    // 饮品
    public static final FoodComponent MILK = new FoodComponent.Builder()
            .hunger(1).saturationModifier(0.4f).build();

    // 调料
    public static final FoodComponent SUGAR_FLOUR = new FoodComponent.Builder()
            .hunger(0).saturationModifier(0.05f).meat()
            .build();
}