package com.cryptocarver.model;

import com.cryptocarver.service.I18nService;
import com.cryptocarver.ui.UiNavigationRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;

/** Builds the command palette's model without depending on JavaFX controls. */
public final class PaletteCommandCatalog {
    public interface Actions {
        void showQuickStart();
        void navigateToModule(String moduleName);
        void openBatchEncryptRecord();
        void openBatchDecryptRecord();
        void toggleInspector();
        void toggleSidePanel();
        boolean hasCurrentResult();
        void openExpandedResultViewer();
        void increaseFontSize();
        void decreaseFontSize();
        void copyOutput();
        void addCurrentOutputToShelf();
        void toggleFavorite();
    }

    private PaletteCommandCatalog() {
    }

    public static List<CommandItem> build(Actions actions) {
        List<CommandItem> commands = new ArrayList<>();
        if (actions == null) {
            return commands;
        }
        I18nService i18n = I18nService.getInstance();
        commands.add(command("nav_quickstart", i18n.text("command.quickStart.title"),
                i18n.text("command.category.navigationRoot"), i18n.text("command.quickStart.description"),
                List.of("quickstart", "home", "start", "dashboard", "guided"), null, () -> true,
                actions::showQuickStart));
        for (OperationDescriptor descriptor : OperationRegistry.getInstance().getAll()) {
            if (UiNavigationRegistry.resolve(descriptor.getNavigationPath()).isEmpty()) {
                continue;
            }
            if ("op_auth_mac".equals(descriptor.getId())) {
                addMacCommands(commands, i18n, actions);
                continue;
            }
            List<String> keywords = new ArrayList<>(descriptor.getAliases());
            keywords.add(descriptor.getCategory());
            keywords.add(descriptor.getNavigationPath());
            commands.add(command("nav_" + descriptor.getId(), descriptor.getTitle(),
                    i18n.text("command.category.navigation", descriptor.getCategory()), descriptor.getSubtitle(),
                    keywords, null, () -> true, () -> actions.navigateToModule(descriptor.getNavigationPath())));
        }
        commands.add(command("batch_encrypt_record", i18n.text("command.batch.encryptRecord.title"),
                i18n.text("command.category.navigation", i18n.text("command.batch.category")),
                i18n.text("command.batch.encryptRecord.description"),
                List.of("Encrypt Record", "Batch Runner", "record batch", "encrypt many"), null,
                () -> true, actions::openBatchEncryptRecord));
        commands.add(command("batch_decrypt_record", i18n.text("command.batch.decryptRecord.title"),
                i18n.text("command.category.navigation", i18n.text("command.batch.category")),
                i18n.text("command.batch.decryptRecord.description"),
                List.of("Decrypt Record", "Batch Runner", "record batch", "decrypt many"), null,
                () -> true, actions::openBatchDecryptRecord));
        commands.add(command("view_inspector", i18n.text("command.inspector.title"),
                i18n.text("command.category.toolsView"), i18n.text("command.inspector.description"),
                List.of("inspector", "details", "toggle", "panel"), "Ctrl+I", () -> true,
                actions::toggleInspector));
        commands.add(command("view_side_panel", i18n.text("command.sidePanel.title"),
                i18n.text("command.category.toolsView"), i18n.text("command.sidePanel.description"),
                List.of("sidebar", "rail", "toggle", "panel"), "Ctrl+B", () -> true,
                actions::toggleSidePanel));
        commands.add(command("view_expand_result", i18n.text("command.expandResult.title"),
                i18n.text("command.category.toolsView"), i18n.text("command.expandResult.description"),
                List.of("expand", "result", "viewer", "fullscreen"), "Ctrl+Shift+E",
                actions::hasCurrentResult, actions::openExpandedResultViewer));
        commands.add(command("view_zoom_in", i18n.text("command.zoomIn.title"),
                i18n.text("command.category.toolsView"), i18n.text("command.zoomIn.description"),
                List.of("zoom", "font", "larger", "increase"), "Ctrl++", () -> true,
                actions::increaseFontSize));
        commands.add(command("view_zoom_out", i18n.text("command.zoomOut.title"),
                i18n.text("command.category.toolsView"), i18n.text("command.zoomOut.description"),
                List.of("zoom", "font", "smaller", "decrease"), "Ctrl+-", () -> true,
                actions::decreaseFontSize));
        commands.add(command("action_copy_output", i18n.text("command.copyOutput.title"),
                i18n.text("command.category.actions"), i18n.text("command.copyOutput.description"),
                List.of("copy", "output", "clipboard"), shortcut("Copy Output"),
                actions::hasCurrentResult, actions::copyOutput));
        commands.add(command("action_add_shelf", i18n.text("command.addShelf.title"),
                i18n.text("command.category.actions"), i18n.text("command.addShelf.description"),
                List.of("shelf", "add", "output", "buffer"), null, actions::hasCurrentResult,
                actions::addCurrentOutputToShelf));
        commands.add(command("action_toggle_favorite", i18n.text("command.favorite.title"),
                i18n.text("command.category.actions"), i18n.text("command.favorite.description"),
                List.of("favorite", "star", "bookmark", "toggle", "pin"), shortcut("Toggle Favorite"),
                () -> true, actions::toggleFavorite));
        Set<String> favorites = AppSettings.getInstance().getFavorites().stream().collect(Collectors.toSet());
        commands.sort(Comparator.comparingInt(command -> favoriteRank(command, favorites)));
        return commands;
    }

    private static int favoriteRank(CommandItem command, Set<String> favorites) {
        return OperationRegistry.getInstance().getAll().stream()
                .filter(operation -> ("nav_" + operation.getId()).equals(command.getId()))
                .anyMatch(operation -> favorites.contains(operation.getNavigationPath())) ? 0 : 1;
    }

    private static CommandItem command(String id, String title, String category, String description,
            List<String> keywords, String shortcut, java.util.function.BooleanSupplier enabled, Runnable action) {
        return new CommandItem(id, title, category, description, keywords, shortcut, enabled, action);
    }

    private static String shortcut(String action) {
        return KeyboardShortcutRegistry.findShortcutByAction(action)
                .map(KeyboardShortcutEntry::getKeyCombination).orElse(null);
    }

    private static void addMacCommands(List<CommandItem> commands, I18nService i18n, Actions actions) {
        String navigation = i18n.text("command.category.navigation", i18n.text("nav.authentication"));
        commands.add(command("nav_auth_hmac", i18n.text("command.hmac.title"), navigation,
                i18n.text("command.hmac.description"), Arrays.asList("HMAC", "Message Authentication Codes", "MAC"),
                null, () -> true, () -> actions.navigateToModule("MAC")));
        commands.add(command("nav_auth_cmac", i18n.text("command.cmac.title"), navigation,
                i18n.text("command.cmac.description"), Arrays.asList("CMAC", "Message Authentication Codes", "MAC"),
                null, () -> true, () -> actions.navigateToModule("MAC")));
        commands.add(command("nav_auth_retail_mac", i18n.text("command.retailMac.title"),
                i18n.text("command.category.navigation", i18n.text("nav.payments")),
                i18n.text("command.retailMac.description"), Arrays.asList("Retail MAC", "MAC", "EMV", "ISO 9797-1"),
                null, () -> true, () -> actions.navigateToModule("EMV Operations")));
    }
}
