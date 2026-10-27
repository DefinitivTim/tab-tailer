# Changelog

## 1.0.0

First public release. Written with AI assistance (Claude by Anthropic), see the AI notice in the README.

### Tabs
- Reorder and hide creative tabs (`tab_order`, `hidden_tabs`), hide items everywhere (`hidden_items`)
- `show_hidden_tabs` shows hidden tabs again without deleting the list (off by default)
- Rename tabs, change their icon, create new tabs
- Fill tabs by item ID, `@mod`, `#tag`, wildcards, `tab:other_tab`, `*` (rest of the tab) and `!` (not)
- `exclusive` tabs take their items out of all other tabs; search keeps finding everything
- Mods that put their items in no creative tab can still be added with `@mod` or wildcards

### Sections
- Split tabs into sections with header rows, like Create: Aeronautics
- Side bar with one slot per section to jump there (max. 5 visible, scrollable), active slot stays on the clicked section
- Header look per section, tab or globally: colors, text shadow, bottom line, images (PNG from the config folder or texture ID), animated images
- Side bar colors per tab or globally

### Window
- Every normal tab uses the vanilla creative window by default, mods can no longer change it (`tab_style`)
- Own background, title color, slot highlight and scroll bar per tab or globally

### In-game editor
- Opened with `/tabtailor edit` or the Config button in the mod list
- Sort tabs by drag and drop, hide them, change title and icon
- Section editor with item picker (search by name, ID, `@mod`, `#tag`) and live preview
- Style editor with color picker, image list and live preview
- Only changed values are written, comments in the files are kept, a backup of every changed file goes to `config/tabtailor/_backup/`

### Commands
- `/tabtailor reload`, `edit`, `list`, `export <tab|all>`, `id`

### Compatibility
- Let's Do side tabs and Vanilla Backport's bundled tabs are turned off in tabs that have a Tab Tailor file
- Section banners of Create Simulated / Aeronautics, Create Propulsion and Create Flight Control are turned off in tabs whose contents Tab Tailor changes
- REMI uses Tab Tailor's tab order and hidden tabs, EMI keeps its panels clear of the side bar
- Works with mods that hand back stale or copied tab contents on a rebuild
