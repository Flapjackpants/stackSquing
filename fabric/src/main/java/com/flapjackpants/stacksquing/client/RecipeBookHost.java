package com.flapjackpants.stacksquing.client;

/** Implemented by the mixin on every AbstractRecipeBookScreen. */
public interface RecipeBookHost {
    boolean stacksquing$recipeBookVisible();

    /** Closes the recipe book like its button would (persisted); does not rebuild. */
    void stacksquing$closeRecipeBook();
}
