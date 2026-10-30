package com.flapjackpants.stacksquing.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flapjackpants.stacksquing.client.BookText.Slot;
import com.flapjackpants.stacksquing.config.BookLayout;
import org.junit.jupiter.api.Test;

class BookTextTest {
    @Test
    void visibleRows() {
        assertEquals(12, BookText.visibleListRows(BookLayout.SINGLE));
        assertEquals(26, BookText.visibleListRows(BookLayout.DOUBLE));
    }

    @Test
    void effectiveLayout() {
        assertEquals(BookLayout.SINGLE, BookText.effectiveLayout(BookLayout.DOUBLE, 299));
        assertEquals(BookLayout.DOUBLE, BookText.effectiveLayout(BookLayout.DOUBLE, 300));
        assertEquals(BookLayout.SINGLE, BookText.effectiveLayout(BookLayout.SINGLE, 1000));
    }

    @Test
    void slots() {
        assertEquals(new Slot(0, 13), BookText.slot(13, BookLayout.DOUBLE));
        assertEquals(new Slot(1, 0), BookText.slot(14, BookLayout.DOUBLE));
        assertEquals(new Slot(1, 11), BookText.slot(25, BookLayout.DOUBLE));
        assertEquals(new Slot(0, 5), BookText.slot(5, BookLayout.SINGLE));
    }

    @Test
    void marquee() {
        assertEquals(0, BookText.marqueeOffset(40, 0));
        assertEquals(0, BookText.marqueeOffset(40, 1500));
        assertEquals(20, BookText.marqueeOffset(40, 2500));
        assertEquals(40, BookText.marqueeOffset(40, 3500));
        assertEquals(40, BookText.marqueeOffset(40, 5000));
        assertEquals(20, BookText.marqueeOffset(40, 6000));
        assertEquals(0, BookText.marqueeOffset(40, 7000));
        for (long t = 0; t <= 20000; t += 37) {
            int o = BookText.marqueeOffset(40, t);
            assertTrue(o >= 0 && o <= 40, "t=" + t + " o=" + o);
            assertEquals(0, BookText.marqueeOffset(0, t));
            assertEquals(0, BookText.marqueeOffset(-5, t));
        }
    }

    @Test
    void dots() {
        assertEquals(".....", BookText.leaderDots(10, 2));
        assertEquals("", BookText.leaderDots(1, 2));
        assertEquals("", BookText.leaderDots(-4, 2));
        assertEquals("", BookText.leaderDots(10, 0));
    }

    @Test
    void prefix() {
        assertEquals("7) ", BookText.itemPrefix(7));
        assertEquals("12) ", BookText.itemPrefix(12));
    }
}
