package com.flapjackpants.stacksquing.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MaterialList {
    public String placementName = "";
    public Path filePath;
    public final List<String> rawLines = new ArrayList<>();
    public final List<MaterialItem> items = new ArrayList<>();
}
