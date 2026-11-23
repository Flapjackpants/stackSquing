package com.flapjackpants.stacksquing.core;

import java.util.ArrayList;
import java.util.List;

public final class Supergroup {
    public String name = "";
    public final List<String> include = new ArrayList<>();
    public final List<String> exclude = new ArrayList<>();
    public boolean enabled;
    public int order;

    public Supergroup() {
    }

    public Supergroup(String name, List<String> include, List<String> exclude, boolean enabled, int order) {
        this.name = name;
        this.include.addAll(include);
        this.exclude.addAll(exclude);
        this.enabled = enabled;
        this.order = order;
    }
}
