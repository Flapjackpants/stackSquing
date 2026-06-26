# stackSquing

Interactive terminal tool for Litematica ASCII material lists. View totals in raw or chest/stack/item format, organize items into filter-based groups, and mark items fulfilled (saved back to the file with an `a` suffix).

## Requirements

- C++17 compiler (Clang or GCC)
- CMake 3.16+
- ncurses (macOS: pre-installed via Xcode CLI tools)

## Build

```bash
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
```

## Install

```bash
cmake --install build
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

### Quantity column

- `total` — show Total column
- `missing` — show Missing column (default)
- `available` — show Available column

### Quantity format

- `raw` — plain item counts
- `css` — chests/stacks/items (`5c+5s+10` for 8970 items; 64 per stack, 27 stacks per chest)

### Groups

Groups are saved globally at `~/.config/stackSquing/groups.json`.

- `groups` — list saved groups and on/off state
- `group add Deepslate include:deepslate`
- `group add Oak include:oak exclude:dark`
- `group on Deepslate` / `group off Deepslate`
- `group remove Deepslate`
- `group order Deepslate 0`

When groups are enabled, matching items are shown in consecutive blocks with a highlighted summary row at the top of each group.

### Fulfillment

Items are numbered in display order. Mark an item fulfilled (strikes through in the UI and appends `a` to the line in the file):

- `fulfill 3` or `a 3`
- `unfulfill 3` or `u 3`

### Other

- `reload` — re-read the material list from disk
- `help` — command summary
- `quit` or `q` — exit

## Example

```bash
stackSquing examples/sample_material_list.txt
```
# stackSquing
