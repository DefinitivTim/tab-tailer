package io.github.definitivtim.tabtailor.config;

/** Files written on first start so the format documents itself. */
final class DefaultFiles {
    private DefaultFiles() {
    }

    static final String MAIN = """
            // Tab Tailor main settings. Lines starting with // are comments.
            // After editing, type /tabtailor reload in game (new tabs need a restart).
            {
              // Order of the creative tabs, from first to last.
              //   "create:base"   one tab by its ID (see /tabtailor list)
              //   "@create"       all tabs of a mod
              //   "create:*"      wildcard
              //   "*"             every tab not listed here, in its normal order
              // Search, Saved Hotbars, Survival Inventory and Operator Utilities stay where they are.
              "tab_order": [
                "*"
              ],

              // Tabs that are not shown. Their items can still be found in the search tab.
              "hidden_tabs": [
              ],

              // true shows the tabs from "hidden_tabs" again (e.g. to look at a mod's own tab), without
              // deleting the list. Off by default.
              "show_hidden_tabs": false,

              // Items removed from every tab, including search. Same syntax as "items" in tab files.
              "hidden_items": [
              ],

              // Show a tab per section on the left side of the creative window. Click one to jump there.
              "section_tabs": true,

              // Look of the creative window for all normal tabs. Mods can no longer change it unless you allow it.
              //   background      "vanilla", "mod" (keep the mod's own), a PNG in config/tabtailor/images/
              //                   or a texture ID. Same 256 x 256 layout as the vanilla tab_items.png.
              //   label_color     tab title color          slot_highlight   color over the slot under the mouse
              //   scroller        "vanilla", "mod" or a GUI sprite ID
              //   keep_mod_colors true = keep a mod's title / highlight colors when you set none here
              // A tab file can have its own "tab_style" with the same settings.
              "tab_style": {
                "background": "vanilla",
                "scroller": "vanilla"
              },

              // Colors of the section bar on the left. Each tab file can override them with its own "sidebar".
              //   background          frame of the bar
              //   slot                a normal section slot
              //   background_active   the slot of the section you are in
              //   border_active       frame and side notches of that slot
              //   button / button_active   arrow button without / with something to scroll to
              //   arrow  / arrow_active    the arrow itself, same two states
              "sidebar": {
                "background": "#C6C6C6",
                "slot": "#8B8B8B",
                "background_active": "#E6E6E6",
                "border_active": "#E08A00",
                "button": "#8B8B8B",
                "button_active": "#A6A6A6",
                "arrow": "#6F6F6F",
                "arrow_active": "#E08A00"
              },

              // Default look of section headers. Colors are "#RRGGBB" or "#AARRGGBB" (AA = opacity).
              "section_style": {
                "background": "#AA2B2B2B",
                "color": "#FFF0F0F0",
                "line": "#00000000",
                "shadow": true
              }
            }
            """;

    static final String EXAMPLE_TAB = """
            // EXAMPLE. Files starting with "_" are ignored. Copy this file to e.g. "vehicles.json" to try it.
            // One file per tab. The file name does not matter, only "tab".
            {
              // ID of the tab to change. With "new_tab": true a new tab with this ID is created (needs a restart).
              "tab": "tabtailor:vehicles",
              "new_tab": true,

              // Optional: new name and icon. Works for existing tabs too.
              // A translation key also works: "title": { "translate": "itemGroup.tools" }
              "title": "Vehicles",
              "icon": "minecraft:minecart",

              // true = items placed in this tab disappear from all other tabs (they stay searchable).
              // Items listed by exact ID in another tab's file are not taken away from that tab.
              "exclusive": false,

              // How items are written (everywhere in Tab Tailor):
              //   "minecraft:minecart"   one item (all its variants, e.g. every potion for "minecraft:potion")
              //   "minecraft:*_boat"     wildcard on item IDs
              //   "@create"              every item of a mod, in its creative tab order
              //   "#minecraft:boats"     item tag
              //   "tab:create:base"      everything that is normally in that tab
              //   "*"                    everything that is normally in THIS tab and not placed yet
              //   "!@minecraft"          "not": only useful in "remove" / "exclude"
              // If an item fits several entries, exact IDs win, then the first matching section.

              // Sections get a header row like in Create: Aeronautics. Order here = order in game.
              "sections": [
                {
                  "title": "Rails",
                  "icon": "minecraft:rail",
                  "items": [ "minecraft:rail", "minecraft:powered_rail", "minecraft:detector_rail", "minecraft:activator_rail" ]
                },
                {
                  "title": "Carts",
                  "items": [ "minecraft:*minecart" ],
                  // Per-section colors override "section_style".
                  "background": "#AA3A2A1A",
                  "color": "#FFFFE8B0"
                  // A picture behind the header instead of a color: put holz.png into config/tabtailor/images/
                  // (162 x 18 pixels or a multiple like 324 x 36), or use a texture ID like "farbound:textures/gui/holz.png".
                  // "image": "holz.png"
                  // Animated: stack the frames below each other in one PNG (e.g. 4 frames of 324 x 36 = 324 x 144).
                  // Frames are detected automatically, or set "image_frames": 4. Speed: "image_frametime": 3 (ticks per frame).
                  // "image_hover_only": true animates only while the mouse is over the header (like Create: Aeronautics).
                },
                {
                  "title": "Boats",
                  "items": [ "#minecraft:boats", "#minecraft:chest_boats" ],
                  "exclude": [ "minecraft:bamboo_raft", "minecraft:bamboo_chest_raft" ]
                }
              ],

              // Instead of "sections" you can write a plain list without headers:
              // "items": [ "@create", "minecraft:stick" ],

              // Colors of the section bar for this tab only (same names as "sidebar" in tabtailor.json):
              // "sidebar": { "background": "#6B4A2E", "slot": "#4A3220", "border_active": "#D9A441" },

              // Removed from the result at the end:
              "remove": [ ]

              // For an existing tab you can also keep its normal contents and only change a bit:
              // "add":    [ "minecraft:saddle" ],     (appended at the end)
              // "remove": [ "!@minecraft" ]           (keep only vanilla items in a vanilla tab)
            }
            """;

    static final String README = """
            Tab Tailor - quick reference
            ============================

            tabtailor.json      tab order, hidden tabs, hidden items, default section colors
            tabs/*.json         one file per tab: name, icon, contents, sections
            tabs/_example.json  commented example (files starting with "_" are ignored)
            export/             written by /tabtailor export, copy files from here into tabs/ to start

            In-game commands (client side, no cheats needed):
              /tabtailor reload           read all files again and rebuild the tabs
              /tabtailor list             all tab IDs in their current order (click to copy)
              /tabtailor export <tab|all> write the current contents of a tab as a ready-to-edit file
              /tabtailor id               ID, mod and tags of the item in your hand (click to copy)

            Item syntax (items, add, remove, exclude, hidden_items):
              minecraft:stone     exact item          @create          whole mod
              create:*_casing     wildcard            #c:ingots        item tag
              tab:create:base     contents of a tab   *                rest of this tab
              !@minecraft         everything that is NOT from minecraft

            Problems in the files are listed after /tabtailor reload and in logs/latest.log.
            """;
}
