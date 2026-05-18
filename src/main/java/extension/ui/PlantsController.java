package extension.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextArea;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.geometry.Point2D;
import javafx.scene.layout.Region;

import lombok.extern.slf4j.Slf4j;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import extension.util.PlantSettings;
import gearth.misc.Cacher;

import org.slf4j.LoggerFactory;

@Slf4j
public class PlantsController {

    private final ComboBox<String> logLevelCombo;
    private final CheckBox chkRequestPetInfo;
    private final Button btnPetInfoHelp;
    private Tooltip ttPetInfoHelp;

    private final PlantsView view;

    public PlantsController(
        TextArea commandsArea,
        TextArea logArea,
        ComboBox<String> logLevelCombo,
        CheckBox chkRequestPetInfo,
        Button btnPetInfoHelp
    ) {
        this.logLevelCombo = logLevelCombo;
        this.chkRequestPetInfo = chkRequestPetInfo;
        this.btnPetInfoHelp = btnPetInfoHelp;
        this.view = new PlantsView(commandsArea, logArea);
    }

    public void initialize() {
        view.initialize();
        log.debug("[Plants] UI initialized");
        initLogLevelControl();
        initSettingsControl();
        initHelpIcon();
    }

    private void initSettingsControl() {
        try {
            if (chkRequestPetInfo == null) return;
            // initialize from current setting
            chkRequestPetInfo.setSelected(PlantSettings.isRequestPetInfoBeforeTreat());
            chkRequestPetInfo.setOnAction(e -> PlantSettings.setRequestPetInfoBeforeTreat(chkRequestPetInfo.isSelected()));
        } catch (Exception e) {
            log.error("Failed to initialize settings control", e);
        }
    }

    public void helpPetInfoClick() {
        // Click action is intentionally left empty as tooltip is now hover-based
    }

    private void initHelpIcon() {
        try {
            if (btnPetInfoHelp == null) return;
            // Use simple text icon instead of SVG
            btnPetInfoHelp.setGraphic(null);
            btnPetInfoHelp.setText("(?)");
            // Ensure bold blue styling and transparent background, remove borders and focus rings
            try {
                btnPetInfoHelp.setStyle("-fx-font-weight: bold; -fx-text-fill: #2b7df6; -fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0; -fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
            } catch (Exception ignored) {}

            // Size the button to its content (text) exactly
            try {
                btnPetInfoHelp.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
                btnPetInfoHelp.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
                btnPetInfoHelp.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
            } catch (Exception ignored) {}

            // Prepare a programmatic tooltip and attach it to the button (hover shows it)
            try {
                String tip = "When enabled, the extension will request plant (pet) info before treating it.\n" +
                        "This is done to avoid sending treats that shouldn't be sent (e.g. if well being is already very high).";

                ttPetInfoHelp = new Tooltip(tip);

                btnPetInfoHelp.setOnMouseEntered(e -> {
                    if (ttPetInfoHelp != null && !ttPetInfoHelp.isShowing()) {
                        Point2D p = btnPetInfoHelp.localToScreen(0, btnPetInfoHelp.getHeight());
                        double offsetX = 8.0;
                        double offsetY = 6.0;
                        ttPetInfoHelp.show(btnPetInfoHelp, p.getX() + offsetX, p.getY() + offsetY);
                    }
                });
                
                btnPetInfoHelp.setOnMouseExited(e -> {
                    if (ttPetInfoHelp != null) {
                        ttPetInfoHelp.hide();
                    }
                });
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.debug("Failed to initialize help button", e);
        }
    }

    private static final String LOG_LEVEL_CACHE_KEY = "plants.logLevel";
    private static final String DEFAULT_LOG_LEVEL = "INFO";
    private static final ObservableList<String> VALID_LOG_LEVELS =
            FXCollections.observableArrayList("ERROR", "WARN", "INFO", "DEBUG", "TRACE", "OFF");

    private void initLogLevelControl() {
        try {
            if (logLevelCombo == null) return;
            logLevelCombo.setItems(VALID_LOG_LEVELS);

            // Load persisted level from cache.json, fallback to INFO if missing or invalid
            String cached = loadCachedLogLevel();
            logLevelCombo.getSelectionModel().select(cached);

            // Use default cell rendering (no additional highlight)

            logLevelCombo.setOnAction(e -> {
                String lvl = logLevelCombo.getValue();
                if (lvl != null) setLogLevel(lvl);
            });

            // apply initial level
            setLogLevel(logLevelCombo.getValue());
        } catch (Exception e) {
            log.error("Failed to initialize log level control", e);
        }
    }

    private String loadCachedLogLevel() {
        try {
            Object cached = Cacher.get(LOG_LEVEL_CACHE_KEY);
            if (cached instanceof String) {
                String value = ((String) cached).toUpperCase();
                if (VALID_LOG_LEVELS.contains(value)) {
                    return value;
                }
            }
        } catch (Exception e) {
            log.debug("[Plants] Could not read log level from cache, using default", e);
        }
        return DEFAULT_LOG_LEVEL;
    }

    private void setLogLevel(String levelName) {
        try {
            Level level = Level.toLevel(levelName, Level.INFO);
            Logger logger = (Logger) LoggerFactory.getLogger("extension");
            logger.setLevel(level);
            Cacher.put(LOG_LEVEL_CACHE_KEY, level.toString());
            log.info("[Plants] Set log level to {}", level);
        } catch (Exception e) {
            log.error("Failed to set log level", e);
        }
    }

    public void clearLogs() {
        view.clearLogs();
    }
}
