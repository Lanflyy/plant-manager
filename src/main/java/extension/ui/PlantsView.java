package extension.ui;

import extension.logging.UiLogPrinter;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class PlantsView {

    private final TextArea logArea;
    private volatile boolean initialized;

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        UiLogPrinter.attach(logArea);
    }

    public void clearLogs() {
        runOnUiThread(() -> logArea.clear());
    }

    private void runOnUiThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
