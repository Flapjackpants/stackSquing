#include <iostream>
#include <string>
#include <vector>

#include "overview.hpp"
#include "stack_size.hpp"
#include "ui_text.hpp"

static int g_failures = 0;

#define CHECK(cond)                                                              \
    do {                                                                         \
        if (!(cond)) {                                                           \
            std::cerr << __FILE__ << ":" << __LINE__ << ": CHECK failed: " #cond \
                      << std::endl;                                              \
            ++g_failures;                                                        \
        }                                                                        \
    } while (0)

static void print_value(const std::string& s) { std::cerr << "  \"" << s << "\"\n"; }
static void print_value(const std::vector<std::string>& v) {
    for (const auto& s : v) {
        std::cerr << "  \"" << s << "\"\n";
    }
}

#define CHECK_EQ(a, b)                                                             \
    do {                                                                           \
        if (!((a) == (b))) {                                                       \
            std::cerr << __FILE__ << ":" << __LINE__ << ": CHECK failed: " #a      \
                      << " == " #b << "\n";                                        \
            std::cerr << " lhs:\n";                                                \
            print_value(a);                                                        \
            std::cerr << " rhs:\n";                                                \
            print_value(b);                                                        \
            ++g_failures;                                                          \
        }                                                                          \
    } while (0)

using Lines = std::vector<std::string>;

static ItemGroup make_group(const std::string& name, Lines include, Lines exclude = {}, bool enabled = true) {
    ItemGroup g;
    g.name = name;
    g.include = std::move(include);
    g.exclude = std::move(exclude);
    g.enabled = enabled;
    return g;
}

static Supergroup make_super(const std::string& name, Lines include, Lines exclude = {}, bool enabled = true) {
    Supergroup s;
    s.name = name;
    s.include = std::move(include);
    s.exclude = std::move(exclude);
    s.enabled = enabled;
    return s;
}

static bool contains(const Lines& v, const std::string& s) {
    for (const auto& x : v) {
        if (x == s) {
            return true;
        }
    }
    return false;
}

void test_stack_size_beds() {
    CHECK(max_stack_size("Straw Bed") == 16);
    CHECK(max_stack_size("straw bed") == 16);
    CHECK(max_stack_size("Red Bed") == 1);
    CHECK(max_stack_size("Oak Sign") == 16);
}

void test_overview_empty() {
    GroupStore store;
    Lines expected = {"Supergroups (0):", "  No saved supergroups.", "", "Groups (0):", "  No saved groups."};
    CHECK_EQ(build_group_overview(store), expected);
}

void test_overview_group_basic() {
    GroupStore store;
    store.groups().push_back(make_group("Oak", {"oak"}));
    store.groups().push_back(make_group("Empty", {}, {}, false));
    Lines out = build_group_overview(store);
    Lines expected = {"Supergroups (0):", "  No saved supergroups.", "", "Groups (2):",
                      "  Oak [on]", "      include: oak",
                      "  Empty [off]", "      include: (none)"};
    CHECK_EQ(out, expected);
}

void test_overview_group_filters() {
    GroupStore store;
    store.groups().push_back(make_group("Wood", {"oak", "birch"}, {"dark"}));
    Lines out = build_group_overview(store);
    CHECK(contains(out, "      include: oak, birch"));
    CHECK(contains(out, "      exclude: dark"));
}

void test_overview_supergroup_members() {
    GroupStore store;
    store.groups().push_back(make_group("Glass", {"glass"}, {}, true));
    store.groups().push_back(make_group("Glazed", {"glazed"}, {}, false));
    store.supergroups().push_back(make_super("smelting", {"gla"}));
    Lines out = build_group_overview(store);
    CHECK(contains(out, "  smelting [on]"));
    CHECK(contains(out, "      groups: Glass [on], Glazed [off]"));
}

void test_overview_supergroup_no_members() {
    GroupStore store;
    store.groups().push_back(make_group("Oak", {"oak"}));
    store.supergroups().push_back(make_super("metals", {"iron"}));
    Lines out = build_group_overview(store);
    CHECK(contains(out, "      groups: none"));
}

void test_overview_group_in_supergroup() {
    GroupStore store;
    store.groups().push_back(make_group("Glass", {"glass"}));
    store.supergroups().push_back(make_super("smelting", {"glass"}));
    Lines out = build_group_overview(store);
    CHECK(contains(out, "      in: smelting"));
}

void test_matches_specific_include_beats_exclude() {
    GroupStore store;
    ItemGroup g = make_group("Light", {"light", "blue", "light blue"}, {"light", "gray"});
    CHECK(store.matches(g, "Light Blue Wool"));
    CHECK(!store.matches(g, "Light Gray Wool"));
    CHECK(!store.matches(g, "Light Weighted Pressure Plate"));
}

