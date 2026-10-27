package io.github.definitivtim.tabtailor.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One entry of an item list in the config, for example {@code "@create"} or {@code "#c:ingots"}.
 *
 * <p>Priority decides who gets an item when several entries of one tab match it:
 * exact IDs (0) beat broad selectors (1), which beat the rest marker "*" (2).
 */
public sealed interface Selector {
    int EXACT = 0;
    int BROAD = 1;
    int REST = 2;

    String raw();

    int priority();

    /** Does this selector describe the given stack? {@code owner} is the tab being built (for "*"). */
    boolean matches(ItemStack stack, ResolveContext ctx, ResourceLocation owner);

    /** All stacks this selector stands for, in display order. */
    List<ItemStack> candidates(ResolveContext ctx, ResourceLocation owner);

    static Selector parse(String input) {
        String s = input.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("empty item entry");
        }
        if (s.startsWith("!")) {
            return new Not(s, parse(s.substring(1)));
        }
        if (s.equals("*")) {
            return new Rest(s);
        }
        if (s.startsWith("@")) {
            String ns = s.substring(1).trim();
            if (ns.isEmpty() || !ns.matches("[a-z0-9_.*-]+")) {
                throw new IllegalArgumentException("\"" + s + "\" is not a valid mod entry, write it like \"@create\"");
            }
            return new Mod(s, glob(ns));
        }
        if (s.startsWith("#")) {
            ResourceLocation tag = parseId(s.substring(1), s);
            return new Tag(s, TagKey.create(Registries.ITEM, tag));
        }
        if (s.startsWith("tab:")) {
            return new Tab(s, parseId(s.substring(4), s));
        }
        if (s.contains("*")) {
            String full = s.contains(":") ? s : "minecraft:" + s;
            if (!full.matches("[a-z0-9_.*-]+:[a-z0-9_./*-]+")) {
                throw new IllegalArgumentException("\"" + s + "\" is not a valid wildcard, write it like \"create:*_casing\"");
            }
            return new IdPattern(s, glob(full));
        }
        return new Exact(s, parseId(s, s));
    }

    private static ResourceLocation parseId(String id, String raw) {
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null) {
            throw new IllegalArgumentException("\"" + raw + "\" is not a valid ID (only a-z, 0-9, _ - . / allowed, e.g. \"minecraft:stone\")");
        }
        return rl;
    }

    private static Pattern glob(String text) {
        StringBuilder regex = new StringBuilder();
        for (String part : text.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }

    /**
     * Items of the registry that match, for selectors that found nothing in any creative tab. Some mods register
     * items without putting them into a creative tab at all (e.g. Aeronautics: Encased Fluid Pipes).
     */
    private static List<ItemStack> fromRegistry(Selector sel, ResolveContext ctx, ResourceLocation owner) {
        List<ItemStack> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || !item.isEnabled(ctx.features())) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (sel.matches(stack, ctx, owner)) {
                out.add(stack);
            }
        }
        return out;
    }

    private static List<ItemStack> filterPool(Selector sel, ResolveContext ctx, ResourceLocation owner) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : ctx.pool()) {
            if (sel.matches(stack, ctx, owner)) {
                out.add(stack);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ implementations

    /** {@code minecraft:stone}: every variant of that item found in any tab, or its default stack. */
    record Exact(String raw, ResourceLocation id) implements Selector {
        public int priority() {
            return EXACT;
        }

        @Nullable Item item() {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            return item.filter(i -> i != Items.AIR).orElse(null);
        }

        public boolean matches(ItemStack stack, ResolveContext ctx, ResourceLocation owner) {
            return Stacks.id(stack).equals(id);
        }

        public List<ItemStack> candidates(ResolveContext ctx, ResourceLocation owner) {
            Item item = item();
            if (item == null) {
                ctx.problem("unknown item \"" + raw + "\"");
                return List.of();
            }
            List<ItemStack> variants = ctx.variantsOf(item);
            if (!variants.isEmpty()) {
                return variants;
            }
            if (!item.isEnabled(ctx.features())) {
                return List.of();
            }
            return List.of(new ItemStack(item));
        }
    }

    /** {@code create:*_casing}: wildcard on the item ID. */
    record IdPattern(String raw, Pattern pattern) implements Selector {
        public int priority() {
            return BROAD;
        }

        public boolean matches(ItemStack stack, ResolveContext ctx, ResourceLocation owner) {
            return pattern.matcher(Stacks.id(stack).toString()).matches();
        }

        public List<ItemStack> candidates(ResolveContext ctx, ResourceLocation owner) {
            List<ItemStack> out = filterPool(this, ctx, owner);
            if (out.isEmpty()) {
                out = fromRegistry(this, ctx, owner);
            }
            if (out.isEmpty()) {
                ctx.problem("\"" + raw + "\" matches no item");
            }
            return out;
        }
    }

    /** {@code @create}: everything of a mod (namespace), in creative tab order. */
    record Mod(String raw, Pattern namespace) implements Selector {
        public int priority() {
            return BROAD;
        }

        public boolean matches(ItemStack stack, ResolveContext ctx, ResourceLocation owner) {
            return namespace.matcher(Stacks.id(stack).getNamespace()).matches();
        }

        public List<ItemStack> candidates(ResolveContext ctx, ResourceLocation owner) {
            List<ItemStack> out = filterPool(this, ctx, owner);
            if (out.isEmpty()) {
                out = fromRegistry(this, ctx, owner);
            }
            if (out.isEmpty()) {
                ctx.problem("\"" + raw + "\" matches no item (is the mod installed?)");
            }
            return out;
        }
    }

    /** {@code #c:ingots}: item tag. */
    record Tag(String raw, TagKey<Item> tag) implements Selector {
        public int priority() {
            return BROAD;
        }

        public boolean matches(ItemStack stack, ResolveContext ctx, ResourceLocation owner) {
            return stack.is(tag);
        }

        public List<ItemStack> candidates(ResolveContext ctx, ResourceLocation owner) {
            if (BuiltInRegistries.ITEM.getTag(tag).isEmpty()) {
                ctx.problem("unknown or empty item tag \"" + raw + "\"");
                return List.of();
            }
            return filterPool(this, ctx, owner);
        }
    }

    /** {@code tab:create:base}: what the tab normally contains. */
    record Tab(String raw, ResourceLocation tabId) implements Selector {
        public int priority() {
            return BROAD;
        }

        public boolean matches(ItemStack stack, ResolveContext ctx, ResourceLocation owner) {
            Set<ItemStack> set = ctx.originalSet(tabId);
            return set != null && set.contains(stack);
        }

        public List<ItemStack> candidates(ResolveContext ctx, ResourceLocation owner) {
            List<ItemStack> list = ctx.original(tabId);
            if (list == null) {
                ctx.problem("unknown tab in \"" + raw + "\" (see /tabtailor list)");
                return List.of();
            }
            return list;
        }
    }

    /** {@code *}: the normal contents of the tab being built that were not placed elsewhere. */
    record Rest(String raw) implements Selector {
        public int priority() {
            return REST;
        }

        public boolean matches(ItemStack stack, ResolveContext ctx, ResourceLocation owner) {
            return true;
        }

        public List<ItemStack> candidates(ResolveContext ctx, ResourceLocation owner) {
            List<ItemStack> list = ctx.original(owner);
            return list == null ? List.of() : list;
        }
    }

    /** {@code !@minecraft}: negation, mainly for "remove" and "exclude". */
    record Not(String raw, Selector inner) implements Selector {
        public int priority() {
            return BROAD;
        }

        public boolean matches(ItemStack stack, ResolveContext ctx, ResourceLocation owner) {
            if (inner instanceof Rest) {
                return false;
            }
            return !inner.matches(stack, ctx, owner);
        }

        public List<ItemStack> candidates(ResolveContext ctx, ResourceLocation owner) {
            return filterPool(this, ctx, owner);
        }
    }
}
