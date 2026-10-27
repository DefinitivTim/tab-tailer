package io.github.definitivtim.tabtailor.core;

import io.github.definitivtim.tabtailor.TabTailor;
import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.config.SectionDefinition;
import io.github.definitivtim.tabtailor.config.TabDefinition;
import io.github.definitivtim.tabtailor.config.TabTailorConfig;
import io.github.definitivtim.tabtailor.mixin.CreativeModeTabAccessor;
import io.github.definitivtim.tabtailor.mixin.CreativeModeTabRegistryAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Runs right after Minecraft has filled all normal creative tabs (and before the search tab is filled)
 * and rewrites their contents according to the config.
 */
public final class TabContentProcessor {
    private static volatile List<String> lastProblems = List.of();
    private static volatile int lastProblemsGeneration = -1;

    private TabContentProcessor() {
    }

    /**
     * What Tab Tailor wrote into a tab last time, and what the tab held before that. Some tabs are not rebuilt on every
     * rebuild (another mod or a cache skips them), so they still hold Tab Tailor's own result. Reading that as the
     * "original" would put everything into the first section that uses "tab:...". If a tab still holds exactly the
     * collection Tab Tailor wrote, the remembered original is used instead.
     */
    private record Written(Collection<ItemStack> display, Set<ItemStack> search, List<ItemStack> original, Set<ItemStack> originalSearch) {
    }

    private static final Map<CreativeModeTab, Written> WRITTEN = new IdentityHashMap<>();

