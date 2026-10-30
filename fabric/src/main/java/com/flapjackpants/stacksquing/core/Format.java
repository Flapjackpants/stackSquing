package com.flapjackpants.stacksquing.core;

import java.util.OptionalInt;

/** Port of src/format.cpp. */
public final class Format {
    public static final int DEFAULT_ITEMS_PER_STACK = 64, SLOTS_PER_CHEST = 27;

    public record Css(int chests, int stacks, int items) {
    }

    private Format() {
    }

    public static Css toCss(int n, int stackSize) {
        if (n <= 0) {
            return new Css(0, 0, 0);
        }
        if (stackSize <= 0) {
            stackSize = DEFAULT_ITEMS_PER_STACK;
        }
        if (stackSize == 1) {
            return new Css(n / SLOTS_PER_CHEST, 0, n % SLOTS_PER_CHEST);
        }
        int itemsPerChest = stackSize * SLOTS_PER_CHEST;
        int chests = n / itemsPerChest;
        n %= itemsPerChest;
        return new Css(chests, n / stackSize, n % stackSize);
    }

    public static String formatCss(Css q) {
        StringBuilder sb = new StringBuilder();
        if (q.chests() != 0) {
            sb.append(q.chests()).append('c');
        }
        if (q.stacks() != 0) {
            if (sb.length() > 0) {
                sb.append('+');
            }
            sb.append(q.stacks()).append('s');
        }
        if (q.items() != 0) {
            if (sb.length() > 0) {
                sb.append('+');
            }
            sb.append(q.items());
        }
        return sb.length() == 0 ? "0" : sb.toString();
    }

    public static String formatQuantity(int value, QuantityFormat format, String itemName) {
        if (format == QuantityFormat.RAW) {
            return Integer.toString(value);
        }
        return formatCss(toCss(value, StackSize.maxStackSize(itemName == null ? "" : itemName)));
    }

    public static int quantityFor(MaterialItem item, QuantityColumn column) {
        return switch (column) {
            case TOTAL -> item.total;
            case MISSING -> Math.max(0, item.missing - item.partial);
            case AVAILABLE -> item.available;
            case NEED -> Math.max(0, item.missing - item.partial - item.available);
        };
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static OptionalInt parseAmount(String text, int stackSize) {
        if (stackSize <= 0) {
            stackSize = DEFAULT_ITEMS_PER_STACK;
        }
        long total = 0;
        int i = 0;
        boolean any = false;
        boolean plusPending = false;
        int n = text.length();
        while (i < n) {
            char ch = text.charAt(i);
            if (ch == '+') {
                if (!any || plusPending) {
                    return OptionalInt.empty();
                }
                plusPending = true;
                i++;
                continue;
            }
            if (ch == ' ' || (ch >= '\t' && ch <= '\r')) {
                i++;
                continue;
            }
            if (!isDigit(ch)) {
                return OptionalInt.empty();
            }
            long value = 0;
            int digits = 0;
            while (i < n && isDigit(text.charAt(i))) {
                if (++digits > 9) {
                    return OptionalInt.empty();
                }
                value = value * 10 + (text.charAt(i) - '0');
                i++;
            }
            if (i < n) {
                char suffix = Character.toLowerCase(text.charAt(i));
                if (suffix == 's') {
                    value *= stackSize;
                    i++;
                } else if (suffix == 'c') {
                    value *= (long) SLOTS_PER_CHEST * stackSize;
                    i++;
                }
            }
            total += value;
            any = true;
            plusPending = false;
            if (total > 2_000_000_000L) {
                return OptionalInt.empty();
            }
        }
        if (!any || plusPending || total <= 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) total);
    }
}
