package io.github.definitivtim.tabtailor.core;

import io.github.definitivtim.tabtailor.config.SectionStyle;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which section every stack of a tab belongs to. The creative screen shows 9 slots per row: every section starts
 * on a new row, a titled section gets an empty header row on which the title is drawn.
 *
 * <p>Padding is computed from the stacks the screen actually shows, not from a stored count, so other mods that
 * filter or add creative items afterwards do not break the sections. Unknown stacks go into a block at the end.
 */
public final class SectionLayout {
    public static final int COLUMNS = 9;

    private static final Map<CreativeModeTab, SectionLayout> LAYOUTS = new IdentityHashMap<>();

    public record Header(Component title, ItemStack icon, SectionStyle style) {
    }

    /** One section as the screen sees it after padding: where its header row is and an icon for side tabs. */
    public record PlacedSection(Header header, int headerRow, ItemStack firstItem) {
        public ItemStack tabIcon() {
            return header.icon().isEmpty() ? firstItem : header.icon();
        }
    }

    /** Result of padding a concrete item list. */
    public record Padded(List<ItemStack> items, Int2ObjectMap<Header> headerRows, List<PlacedSection> sections) {
    }

    private final List<Header> headers; // per block, null entries = untitled block
    private final Object2IntOpenCustomHashMap<ItemStack> blockOf = new Object2IntOpenCustomHashMap<>(Stacks.STRATEGY);
    /** Fallback by item, for stacks whose components changed after the layout was built. */
    private final java.util.IdentityHashMap<net.minecraft.world.item.Item, Integer> blockOfItem = new java.util.IdentityHashMap<>();
    private boolean missLogged;

    /** @param blocks per block its header (or null) and its stacks */
    public SectionLayout(List<Header> headers, List<List<ItemStack>> blocks) {
        this.headers = new ArrayList<>(headers);
        this.blockOf.defaultReturnValue(-1);
        for (int b = 0; b < blocks.size(); b++) {
            for (ItemStack stack : blocks.get(b)) {
                blockOf.putIfAbsent(stack, b);
                blockOfItem.putIfAbsent(stack.getItem(), b);
            }
        }
    }

    public int sectionCount() {
        int n = 0;
        for (Header h : headers) {
            if (h != null) {
                n++;
            }
        }
        return n;
    }

    public Padded pad(List<ItemStack> raw) {
        List<List<ItemStack>> groups = new ArrayList<>(headers.size() + 1);
        for (int i = 0; i <= headers.size(); i++) {
            groups.add(new ArrayList<>());
        }
        int misses = 0;
        int itemFallbacks = 0;
        ItemStack firstMiss = null;
        for (ItemStack stack : raw) {
            if (stack.isEmpty()) {
                continue;
            }
            int block = blockOf.getInt(stack);
            if (block < 0) {
                Integer byItem = blockOfItem.get(stack.getItem());
                if (byItem != null) {
                    block = byItem;
                    itemFallbacks++;
                } else {
                    misses++;
                    if (firstMiss == null) {
                        firstMiss = stack;
                    }
                }
            }
            groups.get(block < 0 ? headers.size() : block).add(stack);
        }
        if ((misses > 0 || itemFallbacks > 0) && !missLogged) {
            missLogged = true;
            io.github.definitivtim.tabtailor.TabTailor.LOGGER.info("[Tab Tailor] Padding {} stacks: {} matched only by item, {} unknown (first unknown: {})",
                    raw.size(), itemFallbacks, misses, firstMiss == null ? "-" : Stacks.id(firstMiss));
        }

        List<ItemStack> out = new ArrayList<>(raw.size() + headers.size() * COLUMNS * 2);
        Int2ObjectMap<Header> headerRows = new Int2ObjectOpenHashMap<>();
        List<PlacedSection> sections = new ArrayList<>();
        for (int b = 0; b < groups.size(); b++) {
            List<ItemStack> items = groups.get(b);
            if (items.isEmpty()) {
                continue;
            }
            Header header = b < headers.size() ? headers.get(b) : null;
            if (header != null) {
                int row = out.size() / COLUMNS;
                headerRows.put(row, header);
                sections.add(new PlacedSection(header, row, items.get(0)));
                for (int i = 0; i < COLUMNS; i++) {
                    out.add(ItemStack.EMPTY);
                }
            }
            out.addAll(items);
            while (out.size() % COLUMNS != 0) {
                out.add(ItemStack.EMPTY);
            }
        }
        return new Padded(out, headerRows, sections);
    }

    // ------------------------------------------------------------------ registry of layouts per tab

    public static synchronized @Nullable SectionLayout get(@Nullable CreativeModeTab tab) {
        return tab == null ? null : LAYOUTS.get(tab);
    }

    public static synchronized int count() {
        return LAYOUTS.size();
    }

    static synchronized void replaceAll(Map<CreativeModeTab, SectionLayout> layouts) {
        LAYOUTS.clear();
        LAYOUTS.putAll(layouts);
    }
}
