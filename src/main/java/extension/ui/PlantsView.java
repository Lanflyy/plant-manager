package extension.ui;

import extension.logging.UiLogPrinter;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public final class PlantsView {

    private static final String DEFAULT_LANG = "en";
    private static final String COMMANDS_RESOURCE_FORMAT = "/i18n/commands_%s.txt";
    private String commandsText;
    private String language = DEFAULT_LANG;

    private final VBox commandsBox;
    private final TextArea logArea;
    private volatile boolean initialized;

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        UiLogPrinter.attach(logArea);
        loadCommandsText();
    }

    public void setLanguage(String lang) {
        this.language = lang;
        loadCommandsText();
    }

    private void loadCommandsText() {
        String resourcePath = String.format(COMMANDS_RESOURCE_FORMAT, language);
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                try (InputStream fallback = getClass().getResourceAsStream(String.format(COMMANDS_RESOURCE_FORMAT, DEFAULT_LANG))) {
                    if (fallback == null) {
                        commandsText = "[Failed to load commands]";
                        return;
                    }
                    commandsText = new BufferedReader(new InputStreamReader(fallback, StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));
                }
                return;
            }
            commandsText = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            commandsText = "[Failed to load commands]";
        }
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
