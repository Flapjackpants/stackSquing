package com.flapjackpants.stacksquing.client;

import com.flapjackpants.stacksquing.config.BookLayout;

public final class BookText {
    public static final int LINES_PER_PAGE = 14, TEXT_WIDTH = 114, IMAGE_SIZE = 192, DOUBLE_MIN_SCREEN_WIDTH = 300;
    public static final int BOOK_LEFT_MARGIN = 20;
    public static final int BOOK_VISIBLE_WIDTH = 146;
    public static final int CONTAINER_GAP = 4;
    public static final int MIN_TOP = 2;
    public static final int RECIPE_BOOK_WIDTH = 147, RECIPE_BOOK_OFFSET = 86, RECIPE_NARROW_WIDTH = 379;

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

    public static int containerPanelWidth() {
        return CONTAINER_GAP + BOOK_VISIBLE_WIDTH;
    }

    public static boolean containerBookFits(int screenWidth, int imageWidth) {
        return imageWidth + containerPanelWidth() <= screenWidth;
    }

    public static int containerLeftPos(int screenWidth, int imageWidth) {
        return (screenWidth - (imageWidth + containerPanelWidth())) / 2;
    }

    public static int containerBookX(int leftPos, int imageWidth) {
        return leftPos + imageWidth + CONTAINER_GAP - BOOK_LEFT_MARGIN;
    }

    public static int containerBookTop(int screenHeight) {
        return Math.max(MIN_TOP, (screenHeight - IMAGE_SIZE) / 2);
    }

    /** Vanilla RecipeBookComponent.updateScreenPosition with the book open on a wide screen. */
    public static int recipeOpenLeftPos(int screenWidth, int imageWidth) {
        return 177 + (screenWidth - imageWidth - 200) / 2;
    }

    public static int recipeBookLeft(int screenWidth) {
        return (screenWidth - RECIPE_BOOK_WIDTH) / 2 - RECIPE_BOOK_OFFSET;
    }

    /** Recipe book left edge to side book right edge. */
    public static int recipeGroupWidth(int screenWidth, int imageWidth) {
        return recipeOpenLeftPos(screenWidth, imageWidth) + imageWidth + containerPanelWidth()
                - recipeBookLeft(screenWidth);
    }

    public static boolean recipeAndBookFit(int screenWidth, int imageWidth) {
        return screenWidth >= RECIPE_NARROW_WIDTH && recipeGroupWidth(screenWidth, imageWidth) <= screenWidth;
    }

    /** How far the recipe book and container move left so all three are centred; 0 when they don't fit. */
    public static int recipeShift(int screenWidth, int imageWidth) {
        return recipeAndBookFit(screenWidth, imageWidth)
                ? recipeBookLeft(screenWidth) - (screenWidth - recipeGroupWidth(screenWidth, imageWidth)) / 2 : 0;
    }

    public static String itemPrefix(int displayNumber) {
        return displayNumber + ") ";
    }
}
