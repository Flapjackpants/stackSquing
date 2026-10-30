package com.flapjackpants.stacksquing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StackSizeTest {
    @Test
    void strawBedStacksTo16() {
        assertEquals(16, StackSize.maxStackSize("Straw Bed"));
        assertEquals(16, StackSize.maxStackSize("straw bed"));
    }

    @Test
    void bedsAreUnstackable() {
        assertEquals(1, StackSize.maxStackSize("Red Bed"));
    }

    @Test
    void signsToolsAndDefault() {
        assertEquals(16, StackSize.maxStackSize("Oak Sign"));
        assertEquals(64, StackSize.maxStackSize("Oak Wall Sign"));
        assertEquals(1, StackSize.maxStackSize("Diamond Pickaxe"));
        assertEquals(64, StackSize.maxStackSize("Stone"));
        assertEquals(64, StackSize.maxStackSize(""));
    }
}
