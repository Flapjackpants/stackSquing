# stackSquing

Interactive terminal tool for Litematica ASCII material lists. View totals in raw or chest/stack/item format, organize items into filter-based groups, and mark items fulfilled (saved back to the file with an `a` suffix).

## Requirements

- C++17 compiler (Clang or GCC)
- CMake 3.16+
- ncurses (macOS: pre-installed via Xcode CLI tools)

## Build

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

Ensure `~/.local/bin` (or your install prefix) is on your `PATH`, then run:

```bash
stackSquing /path/to/material_list.txt
```

## Usage

Type `help` inside the app for a full scrollable list of commands.

### Quantity column

- `total` — show Total column
- `missing` — show Missing column (default)
- `available` — show Available column

### Quantity format

- `raw` — plain item counts
- `css` — chests/stacks/items (`5c+5s+10` for 8970 items; 64 per stack, 27 stacks per chest)

### Groups

Groups are saved globally at `~/.config/stackSquing/groups.json`. Multiple `include` terms match if **any** term appears in the item name (e.g. `include:iron,anvil` matches both Iron Chain and Anvil). `exclude` terms still disqualify a match.

- `groups` — list saved groups and on/off state
- `group add Deepslate include:deepslate`
- `group edit Deepslate include:brick exclude:cracked`
- `group add Oak include:oak exclude:dark`
- `group on Deepslate` / `group off Deepslate`
- `group rename Deepslate DeepslateBlocks`
- `group remove Deepslate`
- `group order Deepslate 0`

When groups are enabled, matching items are shown in consecutive blocks with a highlighted summary row at the top of each group. Items can appear in multiple groups; fulfilling any listing marks all copies as fulfilled and removes their quantity from group totals. Fully fulfilled groups are hidden by default (`hidefulfilled on`). Groups that belong to an enabled supergroup are only shown inside that supergroup, not on their own.

### Supergroups

Supergroups combine multiple item groups under one summary total (each item counted once, fulfilled items excluded). Saved in the same config file as groups.

- `supergroups` — list saved supergroups and on/off state
- `supergroup add Stone groups:Deepslate,Tuff`
- `supergroup edit Stone groups:Cobblestone`
- `supergroup on Stone` / `supergroup off Stone`
- `supergroup rename Stone Rock`
- `supergroup remove Stone`
- `supergroup order Stone 0`

### Fulfillment

Items are numbered in display order. Mark an item fulfilled (strikes through in the UI and appends `a` to the line in the file):

- `fulfill 3` or `a 3`
- `unfulfill 3` or `u 3`

### Navigation

- `up` / `down` — scroll the material list or help screen
- Arrow keys while typing — move cursor, browse command history (up/down), toggle insert/overwrite (Insert), delete forward (Delete)

### Display

- `hidefulfilled on` — hide groups/supergroups when all their items are fulfilled (default)
- `hidefulfilled off` — keep showing fully fulfilled groups

### Other

- `reload` — re-read the material list from disk
- `help` — show all available commands
- `quit` or `q` — exit

## Example

```bash
stackSquing examples/sample_material_list.txt
```
