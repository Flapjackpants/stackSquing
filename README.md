# stackSquing

Interactive terminal tool for Litematica ASCII material lists. View totals in raw or chest/stack/item format, organize items into filter-based groups, and mark items fulfilled (saved back to the file with an `a` suffix).

## Install

macOS, Linux, or Windows (WSL / Git Bash):

```bash
curl -fsSL https://raw.githubusercontent.com/Flapjackpants/stackSquing/main/install.sh | bash
```

That downloads the source, builds it, and installs `stackSquing` to `~/.local/bin`. Make sure that directory is on your `PATH`:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

Custom install location or git ref:

```bash
curl -fsSL https://raw.githubusercontent.com/Flapjackpants/stackSquing/main/install.sh | INSTALL_PREFIX=/usr/local bash
curl -fsSL https://raw.githubusercontent.com/Flapjackpants/stackSquing/main/install.sh | STACKSQUING_REF=v1.0.0 bash
```

**Windows note:** use [WSL](https://learn.microsoft.com/windows/wsl/) (recommended) or Git Bash with CMake and a C++ toolchain. Native PowerShell/cmd is not supported (the UI needs a Unix-style terminal + ncurses).

### Requirements

- C++17 compiler (Clang or GCC; Xcode CLT on macOS)
- CMake 3.16+
- ncurses (macOS: via Xcode CLT; Linux: e.g. `sudo apt install libncurses-dev`)
- `curl`, `tar`

## Build from a local clone

### Using build.sh (recommended)

```bash
./build.sh          # clean, build, and install to ~/.local/bin
./build.sh all      # same as above
./build.sh clean    # remove build/
./build.sh build    # configure and compile
./build.sh install  # install to ~/.local/bin
```

Install to a custom location:

```bash
INSTALL_PREFIX=/usr/local ./build.sh install
```

### Manual build

```bash
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
cmake --install build --prefix ~/.local
```

Or copy the binary manually:

```bash
cp build/stackSquing ~/.local/bin/
```

Then run:

```bash
stackSquing /path/to/material_list.txt
stackSquing /path/to/litematica/   # loads newest material_list_*.txt in that folder
```

## Usage

Type `help` inside the app for a full scrollable list of commands.

Litematica writes a **new timestamped file** on each export (e.g. `material_list_2026-07-14_23.17.52.txt`). The title bar shows which file is open. After exporting again:

- `reload` — re-read the current file, or automatically switch to a newer `material_list_*.txt` in the same folder
- `latest` — always load the newest `material_list_*.txt` in the same folder
- `open <path>` — open a specific file or directory

If the open file changes on disk while the app is running, the status bar prompts you to reload; fulfilling is blocked until you do, so an old in-memory list cannot overwrite a new export.

### Quantity column

- `total` — show Total column
- `missing` — show Missing column (default)
- `available` — show Available column
- `need` — show still need after inventory (`max(0, missing − available)`)

### Quantity format

- `raw` — plain item counts
- `css` — chests/stacks/items, using each item's real max stack size from Java Edition (`5c+5s+10` for 8970 of a 64-stack item; signs/eggs/etc. use 16 per stack; unstackable items use chest slots of 27 with no `s` component)

### Groups

Groups are saved globally at `~/.config/stackSquing/groups.json`. Multiple `include` terms match if **any** term appears in the item name (e.g. `include:iron,anvil` matches both Iron Chain and Anvil). Terms may contain spaces (`include:light blue,blue wool`); commas separate terms. `exclude` terms still disqualify a match.

- `groups` — list saved groups and on/off state
- `group add Deepslate include:deepslate`
- `group edit Deepslate include:brick exclude:cracked`
- `group add Oak include:oak exclude:dark`
- `group add Blues include:light blue,blue wool`
- `group on Deepslate` / `group off Deepslate`
- `group rename Deepslate DeepslateBlocks`
- `group remove Deepslate`
- `group order Deepslate 0`

When groups are enabled, matching items are shown in consecutive blocks with a highlighted summary row at the top of each group. Items that are not in any enabled group appear first (above group blocks). Item names use leader dots to the quantity (table-of-contents style) so counts are easy to scan. Items can appear in multiple groups; fulfilling any listing marks all copies as fulfilled and removes their quantity from group totals. Fully fulfilled groups are hidden by default (`hidefulfilled on`). Groups that belong to an enabled supergroup are only shown inside that supergroup, not on their own.

### Supergroups

Supergroups nest matching item groups under one summary total (each item counted once, fulfilled items excluded). Membership uses the same `include`/`exclude` syntax as groups, but terms match **group names** (not item names). Saved in the same config file as groups.

- `supergroups` — list saved supergroups and on/off state
- `supergroup add smelting include:glass,terracotta,nether`
- `supergroup edit wood include:oak,birch exclude:dark`
- `supergroup add blues include:light blue`
- `supergroup on Stone` / `supergroup off Stone`
- `supergroup rename Stone Rock`
- `supergroup remove Stone`
- `supergroup order Stone 0`

### Fulfillment

Items are numbered in display order. Mark an item fulfilled (strikes through in the UI and appends `a` to the line in the file):

- `fulfill 3` or `a 3`
- `fulfill 14-24` or `a 14-24` — mark items 14 through 24 inclusive
- `unfulfill 3` or `u 3`
- `unfulfill 14-24` or `u 14-24` — clear fulfillment for a range

### Navigation

- `up` / `down` — scroll the material list, help, or overview screen
- Arrow keys while typing — move cursor, browse command history (up/down), toggle insert/overwrite (Insert), delete forward (Delete)
- Resizing the terminal redraws the screen automatically; anything you were typing is kept

### Display

- `overview` — show every saved supergroup and group (on/off state, include/exclude terms, membership) without any items; `up`/`down` scroll, any other command returns to the list
- `hidefulfilled on` — hide groups/supergroups when all their items are fulfilled (default)
- `hidefulfilled off` — keep showing fully fulfilled groups

### Other

- `reload` — re-read the current file, or load a newer `material_list_*.txt` in the same folder if Litematica created one
- `latest` — load the newest `material_list_*.txt` in the same folder
- `open <path>` — open a material list file or directory
- `help` — show all available commands
- `quit` or `q` — exit

## Example

```bash
stackSquing examples/sample_material_list.txt
```
