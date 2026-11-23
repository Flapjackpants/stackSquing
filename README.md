# stackSquing

Client-only Fabric mod for Litematica material lists. It shows the list as a book and quill in game: view totals in raw or chest/stack/item format, organize items into filter-based groups, and mark items fulfilled (saved back to the file with an `a` suffix).

## Requirements

- Minecraft 26.3
- Fabric Loader 0.20.1+
- Fabric API
- Mod Menu (optional, for the settings screen)

## Build

```bash
./gradlew build
```

Copy `build/libs/stacksquing-1.0.0.jar` into `.minecraft/mods`.

## Use

- Press `B` (rebindable under Controls -> stackSquing) to open the book. No book item is needed.
- Press `B` with a chest, shulker box, hopper, dispenser, inventory, crafting table or furnace open to show the book beside it. The GUI scale drops so the recipe book and side book fit together.
- Type commands on the bottom row. Type `help` for the full list.
- PageUp/PageDown and the mouse wheel scroll. Up/Down recall history. Esc or `q` closes the book.
- Long item names scroll.

Litematica writes a **new timestamped file** on each export (e.g. `material_list_2026-07-14_23.17.52.txt`). After exporting again:

- `reload` — re-read the current file, or switch to a newer `material_list_*.txt` in the same folder
- `latest` — load the newest `material_list_*.txt` in the list folder
- `files` — list material lists in the list folder, newest first
- `open <n>` — open file n from the `files` list
- `open <path>` — open a file or directory (relative paths use the list folder)

If the open file changes on disk, fulfilling is blocked until you reload, so an old in-memory list cannot overwrite a new export.

### Quantity column

- `total` — show Total column
- `missing` — show Missing column (default)
- `available` — show Available column
- `need` — show still need after inventory (`max(0, missing − available)`)

### Quantity format

- `raw` — plain item counts
- `css` — chests/stacks/items, using each item's real max stack size (`5c+5s+10` for 8970 of a 64-stack item; signs/eggs/etc. use 16 per stack; unstackable items use chest slots of 27 with no `s` component)

### Groups

Groups are saved at `~/.config/stackSquing/groups.json` by default. Multiple `include` terms match if **any** term appears in the item name (e.g. `include:iron,anvil` matches both Iron Chain and Anvil). Terms may contain spaces (`include:light blue,blue wool`); commas separate terms. `exclude` terms still disqualify a match, unless a longer matching include term contains the exclude term (e.g. `include:light blue exclude:light` keeps Light Blue Wool).

- `groups` — list saved groups and on/off state
- `group add Deepslate include:deepslate`
- `group edit Deepslate include:brick exclude:cracked`
- `group add Oak include:oak exclude:dark`
- `group add Blues include:light blue,blue wool`
- `group on Deepslate` / `group off Deepslate`
- `group rename Deepslate DeepslateBlocks`
- `group remove Deepslate`
- `group order Deepslate 0`

Groups can also be added, edited and deleted from the settings screen.

When groups are enabled, matching items are shown in consecutive blocks with a summary row at the top of each group. Items that are not in any enabled group appear first. Items can appear in multiple groups; fulfilling any listing marks all copies as fulfilled and removes their quantity from group totals. Fully fulfilled groups are hidden by default (`hidefulfilled on`). Groups that belong to an enabled supergroup are only shown inside that supergroup.

### Supergroups

Supergroups nest matching item groups under one summary total (each item counted once, fulfilled items excluded). Membership uses the same `include`/`exclude` syntax as groups, but terms match **group names** (not item names). Saved in the same file as groups.

- `supergroups` — list saved supergroups and on/off state
- `supergroup add smelting include:glass,terracotta,nether`
- `supergroup edit wood include:oak,birch exclude:dark`
- `supergroup on Stone` / `supergroup off Stone`
- `supergroup rename Stone Rock`
- `supergroup remove Stone`
- `supergroup order Stone 0`

### Fulfillment

Items are numbered in display order. Mark an item fulfilled (strikes through in the book and appends `a` to the line in the file):

- `fulfill 3` or `a 3`
- `fulfill 14-24` or `a 14-24` — mark items 14 through 24 inclusive
- `unfulfill 3` or `u 3`
- `unfulfill 14-24` or `u 14-24` — clear fulfillment for a range

Partial fulfillment subtracts an amount from the Missing (and Need) column and writes `a <raw count>` at the end of the row. Amounts may be raw counts or use stacks/chests for that item (`2s`, `1c`, `2s+3`, `2s 3`). Repeated partials add up; once the amount reaches the item's Missing count the row becomes a plain `a`.

- `a 4, 450` — mark 450 of item 4 gathered
- `a 4, 2s+3` — mark 2 stacks + 3 gathered
- `u 4, 100` — un-add 100 (from a fully fulfilled item this leaves `a <missing − 100>`)
- `u 4` — clears both full and partial fulfillment

### Display

- `overview` — show every saved supergroup and group (on/off state, include/exclude terms, membership) without any items
- `hidefulfilled on` — hide groups/supergroups when all their items are fulfilled (default)
- `hidefulfilled off` — keep showing fully fulfilled groups
- `up` / `down` — scroll one row

## Settings (Mod Menu)

- List folder (default `.minecraft/config/litematica`)
- Groups file (default `~/.config/stackSquing/groups.json`)
- Quantity column and format
- Hide fulfilled groups
- Single page or two-page spread (falls back to single page on narrow windows)
- Per-group and per-supergroup on/off, plus adding, editing and deleting groups

The book follows the vanilla GUI scale setting. Column, format and layout changed with book commands are saved too.

## Example

`examples/sample_material_list.txt` is a sample Litematica export. Copy it into the list folder and `open` it from the book.