void test_matches_exclude_still_applies() {
    GroupStore store;
    ItemGroup stone = make_group("Stone", {"stone"}, {"blackstone"});
    CHECK(!store.matches(stone, "Blackstone"));
    CHECK(store.matches(stone, "Stone Bricks"));
    ItemGroup same = make_group("Light", {"light"}, {"light"});
    CHECK(!store.matches(same, "Light Blue Wool"));
}

void test_matches_specific_include_case_insensitive() {
    GroupStore store;
    ItemGroup g = make_group("Light", {"Light Blue"}, {"LIGHT"});
    CHECK(store.matches(g, "light blue wool"));
}

void test_matches_supergroup_specific_include() {
    GroupStore store;
    Supergroup s = make_super("blues", {"light blue"}, {"light"});
    CHECK(store.matches(s, "light blue wool"));
}

void test_classify_status_errors() {
    for (const char* m : {"Usage: open <x>", "Group not found: x", "Group already exists: x",
                          "Unknown command. Type help.", "Group add requires include:term[,term]",
                          "Failed to save group.", "Invalid item number: 3"}) {
        CHECK(classify_status(m) == StatusKind::Error);
    }
}

void test_classify_status_warning() {
    CHECK(classify_status("File changed on disk. Type reload.") == StatusKind::Warning);
}

void test_classify_status_normal() {
    CHECK(classify_status("Added group: Oak") == StatusKind::Normal);
    CHECK(classify_status("") == StatusKind::Normal);
}

void test_split_panel_blank() {
    CHECK(split_panel_line("").kind == PanelLineKind::Blank);
    const auto p = split_panel_line("   ");
    CHECK(p.kind == PanelLineKind::Blank);
    CHECK(p.indent.empty() && p.key.empty() && p.rest.empty());
}

void test_split_panel_heading() {
    const auto p = split_panel_line("Groups:");
    CHECK(p.kind == PanelLineKind::Heading);
    CHECK_EQ(p.key, std::string("Groups:"));
}

void test_split_panel_entry() {
    auto p = split_panel_line("  help    Show help");
    CHECK(p.kind == PanelLineKind::Entry);
    CHECK_EQ(p.indent, std::string("  "));
    CHECK_EQ(p.key, std::string("help"));
    CHECK_EQ(p.rest, std::string("    Show help"));
    p = split_panel_line("  Oak [on]");
    CHECK(p.kind == PanelLineKind::Entry);
    CHECK_EQ(p.key, std::string("Oak [on]"));
    CHECK_EQ(p.rest, std::string(""));
}

void test_split_panel_detail() {
    auto p = split_panel_line("      include: oak, birch");
    CHECK(p.kind == PanelLineKind::Detail);
    CHECK_EQ(p.indent, std::string(6, ' '));
    CHECK_EQ(p.key, std::string("include:"));
    CHECK_EQ(p.rest, std::string(" oak, birch"));
    p = split_panel_line(std::string(24, ' ') + "(terms may contain spaces)");
    CHECK(p.kind == PanelLineKind::Detail);
    CHECK_EQ(p.key, std::string(""));
    CHECK_EQ(p.rest, std::string("(terms may contain spaces)"));
}

void test_scroll_indicator() {
    CHECK_EQ(scroll_indicator(1, 20, 54), std::string("rows 1-20 of 54"));
    CHECK_EQ(scroll_indicator(5, 5, 5), std::string("rows 5-5 of 5"));
    CHECK_EQ(scroll_indicator(0, 0, 0), std::string(""));
}

void test_clamp_scroll() {
    CHECK(clamp_scroll(5, 64, 10) == 5);
    CHECK(clamp_scroll(100, 64, 10) == 54);
    CHECK(clamp_scroll(-3, 64, 10) == 0);
    CHECK(clamp_scroll(4, 8, 10) == 0);
    CHECK(clamp_scroll(2, 0, 10) == 0);
    CHECK(clamp_scroll(70, 64, 0) == 64);
}

int main() {
    test_classify_status_errors();
    test_classify_status_warning();
    test_classify_status_normal();
    test_split_panel_blank();
    test_split_panel_heading();
    test_split_panel_entry();
    test_split_panel_detail();
    test_scroll_indicator();
    test_clamp_scroll();
    test_stack_size_beds();
    test_overview_empty();
    test_overview_group_basic();
    test_overview_group_filters();
    test_overview_supergroup_members();
    test_overview_supergroup_no_members();
    test_overview_group_in_supergroup();
    test_matches_specific_include_beats_exclude();
    test_matches_exclude_still_applies();
    test_matches_specific_include_case_insensitive();
    test_matches_supergroup_specific_include();
    if (g_failures > 0) {
        std::cerr << g_failures << " failures" << std::endl;
        return 1;
    }
    std::cout << "all tests passed" << std::endl;
    return 0;
}
