package io.github.definitivtim.tabtailor.core;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

/** ItemStack helpers: stacks are equal when item and data components match (count is ignored). */
public final class Stacks {
    public static final Hash.Strategy<ItemStack> STRATEGY = new Hash.Strategy<>() {
        @Override
        public int hashCode(ItemStack stack) {
            return stack == null ? 0 : ItemStack.hashItemAndComponents(stack);
        }

        @Override
        public boolean equals(ItemStack a, ItemStack b) {
            return a == b || (a != null && b != null && ItemStack.isSameItemSameComponents(a, b));
        }
    };

    private Stacks() {
    }

    public static ObjectLinkedOpenCustomHashSet<ItemStack> newSet() {
        return new ObjectLinkedOpenCustomHashSet<>(STRATEGY);
    }

    public static ObjectLinkedOpenCustomHashSet<ItemStack> newSet(Collection<ItemStack> from) {
        ObjectLinkedOpenCustomHashSet<ItemStack> set = new ObjectLinkedOpenCustomHashSet<>(Math.max(16, from.size()), Hash.DEFAULT_LOAD_FACTOR, STRATEGY);
        set.addAll(from);
        return set;
    }

    public static ResourceLocation id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
