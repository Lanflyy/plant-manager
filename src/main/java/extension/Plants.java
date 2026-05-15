package extension;

import extension.features.PlantManagerFeature;
import extension.ui.PlantsView;
import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import lombok.extern.slf4j.Slf4j;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@ExtensionInfo(
        Title = "Plants",
        Description = "Automate treating and composting monster plants",
        Version = "1.1.0",
        Author = "Lanflyy"
)
public class Plants extends ExtensionForm {

    @FXML
    private TextArea commandsArea;
    @FXML
    private TextArea logArea;
    @FXML
    private ComboBox<String> logLevelCombo;

    private PlantsView view;
    private PlantManagerFeature plantManagerFeature;

    public void initialize() {
        getView();
        log.debug("[Plants] UI initialized");
        initLogLevelControl();
    }

    private void initLogLevelControl() {
        try {
            if (logLevelCombo == null) return;
            ObservableList<String> items = FXCollections.observableArrayList("ERROR", "WARN", "INFO", "DEBUG", "TRACE", "OFF");
            logLevelCombo.setItems(items);
            // default to INFO
            logLevelCombo.getSelectionModel().select("INFO");

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

    private void setLogLevel(String levelName) {
        try {
            Level level = Level.toLevel(levelName, Level.INFO);
            Logger logger = (Logger) LoggerFactory.getLogger("extension");
            logger.setLevel(level);
            log.info("[Plants] Set log level to {}", level);
        } catch (Exception e) {
            log.error("Failed to set log level", e);
        }
    }

    @Override
    protected void initExtension() {
        getView();
        plantManagerFeature = new PlantManagerFeature(this);
        plantManagerFeature.install();
    }

    @FXML
    public void clearLogs() {
        getView().clearLogs();
    }

    @Override
    protected void onStartConnection() {
        log.debug("[Connection] Started");
    }

    @Override
    protected void onEndConnection() {
        if (plantManagerFeature != null) {
            plantManagerFeature.reset();
        }
        log.debug("[Connection] Ended");
    }

    private PlantsView getView() {
        if (view == null) {
            view = new PlantsView(commandsArea, logArea);
            view.initialize();
        }
        return view;
    }
}
