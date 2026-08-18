package org.bakingprocess.recipe.serializer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.bakingprocess.recipe.PlatingRecipe;
import org.twcore.api.process.PlayerAction;

import java.util.ArrayList;
import java.util.List;

/**
 * 摆盘配方序列化器，用于JSON格式的摆盘配方解析。
 *
 * <h2>JSON格式示例</h2>
 * <pre>{@code
 * {
 *   "type": "baking_process:plating",
 *   "container": "baking_process:iron_plate",
 *   "actions": [
 *     "add_item|minecraft:beef",
 *     "add_item|minecraft:sweet_berries",
 *   ],
 *   "dish_name": "baking_process:beef_berries",
 *   "eat_count": 2,
 *   "edible": false
 * }
 * }</pre>
 *
 * <h2>字段说明</h2>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>必需</th><th>描述</th></tr>
 *   <tr><td>type</td><td>string</td><td>是</td><td>配方类型，必须为"baking_process:plating"</td></tr>
 *   <tr><td>container</td><td>string</td><td>是</td><td>容器物品ID</td></tr>
 *   <tr><td>actions</td><td>string[]</td><td>是</td><td>操作序列，每个字符串格式为"操作类型|参数1|参数2..."</td></tr>
 *   <tr><td>dish_name</td><td>string</td><td>是</td><td>目标菜标识（显示名与模型派生的依据）</td></tr>
 *   <tr><td>eat_count</td><td>int</td><td>是</td><td>目标菜口数（edible=false 时为熟菜口数）</td></tr>
 *   <tr><td>edible</td><td>bool</td><td>否</td><td>摆完是否直接可食，默认 false</td></tr>
 * </table>
 *
 * @see PlatingRecipe
 * @see RecipeSerializer
 */
public class PlatingRecipeSerializer implements RecipeSerializer<PlatingRecipe> {

    @Override
    public PlatingRecipe read(Identifier id, JsonObject json) {
        // 1. 读取容器标识
        Identifier containerId = Identifier.tryParse(JsonHelper.getString(json, "container"));
        if (containerId == null) {
            throw new JsonParseException("Invalid container id in plating recipe: " + id);
        }

        // 2. 读取操作列表
        if (!json.has("actions")) {
            throw new JsonParseException("The plating recipe must contain an 'actions' field");
        }

        List<PlayerAction> actions = new ArrayList<>();
        for (JsonElement element : JsonHelper.getArray(json, "actions")) {
            String actionStr = element.getAsString();
            PlayerAction action = PlayerAction.fromString(actionStr);
            actions.add(action);
        }

        // 3. 读取目标菜数据
        Identifier dishName = Identifier.tryParse(JsonHelper.getString(json, "dish_name"));
        if (dishName == null) {
            throw new JsonParseException("Invalid dish name in plating recipe: " + id);
        }
        int eatCount = JsonHelper.getInt(json, "eat_count");
        boolean edible = JsonHelper.getBoolean(json, "edible", false);

        // 4. 创建并返回配方对象
        return new PlatingRecipe(id, containerId, actions, dishName, eatCount, edible);
    }

    @Override
    public PlatingRecipe read(Identifier id, PacketByteBuf buf) {
        // 1. 读取容器标识
        Identifier containerId = buf.readIdentifier();

        // 2. 读取操作列表
        int actionCount = buf.readVarInt();
        List<PlayerAction> actions = new ArrayList<>(actionCount);
        for (int i = 0; i < actionCount; i++) {
            String actionStr = buf.readString();
            PlayerAction action = PlayerAction.fromString(actionStr);
            actions.add(action);
        }

        // 3. 读取目标菜数据
        Identifier dishName = buf.readIdentifier();
        int eatCount = buf.readVarInt();
        boolean edible = buf.readBoolean();

        // 4. 创建并返回配方对象
        return new PlatingRecipe(id, containerId, actions, dishName, eatCount, edible);
    }

    @Override
    public void write(PacketByteBuf buf, PlatingRecipe recipe) {
        // 1. 写入容器标识
        buf.writeIdentifier(recipe.getContainerId());

        // 2. 写入操作列表
        List<PlayerAction> actions = recipe.getActions();
        buf.writeVarInt(actions.size());
        for (PlayerAction action : actions) {
            buf.writeString(action.toString());
        }

        // 3. 写入目标菜数据
        buf.writeIdentifier(recipe.getDishName());
        buf.writeVarInt(recipe.getEatCount());
        buf.writeBoolean(recipe.isEdible());
    }
}
