package com.cryptocarver.model;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cryptocarver.ui.UiNavigationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaletteCommandCatalogTest {
    @Test
    void catalogIncludesOnlyResolvableNavigationEntriesAndRunsTheProvidedAction() {
        List<String> navigated = new ArrayList<>();
        PaletteCommandCatalog.Actions actions = new StubActions(navigated);
        List<CommandItem> commands = PaletteCommandCatalog.build(actions);
        assertTrue(commands.stream().filter(command -> command.getId().startsWith("nav_op_"))
                .allMatch(command -> OperationRegistry.getInstance().getAll().stream()
                        .filter(operation -> ("nav_" + operation.getId()).equals(command.getId()))
                        .anyMatch(operation -> UiNavigationRegistry.resolve(operation.getNavigationPath()).isPresent())));
        commands.stream().filter(command -> "nav_op_gen_hash".equals(command.getId())).findFirst().orElseThrow().execute();
        assertTrue(navigated.contains("Hashing"));
    }

    @Test
    void catalogPrioritizesFavoriteRoutes() {
        AppSettings settings = AppSettings.getInstance();
        List<String> previousFavorites = settings.getFavorites();
        try {
            for (String favorite : previousFavorites) {
                settings.toggleFavorite(favorite);
            }
            settings.toggleFavorite("Hashing");
            List<CommandItem> commands = PaletteCommandCatalog.build(new StubActions(new ArrayList<>()));
            assertEquals("nav_op_gen_hash", commands.stream().filter(command -> command.getId().startsWith("nav_"))
                    .findFirst().orElseThrow().getId());
        } finally {
            for (String favorite : settings.getFavorites()) {
                settings.toggleFavorite(favorite);
            }
            for (String favorite : previousFavorites) {
                if (!settings.isFavorite(favorite)) {
                    settings.toggleFavorite(favorite);
                }
            }
        }
    }

    private static final class StubActions implements PaletteCommandCatalog.Actions {
        private final List<String> navigated;

        private StubActions(List<String> navigated) {
            this.navigated = navigated;
        }

        @Override
        public void showQuickStart() { }

        @Override
        public void navigateToModule(String moduleName) { navigated.add(moduleName); }

        @Override
        public void toggleInspector() { }

        @Override
        public void toggleSidePanel() { }

        @Override
        public boolean hasCurrentResult() { return true; }

        @Override
        public void openExpandedResultViewer() { }

        @Override
        public void increaseFontSize() { }

        @Override
        public void decreaseFontSize() { }

        @Override
        public void copyOutput() { }

        @Override
        public void addCurrentOutputToShelf() { }

        @Override
        public void toggleFavorite() { }
    }
}
