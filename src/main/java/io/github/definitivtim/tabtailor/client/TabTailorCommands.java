package io.github.definitivtim.tabtailor.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.config.TabTailorConfig;
import io.github.definitivtim.tabtailor.core.TabContentProcessor;
import io.github.definitivtim.tabtailor.core.TabOrdering;
import io.github.definitivtim.tabtailor.mixin.CreativeModeTabRegistryAccessor;
import io.github.definitivtim.tabtailor.mixin.CreativeModeTabsAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.CreativeModeTabSearchRegistry;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** /tabtailor reload | edit | list | export <tab|all> | id */
public final class TabTailorCommands {
    private static final int MAX_PROBLEMS_IN_CHAT = 12;

    private TabTailorCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tabtailor")
                .then(Commands.literal("reload").executes(TabTailorCommands::reload))
                .then(Commands.literal("edit").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    // after the chat screen has closed
                    mc.tell(() -> mc.setScreen(new io.github.definitivtim.tabtailor.client.editor.TabEditorScreen(null)));
                    return 1;
                }))
                .then(Commands.literal("list").executes(TabTailorCommands::list))
                .then(Commands.literal("id").executes(TabTailorCommands::id))
                .then(Commands.literal("export")
                        .then(Commands.argument("tab", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("all");
                                    return SharedSuggestionProvider.suggestResource(BuiltInRegistries.CREATIVE_MODE_TAB.keySet(), builder);
                                })
                                .executes(TabTailorCommands::export))));
    }

    // ------------------------------------------------------------------ reload

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        TabTailorConfig config = ConfigManager.reload();
        List<String> problems = new ArrayList<>(config.problems());
        rebuildTabsNow();
        problems.addAll(TabContentProcessor.lastProblems());

        for (var def : config.tabs()) {
            if (def.newTab() && !BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(def.id())) {
                problems.add(def.sourceFile() + ": new tab " + def.id() + " needs a game restart");
            }
        }
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(problems));

        ctx.getSource().sendSuccess(() -> Component.literal("[Tab Tailor] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Reloaded " + config.tabs().size() + " tab file(s), ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(unique.size() + " problem(s)")
                        .withStyle(unique.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        for (int i = 0; i < Math.min(unique.size(), MAX_PROBLEMS_IN_CHAT); i++) {
            String p = unique.get(i);
            ctx.getSource().sendSuccess(() -> Component.literal(" - " + p).withStyle(ChatFormatting.RED), false);
        }
        if (unique.size() > MAX_PROBLEMS_IN_CHAT) {
            int more = unique.size() - MAX_PROBLEMS_IN_CHAT;
            ctx.getSource().sendSuccess(() -> Component.literal(" ... and " + more + " more, see logs/latest.log").withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    /** Rebuilds all tab contents and the search index right away, like the creative screen would. */
    public static void rebuildTabsNow() {
        CreativeModeTabsAccessor.tabtailor$setCachedParameters(null);
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return; // rebuilt automatically when the creative screen opens
        }
        boolean op = mc.options.operatorItemsTab().get() && player.canUseGameMasterBlocks();
        HolderLookup.Provider registries = mc.level.registryAccess();
        if (CreativeModeTabs.tryRebuildTabContents(player.connection.enabledFeatures(), op, registries)) {
            SessionSearchTrees trees = player.connection.searchTrees();
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                if (tab.hasSearchBar()) {
                    List<ItemStack> items = List.copyOf(tab.getDisplayItems());
                    trees.updateCreativeTooltips(registries, items, CreativeModeTabSearchRegistry.getNameSearchKey(tab));
                    trees.updateCreativeTags(items, CreativeModeTabSearchRegistry.getTagSearchKey(tab));
                }
            }
        }
    }

    // ------------------------------------------------------------------ list

    private static int list(CommandContext<CommandSourceStack> ctx) {
        TabTailorConfig config = ConfigManager.get();
        List<CreativeModeTab> shown = CreativeModeTabRegistry.getSortedCreativeModeTabs();
        ctx.getSource().sendSuccess(() -> Component.literal("[Tab Tailor] Tabs in display order (click an ID to copy):")
                .withStyle(ChatFormatting.GOLD), false);
        int page = 1;
        int index = 0;
        for (CreativeModeTab tab : shown) {
            if (index % 10 == 0) {
                int p = page++;
                ctx.getSource().sendSuccess(() -> Component.literal("Page " + p).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            }
            index++;
            sendTabLine(ctx, tab, config, false);
        }
        List<CreativeModeTab> hidden = CreativeModeTabRegistryAccessor.tabtailor$getSortedTabs().stream()
                .filter(t -> TabOrdering.isHidden(t, config)).toList();
        if (!hidden.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Hidden").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            for (CreativeModeTab tab : hidden) {
                sendTabLine(ctx, tab, config, true);
            }
        }
        return shown.size();
    }

    private static void sendTabLine(CommandContext<CommandSourceStack> ctx, CreativeModeTab tab, TabTailorConfig config, boolean hidden) {
        ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        String idText = String.valueOf(id);
        MutableComponent line = Component.literal(" ")
                .append(copyable(idText, hidden ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA))
                .append(Component.literal("  " + tab.getDisplayName().getString() + "  ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("(" + tab.getDisplayItems().size() + " items)").withStyle(ChatFormatting.GRAY));
        if (id != null && config.tab(id) != null) {
            line.append(Component.literal("  " + config.tab(id).sourceFile()).withStyle(ChatFormatting.GREEN));
        }
        ctx.getSource().sendSuccess(() -> line, false);
    }

    // ------------------------------------------------------------------ id

    private static int id(CommandContext<CommandSourceStack> ctx) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[Tab Tailor] Hold an item in your main hand."));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ctx.getSource().sendSuccess(() -> Component.literal("[Tab Tailor] ").withStyle(ChatFormatting.GOLD)
                .append(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  "))
                .append(copyable(id.toString(), ChatFormatting.AQUA))
                .append(Component.literal("  "))
                .append(copyable("@" + id.getNamespace(), ChatFormatting.YELLOW)), false);
        String tags = stack.getTags().map(t -> "#" + t.location()).sorted().collect(Collectors.joining(" "));
        if (!tags.isEmpty()) {
            MutableComponent tagLine = Component.literal(" Tags: ").withStyle(ChatFormatting.GRAY);
            stack.getTags().map(t -> "#" + t.location()).sorted().forEach(t ->
                    tagLine.append(copyable(t, ChatFormatting.DARK_AQUA)).append(Component.literal(" ")));
            ctx.getSource().sendSuccess(() -> tagLine, false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ export

    private static int export(CommandContext<CommandSourceStack> ctx) {
        String arg = StringArgumentType.getString(ctx, "tab").trim();
        List<CreativeModeTab> tabs = new ArrayList<>();
        if (arg.equalsIgnoreCase("all")) {
            for (CreativeModeTab tab : CreativeModeTabRegistryAccessor.tabtailor$getSortedTabs()) {
                if (tab.getType() == CreativeModeTab.Type.CATEGORY) {
                    tabs.add(tab);
                }
            }
        } else {
            ResourceLocation id = ResourceLocation.tryParse(arg.contains(":") ? arg : "minecraft:" + arg);
            CreativeModeTab tab = id == null ? null : BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
            if (tab == null || tab.getType() != CreativeModeTab.Type.CATEGORY) {
                ctx.getSource().sendFailure(Component.literal("[Tab Tailor] Unknown tab \"" + arg + "\", see /tabtailor list"));
                return 0;
            }
            tabs.add(tab);
        }
        if (tabs.stream().allMatch(t -> t.getDisplayItems().isEmpty())) {
            ctx.getSource().sendFailure(Component.literal("[Tab Tailor] Tabs are empty right now. Open the creative inventory once, then export."));
            return 0;
        }

        Path dir = ConfigManager.exportDir();
        int written = 0;
        try {
            Files.createDirectories(dir);
            for (CreativeModeTab tab : tabs) {
                ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
                Path file = dir.resolve(id.getNamespace() + "__" + id.getPath().replace('/', '_') + ".json");
                Files.writeString(file, exportText(tab, id), StandardCharsets.UTF_8);
                written++;
            }
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("[Tab Tailor] Export failed: " + e.getMessage()));
            return 0;
        }
        int count = written;
        ctx.getSource().sendSuccess(() -> Component.literal("[Tab Tailor] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Exported " + count + " tab(s) to ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("config/tabtailor/export/").withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, dir.toAbsolutePath().toString()))))
                .append(Component.literal(". Copy a file into tabs/ to use it.").withStyle(ChatFormatting.WHITE)), false);
        return count;
    }

    private static String exportText(CreativeModeTab tab, ResourceLocation id) {
        Set<String> itemIds = new LinkedHashSet<>();
        for (ItemStack stack : tab.getDisplayItems()) {
            itemIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        ItemStack icon = tab.getIconItem();
        StringBuilder out = new StringBuilder();
        out.append("// Exported by /tabtailor export. Copy into config/tabtailor/tabs/ to use it.\n");
        out.append("// Each ID stands for all variants of that item (e.g. every potion).\n");
        out.append("{\n");
        out.append("  \"tab\": \"").append(id).append("\",\n");
        out.append("  \"title\": ").append(jsonString(tab.getDisplayName().getString())).append(",\n");
        if (!icon.isEmpty()) {
            out.append("  \"icon\": \"").append(BuiltInRegistries.ITEM.getKey(icon.getItem())).append("\",\n");
        }
        out.append("  \"items\": [\n");
        int i = 0;
        for (String itemId : itemIds) {
            out.append("    \"").append(itemId).append('"').append(++i < itemIds.size() ? "," : "").append('\n');
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    private static String jsonString(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static MutableComponent copyable(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(Style.EMPTY
                .withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy"))));
    }
}
