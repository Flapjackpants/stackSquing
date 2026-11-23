package com.flapjackpants.stacksquing.client;

/** Implemented by the mixin on RecipeBookComponent. */
public interface RecipeBookShift {
    /** Extra pixels added to xOffset the next time the recipe book lays itself out. */
    void stacksquing$setShift(int px);
}
