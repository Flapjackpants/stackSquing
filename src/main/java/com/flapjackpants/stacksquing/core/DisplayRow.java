package com.flapjackpants.stacksquing.core;

public record DisplayRow(DisplayRowKind kind, int displayNumber, String label, int quantity,
                         int itemIndex, boolean fulfilled, ItemGroup group, Supergroup supergroup) {
}
