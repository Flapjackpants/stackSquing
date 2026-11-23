package com.flapjackpants.stacksquing.core;

public final class MaterialItem {
    public String name = "";
    public int total, missing, available;
    /** Raw items already gathered; 0 whenever fulfilled. */
    public int partial;
    public boolean fulfilled;
    public int lineIndex = -1;
    public String originalLine = "";
}
