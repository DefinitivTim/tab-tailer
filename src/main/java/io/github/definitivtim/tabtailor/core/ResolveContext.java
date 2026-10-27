package io.github.definitivtim.tabtailor.core;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything selectors need while tabs are rebuilt: the original contents of every tab (before Tab Tailor
 * changed anything) and a "pool" of all stacks that appear in any tab, in creative tab order.
 */
public final class ResolveContext {
    private final List<ItemStack> pool;
    private final Map<Item, List<ItemStack>> poolByItem = new IdentityHashMap<>();
    private final Map<ResourceLocation, List<ItemStack>> originals;
    private final Map<ResourceLocation, ObjectLinkedOpenCustomHashSet<ItemStack>> originalSets = new java.util.HashMap<>();
    private final FeatureFlagSet features;
    private final Set<String> problems = new LinkedHashSet<>();
    private String currentFile = "";

    public ResolveContext(List<ItemStack> pool, Map<ResourceLocation, List<ItemStack>> originals, FeatureFlagSet features) {
        this.pool = pool;
        this.originals = originals;
        this.features = features;
        for (ItemStack stack : pool) {
            poolByItem.computeIfAbsent(stack.getItem(), k -> new ArrayList<>(1)).add(stack);
        }
    }

    public List<ItemStack> pool() {
        return pool;
    }

    public List<ItemStack> variantsOf(Item item) {
        return poolByItem.getOrDefault(item, List.of());
    }

    public @Nullable List<ItemStack> original(ResourceLocation tabId) {
        return originals.get(tabId);
    }

    public @Nullable Set<ItemStack> originalSet(ResourceLocation tabId) {
        List<ItemStack> list = originals.get(tabId);
        if (list == null) {
            return null;
        }
        return originalSets.computeIfAbsent(tabId, id -> Stacks.newSet(list));
    }

    public FeatureFlagSet features() {
        return features;
    }

    public void setCurrentFile(String file) {
        this.currentFile = file;
    }

    public void problem(String message) {
        problems.add(currentFile.isEmpty() ? message : currentFile + ": " + message);
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }
}
