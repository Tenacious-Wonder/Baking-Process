package org.bakingprocess.culinary.ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.process.PlayerAction;
import org.twcore.content.Content;
import org.twcore.process.playeraction.impl.AddContentPlayerAction;
import org.twcore.process.playeraction.impl.AddItemPlayerAction;
import org.twcore.registry.TWRegistries;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 原料来源。
 *
 * <p>原料可以是原版/模组物品（{@code add_item} 操作），也可以是 TW Core 内容物
 * （{@code add_content} 操作）。两个注册表中的 id 可能相同，因此食材表以
 * "来源 + id" 联合定位。</p>
 *
 * <p>泛型参数 T 为来源维护的注册表实体类型（{@link Item} / {@link Content}），
 * 归属判定（{@link #tryExtract}）与"实体 → id"映射（{@link #getIdOf}）分离，
 * 保证 id 只在自己的注册表上产生。</p>
 *
 * @param <T> 来源维护的注册表实体类型
 */
public abstract class IngredientSource<T> {
    /** 物品。 */
    public static final IngredientSource<Item> ITEM = new ItemSource();
    /** 内容物。 */
    public static final IngredientSource<Content> CONTENT = new ContentSource();

    private static final Map<String, IngredientSource<?>> REGISTRY = new HashMap<>();

    private final String id;

    protected IngredientSource(String id) {
        if (REGISTRY.put(id, this) != null) {
            throw new IllegalArgumentException("Duplicate ingredient source: " + id);
        }
        this.id = id;
    }

    public final String getId() {
        return id;
    }

    /** 若给定操作属于本来源，返回其原料实体；否则返回 {@code null}。 */
    @Nullable
    public abstract T tryExtract(PlayerAction action);

    /** 原料实体 → 注册表 id。 */
    public abstract Identifier getIdOf(T value);

    /** 若给定操作属于本来源，返回其原料 id；否则返回 {@code null}。 */
    @Nullable
    public final Identifier extractId(PlayerAction action) {
        T value = tryExtract(action);
        return value != null ? getIdOf(value) : null;
    }

    /** 所有已注册的来源。 */
    public static Collection<IngredientSource<?>> values() {
        return REGISTRY.values();
    }

    /** 序列化 Codec：按来源 id 分派。 */
    public static final Codec<IngredientSource<?>> CODEC = Codec.STRING.comapFlatMap(
            id -> Optional.ofNullable(REGISTRY.get(id))
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown ingredient source: " + id)),
            IngredientSource::getId
    );

    private static final class ItemSource extends IngredientSource<Item> {
        private ItemSource() {
            super("item");
        }

        @Override
        @Nullable
        public Item tryExtract(PlayerAction action) {
            if (action instanceof AddItemPlayerAction addItem) {
                return addItem.getItem();
            }
            return null;
        }

        @Override
        public Identifier getIdOf(Item value) {
            return Registries.ITEM.getId(value);
        }
    }

    private static final class ContentSource extends IngredientSource<Content> {
        private ContentSource() {
            super("content");
        }

        @Override
        @Nullable
        public Content tryExtract(PlayerAction action) {
            if (action instanceof AddContentPlayerAction addContent) {
                return addContent.getContent();
            }
            return null;
        }

        @Override
        public Identifier getIdOf(Content value) {
            return TWRegistries.CONTENT.getId(value);
        }
    }
}
