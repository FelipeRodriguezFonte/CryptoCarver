package com.cryptocarver.model;

import com.cryptocarver.ui.ModernMainController;
import com.cryptocarver.ui.UiNavigationRegistry;
import com.cryptocarver.service.I18nService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds and registers all safe UI commands available in the Command Palette.
 */
public final class CommandRegistry {

    private CommandRegistry() {}

    /**
     * Constructs the list of commands bound to the given ModernMainController instance.
     */
    public static List<CommandItem> buildCommands(ModernMainController controller) {
        List<CommandItem> commands = new ArrayList<>();
        if (controller == null) return commands;
        I18nService i18n = I18nService.getInstance();

        // --- 1. NAVIGATION COMMANDS ---
        commands.add(new CommandItem(
                "nav_quickstart",
                i18n.text("command.quickStart.title"),
                i18n.text("command.category.navigationRoot"),
                i18n.text("command.quickStart.description"),
                Arrays.asList("quickstart", "home", "start", "dashboard", "guided"),
                null,
                () -> true,
                controller::showQuickStart
        ));

        // Keep command-palette navigation in lockstep with the source-of-truth
        // catalog. Only expose entries that the UI can actually reveal.
        for (OperationDescriptor descriptor : OperationRegistry.getInstance().getAll()) {
            if (UiNavigationRegistry.resolve(descriptor.getNavigationPath()).isEmpty()) {
                continue;
            }
            List<String> keywords = new ArrayList<>(descriptor.getAliases());
            keywords.add(descriptor.getCategory());
            keywords.add(descriptor.getNavigationPath());
            // MAC is a workspace containing several recognizable algorithms.
            // Keep each algorithm discoverable as its own palette result while
            // retaining the single canonical navigation destination.
            if ("op_auth_mac".equals(descriptor.getId())) {
                addMacCommands(commands, controller);
                continue;
            }
            commands.add(new CommandItem(
                    "nav_" + descriptor.getId(),
                    descriptor.getTitle(),
                    i18n.text("command.category.navigation", descriptor.getCategory()),
                    descriptor.getSubtitle(),
                    keywords,
                    null,
                    () -> true,
                    () -> controller.navigateToModule(descriptor.getNavigationPath())
            ));
        }

        // --- 2. TOOLS & VIEW COMMANDS ---
        commands.add(new CommandItem(
                "view_inspector",
                i18n.text("command.inspector.title"),
                i18n.text("command.category.toolsView"),
                i18n.text("command.inspector.description"),
                Arrays.asList("inspector", "details", "toggle", "panel"),
                "Ctrl+I",
                () -> true,
                controller::handleToggleInspector
        ));

        commands.add(new CommandItem(
                "view_side_panel",
                i18n.text("command.sidePanel.title"),
                i18n.text("command.category.toolsView"),
                i18n.text("command.sidePanel.description"),
                Arrays.asList("sidebar", "rail", "toggle", "panel"),
                "Ctrl+B",
                () -> true,
                controller::handleToggleSidePanel
        ));

        commands.add(new CommandItem(
                "view_expand_result",
                i18n.text("command.expandResult.title"),
                i18n.text("command.category.toolsView"),
                i18n.text("command.expandResult.description"),
                Arrays.asList("expand", "result", "viewer", "fullscreen"),
                "Ctrl+Shift+E",
                controller::hasCurrentResult,
                controller::handleOpenExpandedResultViewer
        ));

        commands.add(new CommandItem(
                "view_zoom_in",
                i18n.text("command.zoomIn.title"),
                i18n.text("command.category.toolsView"),
                i18n.text("command.zoomIn.description"),
                Arrays.asList("zoom", "font", "larger", "increase"),
                "Ctrl++",
                () -> true,
                controller::handleIncreaseFontSize
        ));

        commands.add(new CommandItem(
                "view_zoom_out",
                i18n.text("command.zoomOut.title"),
                i18n.text("command.category.toolsView"),
                i18n.text("command.zoomOut.description"),
                Arrays.asList("zoom", "font", "smaller", "decrease"),
                "Ctrl+-",
                () -> true,
                controller::handleDecreaseFontSize
        ));

        // --- 3. ACTIONS COMMANDS ---
        commands.add(new CommandItem(
                "action_copy_output",
                i18n.text("command.copyOutput.title"),
                i18n.text("command.category.actions"),
                i18n.text("command.copyOutput.description"),
                Arrays.asList("copy", "output", "clipboard"),
                KeyboardShortcutRegistry.findShortcutByAction("Copy Output")
                        .map(KeyboardShortcutEntry::getKeyCombination)
                        .orElse(null),
                controller::hasCurrentResult,
                controller::handleCopyOutput
        ));

        commands.add(new CommandItem(
                "action_add_shelf",
                i18n.text("command.addShelf.title"),
                i18n.text("command.category.actions"),
                i18n.text("command.addShelf.description"),
                Arrays.asList("shelf", "add", "output", "buffer"),
                null,
                controller::hasCurrentResult,
                controller::handleAddCurrentOutputToShelf
        ));

        commands.add(new CommandItem(
                "action_toggle_favorite",
                i18n.text("command.favorite.title"),
                i18n.text("command.category.actions"),
                i18n.text("command.favorite.description"),
                Arrays.asList("favorite", "star", "bookmark", "toggle", "pin"),
                KeyboardShortcutRegistry.findShortcutByAction("Toggle Favorite")
                        .map(KeyboardShortcutEntry::getKeyCombination)
                        .orElse(null),
                () -> true,
                controller::handleToggleFavorite
        ));

        return commands;
    }

    private static void addMacCommands(List<CommandItem> commands, ModernMainController controller) {
        I18nService i18n = I18nService.getInstance();
        String navigation = i18n.text("command.category.navigation", i18n.text("nav.authentication"));
        commands.add(new CommandItem("nav_auth_hmac", i18n.text("command.hmac.title"), navigation,
                i18n.text("command.hmac.description"),
                Arrays.asList("HMAC", "Message Authentication Codes", "MAC"), null, () -> true,
                () -> controller.navigateToModule("MAC")));
        commands.add(new CommandItem("nav_auth_cmac", i18n.text("command.cmac.title"), navigation,
                i18n.text("command.cmac.description"),
                Arrays.asList("CMAC", "Message Authentication Codes", "MAC"), null, () -> true,
                () -> controller.navigateToModule("MAC")));
        commands.add(new CommandItem("nav_auth_retail_mac", i18n.text("command.retailMac.title"),
                i18n.text("command.category.navigation", i18n.text("nav.payments")),
                i18n.text("command.retailMac.description"),
                Arrays.asList("Retail MAC", "MAC", "EMV", "ISO 9797-1"), null, () -> true,
                () -> controller.navigateToModule("EMV Operations")));
    }
}
