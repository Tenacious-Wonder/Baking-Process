package org.bakingprocess.recipe.serializer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.bakingprocess.recipe.GenericPlatingRecipe;
import org.bakingprocess.recipe.GenericPlatingRecipe.CountRange;
import org.bakingprocess.recipe.GenericPlatingRecipe.DishClass;
import org.bakingprocess.recipe.GenericPlatingRecipe.RequirementKey;

import java.util.HashMap;
import java.util.Map;

/**
 * 无序通用摆盘配方的序列化器（JSON + 网络）。
 *
 * <h2>JSON 格式</h2>
 * <pre>{@code
 * {
 *   "type": "baking_process:generic_plating",
 *   "container": "baking_process:iron_plate",
 *   "dish_class": "raw",
 *   "dish_name": "baking_process:generic/raw",
 *   "edible": false,
 *   "requirements": {
 *     "main/regular": 1,
 *     "side/medium": [1, 2],
 *     "seasoning": [0, 3]
 *   }
 * }
 * }</pre>
 *
 * <p>{@code requirements} 的 key 支持<b>子类级</b>（含 {@code /}，如 {@code main/regular}）
 * 与<b>大类级</b>（不含 {@code /}，如 {@code seasoning} 统计调料总量）；
 * value 支持整数（固定数量）或二元数组（区间）。</p>
 */
public class GenericPlatingRecipeSerializer implements RecipeSerializer<GenericPlatingRecipe> {

    @Override
    public GenericPlatingRecipe read(Identifier id, JsonObject json) {
        Identifier containerId = Identifier.tryParse(JsonHelper.getString(json, "container"));
        if (containerId == null) {
            throw new JsonParseException("Invalid container id in generic plating recipe: " + id);
        }

        DishClass dishClass = DishClass.fromId(JsonHelper.getString(json, "dish_class"));

        Identifier dishName = Identifier.tryParse(JsonHelper.getString(json, "dish_name"));
        if (dishName == null) {
            throw new JsonParseException("Invalid dish_name in generic plating recipe: " + id);
        }

        boolean edible = JsonHelper.getBoolean(json, "edible", false);

        JsonObject requirementsJson = JsonHelper.getObject(json, "requirements");
        Map<RequirementKey, CountRange> requirements = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : requirementsJson.entrySet()) {
            RequirementKey key = RequirementKey.fromString(entry.getKey());
            CountRange range = CountRange.CODEC
                    .parse(JsonOps.INSTANCE, entry.getValue())
                    .getOrThrow(false, error -> {
                        throw new JsonParseException(
                                "Invalid requirement value for '" + entry.getKey() + "' in generic plating recipe " + id + ": " + error);
                    });
            requirements.put(key, range);
        }

        return new GenericPlatingRecipe(id, containerId, dishClass, dishName, edible, requirements);
    }

    @Override
    public GenericPlatingRecipe read(Identifier id, PacketByteBuf buf) {
        Identifier containerId = buf.readIdentifier();
        DishClass dishClass = DishClass.fromId(buf.readString());
        Identifier dishName = buf.readIdentifier();
        boolean edible = buf.readBoolean();

        int size = buf.readVarInt();
        Map<RequirementKey, CountRange> requirements = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            RequirementKey key = RequirementKey.fromString(buf.readString());
            int min = buf.readVarInt();
            int max = buf.readVarInt();
            requirements.put(key, new CountRange(min, max));
        }

        return new GenericPlatingRecipe(id, containerId, dishClass, dishName, edible, requirements);
    }

    @Override
    public void write(PacketByteBuf buf, GenericPlatingRecipe recipe) {
        buf.writeIdentifier(recipe.getContainerId());
        buf.writeString(recipe.getDishClass().asString());
        buf.writeIdentifier(recipe.getDishName());
        buf.writeBoolean(recipe.isEdible());

        Map<RequirementKey, CountRange> requirements = recipe.getRequirements();
        buf.writeVarInt(requirements.size());
        for (Map.Entry<RequirementKey, CountRange> entry : requirements.entrySet()) {
            buf.writeString(entry.getKey().asString());
            buf.writeVarInt(entry.getValue().min());
            buf.writeVarInt(entry.getValue().max());
        }
    }
}
