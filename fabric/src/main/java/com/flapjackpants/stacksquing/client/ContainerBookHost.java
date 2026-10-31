package com.flapjackpants.stacksquing.client;

/** Implemented by the mixin on every AbstractContainerScreen. */
public interface ContainerBookHost {
    int stacksquing$leftPos();

    int stacksquing$imageWidth();

    /** Calls Screen.rebuildWidgets(). */
    void stacksquing$rebuild();
}
