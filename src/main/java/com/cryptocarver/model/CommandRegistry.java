package com.cryptocarver.model;

import com.cryptocarver.ui.ModernMainController;
import java.util.List;

/** Compatibility facade for callers that build commands from the shell controller. */
public final class CommandRegistry {
    private CommandRegistry() {
    }

    public static List<CommandItem> buildCommands(ModernMainController controller) {
        if (controller == null) {
            return List.of();
        }
        return PaletteCommandCatalog.build(new PaletteCommandCatalog.Actions() {
            @Override
            public void showQuickStart() { controller.showQuickStart(); }

            @Override
            public void navigateToModule(String moduleName) { controller.navigateToModule(moduleName); }

            @Override
            public void openBatchEncryptRecord() { controller.openBatchEncryptRecord(); }

            @Override
            public void openBatchDecryptRecord() { controller.openBatchDecryptRecord(); }

            @Override
            public void toggleInspector() { controller.handleToggleInspector(); }

            @Override
            public void toggleSidePanel() { controller.handleToggleSidePanel(); }

            @Override
            public boolean hasCurrentResult() { return controller.hasCurrentResult(); }

            @Override
            public void openExpandedResultViewer() { controller.handleOpenExpandedResultViewer(); }

            @Override
            public void increaseFontSize() { controller.handleIncreaseFontSize(); }

            @Override
            public void decreaseFontSize() { controller.handleDecreaseFontSize(); }

            @Override
            public void copyOutput() { controller.handleCopyOutput(); }

            @Override
            public void addCurrentOutputToShelf() { controller.handleAddCurrentOutputToShelf(); }

            @Override
            public void toggleFavorite() { controller.handleToggleFavorite(); }
        });
    }
}
