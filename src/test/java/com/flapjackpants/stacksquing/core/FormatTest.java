package com.flapjackpants.stacksquing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class FormatTest {
    private static void assertParses(int expected, String text, int stackSize) {
        assertEquals(OptionalInt.of(expected), Format.parseAmount(text, stackSize), text);
    }

    @Test
    void cssFormatting() {
        assertEquals("5c+5s+10", Format.formatCss(Format.toCss(8970, 64)));
        assertEquals("0", Format.formatCss(new Format.Css(0, 0, 0)));
        assertEquals("1c+3", Format.formatCss(Format.toCss(30, 1)));
        assertEquals("1s", Format.formatCss(Format.toCss(16, 16)));
    }

    @Test
    void formatQuantityUsesItemStackSize() {
        assertEquals("8970", Format.formatQuantity(8970, QuantityFormat.RAW, "Stone"));
        assertEquals("1c", Format.formatQuantity(432, QuantityFormat.CSS, "Oak Sign"));
    }

    @Test
    void quantityForPartial() {
        MaterialItem item = new MaterialItem();
        item.total = 985;
        item.missing = 985;
        item.available = 100;
        item.partial = 450;
        assertEquals(535, Format.quantityFor(item, QuantityColumn.MISSING));
        assertEquals(435, Format.quantityFor(item, QuantityColumn.NEED));
        assertEquals(985, Format.quantityFor(item, QuantityColumn.TOTAL));
        assertEquals(100, Format.quantityFor(item, QuantityColumn.AVAILABLE));

        item.partial = 0;
        assertEquals(985, Format.quantityFor(item, QuantityColumn.MISSING));

        item.partial = 985;
        item.available = 100;
        assertEquals(0, Format.quantityFor(item, QuantityColumn.MISSING));
        assertEquals(0, Format.quantityFor(item, QuantityColumn.NEED));
    }

    @Test
    void parseAmountValid() {
        assertParses(450, "450", 64);
        assertParses(128, "2s", 64);
        assertParses(131, "2s+3", 64);
        assertParses(131, "2s 3", 64);
        assertParses(1856, " 1c + 2s ", 64);
        assertParses(432, "1C", 16);
        assertParses(27, "1c", 1);
        assertParses(2, "2s", 1);
        assertParses(3, "3", 0);
    }

    @Test
    void parseAmountInvalid() {
        for (String s : new String[] {"", "abc", "2x", "s", "0", "-5", "2s+", "1234567890", "+2", "2++3",
                "99999999c"}) {
            assertTrue(Format.parseAmount(s, 64).isEmpty(), s);
        }
    }
}
