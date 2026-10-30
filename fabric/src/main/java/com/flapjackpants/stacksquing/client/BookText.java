package com.flapjackpants.stacksquing.client;

import com.flapjackpants.stacksquing.config.BookLayout;

public final class BookText {
    public static final int LINES_PER_PAGE = 14, TEXT_WIDTH = 114, IMAGE_SIZE = 192, DOUBLE_MIN_SCREEN_WIDTH = 300;

    public record Slot(int page, int line) {
    }

    private BookText() {
    }

    public static int visibleListRows(BookLayout layout) {
        return layout == BookLayout.DOUBLE ? 2 * LINES_PER_PAGE - 2 : LINES_PER_PAGE - 2;
    }

    public static BookLayout effectiveLayout(BookLayout configured, int screenWidth) {
        return configured == BookLayout.DOUBLE && screenWidth < DOUBLE_MIN_SCREEN_WIDTH
                ? BookLayout.SINGLE : configured;
    }

    public static Slot slot(int visibleIndex, BookLayout layout) {
        if (layout == BookLayout.DOUBLE && visibleIndex >= LINES_PER_PAGE) {
            return new Slot(1, visibleIndex - LINES_PER_PAGE);
        }
        return new Slot(0, visibleIndex);
    }

    public static int marqueeOffset(int overflowPx, long millis) {
        if (overflowPx <= 0) {
            return 0;
        }
        long holdMs = 1500;
        long travelMs = overflowPx * 1000L / 20;
        long half = holdMs + travelMs;
        long phase = Math.floorMod(millis, 2 * half);
        boolean forward = phase < half;
        long t = Math.max(0, Math.min(travelMs, (forward ? phase : phase - half) - holdMs));
        long travelled = travelMs == 0 ? 0 : overflowPx * t / travelMs;
        if (forward) {
            return (int) travelled;
        }
        return (int) (overflowPx - travelled);
    }

    public static String leaderDots(int gapPx, int dotWidthPx) {
        if (gapPx <= 0 || dotWidthPx <= 0) {
            return "";
        }
        return ".".repeat(gapPx / dotWidthPx);
    }

    public static String itemPrefix(int displayNumber) {
        return displayNumber + ") ";
    }
}
