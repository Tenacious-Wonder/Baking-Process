package org.bakingprocess.recipe.serializer;

import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.bakingprocess.recipe.StoveRecipe;
import org.twcore.content.Content;
import org.twcore.registry.TWRegistries;

import java.util.Objects;

public class StoveRecipeSerializer implements RecipeSerializer<StoveRecipe> {

    @Override
    public StoveRecipe read(Identifier id, JsonObject json) {
        // 读取输入组件（物品 / 内容物 / 菜标识）
        StoveRecipe.Component input = readComponentFromString(JsonHelper.getString(json, "ingredient"));

        // 读取输出组件
        StoveRecipe.Component result = readComponentFromString(JsonHelper.getString(json, "result"));

        // 读取烘烤时间、最大输入数量
        int inputCount = JsonHelper.getInt(json, "MaxInputCount", 1);
        int stoveTime = JsonHelper.getInt(json, "stoveTime", 200);

        return new StoveRecipe(id, input, result, inputCount, stoveTime);
    }

    @Override
    public StoveRecipe read(Identifier id, PacketByteBuf buf) {
        StoveRecipe.Component input = readComponentFromString(buf.readString());
        StoveRecipe.Component result = readComponentFromString(buf.readString());

        int inputCount = buf.readInt();
        int stoveTime = buf.readVarInt();

        return new StoveRecipe(id, input, result, inputCount, stoveTime);
    }

    @Override
    public void write(PacketByteBuf buf, StoveRecipe recipe) {
        buf.writeString(componentToString(recipe.getInput()));
        buf.writeString(componentToString(recipe.getOutput()));

        buf.writeInt(recipe.getMaxInputCount());
        buf.writeVarInt(recipe.getBakingTime());
    }

    /**
     * 将组件转换为与配方 JSON 格式一致的字符串：{@code item|id} / {@code content|id} / {@code culinary|id}。
     */
    private static String componentToString(StoveRecipe.Component component) {
        if (component instanceof StoveRecipe.Component.ItemComp comp) {
            return "item|" + Registries.ITEM.getId(comp.stack().getItem());
        }
        if (component instanceof StoveRecipe.Component.ContentComp comp) {
            return "content|" + TWRegistries.CONTENT.getId(comp.content());
        }
        if (component instanceof StoveRecipe.Component.CulinaryComp comp) {
            return "culinary|" + comp.dishId();
        }
        throw new IllegalArgumentException("Unknown component: " + component);
    }

    /**
     * 从字符串中读取组件。
     * <p>使用'|'分割，格式为"类别|值"：</p>
     * <ul>
     *   <li>类别为"item"时：解析为物品堆栈</li>
     *   <li>类别为"content"时：解析为内容物（如面团）</li>
     *   <li>类别为"culinary"时：解析为菜标识（经 ItemStackVessel 与容器内菜肴匹配）</li>
     * </ul>
     * <p>如果字符串中不包含'|'，则默认按普通物品处理。</p>
     *
     * @param idString 要解析的字符串
     * @return 解析后的配方组件
     * @throws IllegalArgumentException 如果无法解析出有效组件
     * @throws NullPointerException 如果输入字符串为null
     */
    private static StoveRecipe.Component readComponentFromString(String idString) {
        Objects.requireNonNull(idString, "Input string cannot be null");

        // 去除前后空格
        idString = idString.trim();

        // 如果不包含'|'，按普通物品处理
        if (!idString.contains("|")) {
            return parseItemStack(idString);
        }

        // 分割字符串
        String[] args = idString.split("\\|", 2); // 限制分割为2部分
        if (args.length != 2) {
            throw new IllegalArgumentException("Invalid string format: '" + idString + "'. Expected format: 'type|value'");
        }

        String type = args[0].trim().toLowerCase();
        String value = args[1].trim();

        return switch (type) {
            case "item" -> parseItemStack(value);
            case "content" -> parseContentStack(value);
            case "culinary" -> parseCulinaryStack(value);
            default -> throw new IllegalArgumentException("Unknown type: '" + type + "'. Expected 'item', 'content' or 'culinary'");
        };
    }

    /**
     * 解析物品堆栈。
     */
    private static StoveRecipe.Component parseItemStack(String itemId) {
        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid item ID format: '" + itemId + "'");
        }

        Item item = Registries.ITEM.getOrEmpty(identifier)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

        return new StoveRecipe.Component.ItemComp(new ItemStack(item));
    }

    /**
     * 解析内容物。
     */
    private static StoveRecipe.Component parseContentStack(String contentId) {
        Identifier identifier = Identifier.tryParse(contentId);
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid content ID format: '" + contentId + "'");
        }

        Content content = TWRegistries.CONTENT.get(identifier);
        if (content == null) {
            throw new IllegalArgumentException("Content not found: " + contentId);
        }

        return new StoveRecipe.Component.ContentComp(content);
    }

    /**
     * 解析菜标识（culinary）：纯标识符，不依赖内容物注册。
     */
    private static StoveRecipe.Component parseCulinaryStack(String dishId) {
        Identifier identifier = Identifier.tryParse(dishId);
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid culinary dish ID format: '" + dishId + "'");
        }

        return new StoveRecipe.Component.CulinaryComp(identifier);
    }
}
