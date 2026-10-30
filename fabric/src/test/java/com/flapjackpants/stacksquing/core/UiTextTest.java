package com.flapjackpants.stacksquing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.flapjackpants.stacksquing.core.UiText.PanelLineKind;
import com.flapjackpants.stacksquing.core.UiText.PanelLineParts;
import com.flapjackpants.stacksquing.core.UiText.StatusKind;
import org.junit.jupiter.api.Test;

class UiTextTest {
    @Test
    void classifyStatusErrors() {
        for (String m : new String[] {"Usage: open <x>", "Group not found: x", "Group already exists: x",
                "Unknown command. Type help.", "Group add requires include:term[,term]",
                "Failed to save group.", "Invalid item number: 3", "Invalid file number: 9. Type files.",
                "No material_list_*.txt found in /x", "No items in range 1-2"}) {
            assertEquals(StatusKind.ERROR, UiText.classifyStatus(m), m);
        }
    }

    @Test
    void classifyStatusWarning() {
        assertEquals(StatusKind.WARNING, UiText.classifyStatus("File changed on disk. Type reload."));
        assertEquals(StatusKind.WARNING, UiText.classifyStatus("Warning: something"));
    }

    @Test
    void classifyStatusNormal() {
        assertEquals(StatusKind.NORMAL, UiText.classifyStatus("Added group: Oak"));
        assertEquals(StatusKind.NORMAL, UiText.classifyStatus(""));
    }

    @Test
    void splitPanelBlank() {
        assertEquals(PanelLineKind.BLANK, UiText.splitPanelLine("").kind());
        PanelLineParts p = UiText.splitPanelLine("   ");
        assertEquals(PanelLineKind.BLANK, p.kind());
        assertEquals("", p.indent());
        assertEquals("", p.key());
        assertEquals("", p.rest());
    }

    @Test
    void splitPanelHeading() {
        PanelLineParts p = UiText.splitPanelLine("Groups:");
        assertEquals(PanelLineKind.HEADING, p.kind());
        assertEquals("Groups:", p.key());
    }

    @Test
    void splitPanelEntry() {
        PanelLineParts p = UiText.splitPanelLine("  help    Show help");
        assertEquals(PanelLineKind.ENTRY, p.kind());
        assertEquals("  ", p.indent());
        assertEquals("help", p.key());
        assertEquals("    Show help", p.rest());
        p = UiText.splitPanelLine("  Oak [on]");
        assertEquals(PanelLineKind.ENTRY, p.kind());
        assertEquals("Oak [on]", p.key());
        assertEquals("", p.rest());
    }

    @Test
    void splitPanelDetail() {
        PanelLineParts p = UiText.splitPanelLine("      include: oak, birch");
        assertEquals(PanelLineKind.DETAIL, p.kind());
        assertEquals(" ".repeat(6), p.indent());
        assertEquals("include:", p.key());
        assertEquals(" oak, birch", p.rest());
        p = UiText.splitPanelLine(" ".repeat(24) + "(terms may contain spaces)");
        assertEquals(PanelLineKind.DETAIL, p.kind());
        assertEquals("", p.key());
        assertEquals("(terms may contain spaces)", p.rest());
    }

    @Test
    void scrollIndicator() {
        assertEquals("rows 1-12 of 80", UiText.scrollIndicator(1, 12, 80));
        assertEquals("rows 5-5 of 5", UiText.scrollIndicator(5, 5, 5));
        assertEquals("", UiText.scrollIndicator(0, 0, 0));
    }

    @Test
    void clampScroll() {
        assertEquals(5, UiText.clampScroll(5, 64, 10));
        assertEquals(54, UiText.clampScroll(100, 64, 10));
        assertEquals(0, UiText.clampScroll(-3, 64, 10));
        assertEquals(0, UiText.clampScroll(4, 8, 10));
        assertEquals(0, UiText.clampScroll(2, 0, 10));
        assertEquals(64, UiText.clampScroll(70, 64, 0));
        assertEquals(8, UiText.clampScroll(50, 20, 12));
        assertEquals(0, UiText.clampScroll(-3, 5, 12));
    }

    @Test
    void helpLinesAreWellFormed() {
        var lines = HelpText.lines();
        assertFalse(lines.isEmpty());
        for (String line : lines) {
            PanelLineParts p = UiText.splitPanelLine(line);
            switch (p.kind()) {
                case BLANK, HEADING, DETAIL -> {
                }
                case ENTRY -> assertFalse(p.rest().isBlank(), "entry without description: '" + line + "'");
            }
        }
    }

    @Test
    void helpLinesContent() {
        var lines = HelpText.lines();
        assertEquals("Quantity column:", lines.get(0));
        assertEquals("  total  Show Total column", lines.get(1));
        assertFalse(lines.stream().anyMatch(l -> l.contains("Window resize")));
        assertEquals(true, lines.contains("  files  List material lists in the list folder, newest first"));
        assertEquals(true, lines.contains("  quit, q, exit  Close the book"));
    }
}