    /**
     * Does the tab still hold (a copy of) what Tab Tailor wrote? True if it contains any stack that Tab Tailor moved
     * into this tab from elsewhere, because a freshly built tab cannot contain those.
     */
    private static boolean isOurOutput(Collection<ItemStack> current, Written written) {
        if (current == written.display()) {
            return true;
        }
        Set<ItemStack> original = Stacks.newSet(written.original());
        for (ItemStack stack : current) {
            if (!stack.isEmpty() && !original.contains(stack) && written.display().contains(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Problems found the last time tabs were built (unknown items, empty tags, ...). */
    public static List<String> lastProblems() {
        return lastProblems;
    }

    private record Snapshot(List<ItemStack> pool, Map<ResourceLocation, List<ItemStack>> originals, net.minecraft.world.flag.FeatureFlagSet features) {
    }

    /** Inputs of the last rebuild, so the editor can preview a tab without rebuilding everything. */
    private static volatile @Nullable Snapshot lastSnapshot;

    /** One block of a preview: header title (null = no header) and its items. */
    public record PreviewBlock(@Nullable net.minecraft.network.chat.Component title, List<ItemStack> items) {
    }

    public record Preview(List<PreviewBlock> blocks, List<String> problems) {
    }

    /**
     * What a tab would contain with the given definition, resolved like a real rebuild (without items that other
     * tabs take with "exclusive"). Null if the creative tabs were not built yet (no world loaded so far).
     */
    public static @Nullable Preview preview(TabDefinition def) {
        Snapshot snap = lastSnapshot;
        if (snap == null) {
            return null;
        }
        TabTailorConfig config = ConfigManager.get();
        ResolveContext ctx = new ResolveContext(snap.pool(), snap.originals(), snap.features());
        ctx.setCurrentFile(def.sourceFile());
        TabResult result = build(def, def.id(), ctx, config);
        List<Selector> hidden = config.hiddenItems();
        result.removeIf(stack -> anyMatch(hidden, stack, ctx, def.id()));
        List<PreviewBlock> blocks = new ArrayList<>();
        for (TabResult.MutableBlock block : result.blocks()) {
            blocks.add(new PreviewBlock(block.header() == null ? null : block.header().title(), List.copyOf(block.items())));
        }
        return new Preview(blocks, List.copyOf(ctx.problems()));
    }

    public static void process(CreativeModeTab.ItemDisplayParameters params) {
        try {
            run(params);
        } catch (Throwable t) {
            TabTailor.LOGGER.error("[Tab Tailor] Failed to apply creative tab changes, tabs are left unchanged", t);
            lastProblems = List.of("Internal error while building tabs: " + t + " (see logs/latest.log)");
            SectionLayout.replaceAll(Map.of());
        }
    }

    private static void run(CreativeModeTab.ItemDisplayParameters params) {
        TabTailorConfig config = ConfigManager.get();
        Registry<CreativeModeTab> registry = BuiltInRegistries.CREATIVE_MODE_TAB;

        // All normal tabs in their unmodified creative order (hidden ones included).
        List<CreativeModeTab> tabs = new ArrayList<>();
        Set<CreativeModeTab> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CreativeModeTab tab : CreativeModeTabRegistryAccessor.tabtailor$getSortedTabs()) {
            if (tab.getType() == CreativeModeTab.Type.CATEGORY && seen.add(tab)) {
                tabs.add(tab);
            }
        }
        for (CreativeModeTab tab : registry) {
            if (tab.getType() == CreativeModeTab.Type.CATEGORY && seen.add(tab)) {
                tabs.add(tab);
            }
        }

        // Snapshot of what every tab contains before we touch anything, plus the pool of all stacks.
        Map<ResourceLocation, List<ItemStack>> originals = new HashMap<>();
        Map<CreativeModeTab, Set<ItemStack>> originalSearch = new IdentityHashMap<>();
        ObjectLinkedOpenCustomHashSet<ItemStack> pool = Stacks.newSet();
        for (CreativeModeTab tab : tabs) {
            // Some mods put ItemStack.EMPTY into their contents as padding for their own section headers.
            Written written;
            synchronized (WRITTEN) {
                written = WRITTEN.get(tab);
            }
            List<ItemStack> parent;
            Set<ItemStack> search;
            if (written != null && isOurOutput(tab.getDisplayItems(), written)) {
                parent = written.original(); // not rebuilt since our last write
                search = Stacks.newSet(written.originalSearch());
            } else {
                parent = tab.getDisplayItems().stream().filter(stack -> !stack.isEmpty()).toList();
                search = Stacks.newSet(tab.getSearchTabDisplayItems());
                search.removeIf(ItemStack::isEmpty);
            }
            originals.put(registry.getKey(tab), parent);
            originalSearch.put(tab, search);
            pool.addAll(parent);
        }
        for (CreativeModeTab tab : tabs) {
            pool.addAll(originalSearch.get(tab));
        }
        ResolveContext ctx = new ResolveContext(new ArrayList<>(pool), originals, params.enabledFeatures());
        lastSnapshot = new Snapshot(List.copyOf(pool), Map.copyOf(originals), params.enabledFeatures());

        // Build every configured tab.
        Map<CreativeModeTab, TabResult> results = new IdentityHashMap<>();
        Map<ItemStack, CreativeModeTab> exclusiveClaims = new Object2ObjectOpenCustomHashMap<>(Stacks.STRATEGY);
        for (TabDefinition def : config.tabs()) {
            ctx.setCurrentFile(def.sourceFile());
            CreativeModeTab tab = registry.get(def.id());
            if (tab == null) {
                ctx.problem(def.newTab()
                        ? "new tab " + def.id() + " does not exist yet, restart the game once to create it"
                        : "unknown tab " + def.id() + " (see /tabtailor list, or add \"new_tab\": true to create it)");
                continue;
            }
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
                ctx.problem("tab " + def.id() + " is a special tab (search, hotbar, inventory) and cannot be edited");
                continue;
            }
            validate(def.remove(), ctx);
            TabResult result = build(def, def.id(), ctx, config);
            results.put(tab, result);
            if (def.exclusive()) {
                for (ItemStack stack : result.flatten()) {
                    exclusiveClaims.putIfAbsent(stack, tab);
                }
            }
        }

        ctx.setCurrentFile("tabtailor.json");
        List<Selector> hidden = config.hiddenItems();
        validate(hidden, ctx);

        // Write the results into the tabs.
        Map<CreativeModeTab, SectionLayout> layouts = new IdentityHashMap<>();
        Map<CreativeModeTab, Written> writtenNow = new IdentityHashMap<>();
        for (CreativeModeTab tab : tabs) {
            ResourceLocation id = registry.getKey(tab);
            TabResult result = results.get(tab);
            boolean configured = result != null;
            if (!configured) {
                if (exclusiveClaims.isEmpty() && hidden.isEmpty()) {
                    continue;
                }
                result = TabResult.plain(originals.get(id));
            }
            TabResult res = result;
            Predicate<ItemStack> drop = stack -> {
                if (anyMatch(hidden, stack, ctx, id)) {
                    return true;
                }
                CreativeModeTab owner = exclusiveClaims.get(stack);
                return owner != null && owner != tab && !res.exact().contains(stack);
            };
            boolean changed = res.removeIf(drop);
            if (!configured && !changed) {
                continue;
            }

            List<ItemStack> flat = res.flatten();
            CreativeModeTabAccessor accessor = (CreativeModeTabAccessor) tab;
            Set<ItemStack> display = Stacks.newSet(flat);
            accessor.tabtailor$setDisplayItems(display);
            // Search keeps exactly what the tab normally offers, so moved items stay findable and items that a mod
            // deliberately keeps out of search (TabVisibility.PARENT_TAB_ONLY) stay out. Only stacks that did not
            // exist in any tab before (default stacks for exact IDs) are added, otherwise they could not be found.
            // Adding existing stacks again makes mods that insert them into the search tab later crash
            // ("already exists in the tab's list", e.g. Create: Trading Floor).
            Set<ItemStack> search = Stacks.newSet(originalSearch.get(tab));
            for (ItemStack stack : flat) {
                if (!pool.contains(stack)) {
                    search.add(stack);
                }
            }
            if (!hidden.isEmpty()) {
                search.removeIf(stack -> anyMatch(hidden, stack, ctx, id));
            }
            accessor.tabtailor$setDisplayItemsSearchTab(search);
            writtenNow.put(tab, new Written(display, search, originals.get(id), originalSearch.get(tab)));

            SectionLayout layout = res.layout();
            if (layout != null) {
                layouts.put(tab, layout);
            }
        }
        SectionLayout.replaceAll(layouts);
        synchronized (WRITTEN) {
            WRITTEN.clear();
            WRITTEN.putAll(writtenNow);
        }
        io.github.definitivtim.tabtailor.compat.remi.RemiCompat.reloadLater();
        TabTailor.LOGGER.info("[Tab Tailor] Rebuilt creative tabs on {}: {} configured, {} with sections, {} problem(s)",
                Thread.currentThread().getName(), results.size(), layouts.size(), ctx.problems().size());

        List<String> problems = new ArrayList<>(ctx.problems());
        if (!problems.equals(lastProblems) || lastProblemsGeneration != config.generation()) {
            for (String p : problems) {
                TabTailor.LOGGER.warn("[Tab Tailor] {}", p);
            }
        }
        lastProblems = List.copyOf(problems);
        lastProblemsGeneration = config.generation();
    }

    private static TabResult build(TabDefinition def, ResourceLocation id, ResolveContext ctx, TabTailorConfig config) {
        List<TabResult.MutableBlock> blocks = new ArrayList<>();
        ObjectLinkedOpenCustomHashSet<ItemStack> exact = Stacks.newSet();

        if (def.sections() == null) {
            // Keep the normal contents, append "add".
            List<ItemStack> original = ctx.original(id);
            List<ItemStack> list = new ArrayList<>(original == null ? List.of() : original);
            Set<ItemStack> present = Stacks.newSet(list);
            for (Selector sel : def.add()) {
                for (ItemStack stack : sel.candidates(ctx, id)) {
                    if (present.add(stack)) {
                        list.add(stack);
                    }
                    if (sel.priority() == Selector.EXACT) {
                        exact.add(stack);
                    }
                }
            }
            blocks.add(new TabResult.MutableBlock(null, list));
        } else {
            List<SectionDefinition> sections = def.sections();
            // outputs[section][selector] keeps the display order of the config while claims are resolved by priority.
            List<List<List<ItemStack>>> outputs = new ArrayList<>();
            for (SectionDefinition section : sections) {
                List<List<ItemStack>> perSelector = new ArrayList<>();
                for (int i = 0; i < section.items().size(); i++) {
                    perSelector.add(new ArrayList<>());
                }
                outputs.add(perSelector);
                validate(section.exclude(), ctx);
            }
            Set<ItemStack> claimed = Stacks.newSet();
            for (int priority = Selector.EXACT; priority <= Selector.REST; priority++) {
                for (int s = 0; s < sections.size(); s++) {
                    SectionDefinition section = sections.get(s);
                    for (int i = 0; i < section.items().size(); i++) {
                        Selector sel = section.items().get(i);
                        if (sel.priority() != priority) {
                            continue;
                        }
                        for (ItemStack stack : sel.candidates(ctx, id)) {
                            if (anyMatch(section.exclude(), stack, ctx, id)) {
                                continue;
                            }
                            if (claimed.add(stack)) {
                                outputs.get(s).get(i).add(stack);
                                if (priority == Selector.EXACT) {
                                    exact.add(stack);
                                }
                            }
                        }
                    }
                }
            }
            for (int s = 0; s < sections.size(); s++) {
                SectionDefinition section = sections.get(s);
                List<ItemStack> items = new ArrayList<>();
                for (List<ItemStack> part : outputs.get(s)) {
                    items.addAll(part);
                }
                SectionLayout.Header header = null;
                if (section.title() != null) {
                    header = new SectionLayout.Header(section.title(), iconStack(section.icon()),
                            section.style().inherit(def.sectionStyle()).inherit(config.sectionStyle()));
                }
                blocks.add(new TabResult.MutableBlock(header, items));
            }
        }

        if (!def.remove().isEmpty()) {
            for (TabResult.MutableBlock block : blocks) {
                block.items().removeIf(stack -> anyMatch(def.remove(), stack, ctx, id));
            }
        }
        return new TabResult(blocks, exact);
    }

    private static ItemStack iconStack(@Nullable ResourceLocation id) {
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static boolean anyMatch(List<Selector> selectors, ItemStack stack, ResolveContext ctx, ResourceLocation owner) {
        for (Selector sel : selectors) {
            if (sel.matches(stack, ctx, owner)) {
                return true;
            }
        }
        return false;
    }

    /** Reports unknown exact IDs, tags and tabs in lists that are only used for matching. */
    private static void validate(List<Selector> selectors, ResolveContext ctx) {
        for (Selector sel : selectors) {
            Selector inner = sel instanceof Selector.Not not ? not.inner() : sel;
            if (inner instanceof Selector.Exact exact && exact.item() == null) {
                ctx.problem("unknown item \"" + exact.raw() + "\"");
            } else if (inner instanceof Selector.Tag tag && BuiltInRegistries.ITEM.getTag(tag.tag()).isEmpty()) {
                ctx.problem("unknown or empty item tag \"" + tag.raw() + "\"");
            } else if (inner instanceof Selector.Tab tab && ctx.original(tab.tabId()) == null) {
                ctx.problem("unknown tab in \"" + tab.raw() + "\" (see /tabtailor list)");
            }
        }
    }

    /** Result of building one tab: blocks (sections) of stacks. */
    static final class TabResult {
        record MutableBlock(@Nullable SectionLayout.Header header, List<ItemStack> items) {
        }

        private final List<MutableBlock> blocks;
        private final Set<ItemStack> exact;

        TabResult(List<MutableBlock> blocks, Set<ItemStack> exact) {
            this.blocks = blocks;
            this.exact = exact;
        }

        static TabResult plain(@Nullable List<ItemStack> items) {
            List<MutableBlock> blocks = new ArrayList<>();
            blocks.add(new MutableBlock(null, new ArrayList<>(items == null ? List.of() : items)));
            return new TabResult(blocks, Stacks.newSet());
        }

        List<MutableBlock> blocks() {
            return blocks;
        }

        Set<ItemStack> exact() {
            return exact;
        }

        boolean removeIf(Predicate<ItemStack> predicate) {
            boolean changed = false;
            for (MutableBlock block : blocks) {
                changed |= block.items().removeIf(predicate);
            }
            return changed;
        }

        List<ItemStack> flatten() {
            List<ItemStack> out = new ArrayList<>();
            for (MutableBlock block : blocks) {
                out.addAll(block.items());
            }
            return out;
        }

        @Nullable SectionLayout layout() {
            List<SectionLayout.Header> headers = new ArrayList<>();
            List<List<ItemStack>> items = new ArrayList<>();
            boolean anyHeader = false;
            for (MutableBlock block : blocks) {
                if (block.items().isEmpty()) {
                    continue;
                }
                anyHeader |= block.header() != null;
                headers.add(block.header());
                items.add(block.items());
            }
            return anyHeader ? new SectionLayout(headers, items) : null;
        }
    }
}
