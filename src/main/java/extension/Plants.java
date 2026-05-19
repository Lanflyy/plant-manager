package extension;

import extension.features.PlantManagerFeature;
import extension.ui.PlantsController;
import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtensionInfo(
        Title = "Plants",
        Description = "Automate treating and composting monster plants",
        Version = "1.2.0",
        Author = "Lanflyy"
)
public class Plants extends ExtensionForm {

    @FXML
    private TextArea commandsArea;
    @FXML
    private TextArea logArea;
    @FXML
    private ComboBox<String> logLevelCombo;
    @FXML
    private CheckBox chkRequestPetInfo;
    @FXML
    private Button btnPetInfoHelp;

    private PlantsController uiController;
    private PlantManagerFeature plantManagerFeature;

    public void initialize() {
        uiController = new PlantsController(commandsArea, logArea, logLevelCombo, chkRequestPetInfo, btnPetInfoHelp);
        uiController.initialize();
    }

    @FXML
    private void helpPetInfoClick() {
        if (uiController != null) {
            uiController.helpPetInfoClick();
        }
    }

    private boolean isAlreadyConnected = false;

    @Override
    protected void initExtension() {
        // If the PacketInfoManager has data during initExtension, it means the extension
        // was attached while G-Earth was already connected to the game client.
        isAlreadyConnected = getPacketInfoManager() != null && !getPacketInfoManager().getPacketInfoList().isEmpty();
        
        plantManagerFeature = new PlantManagerFeature(this);
        plantManagerFeature.install();
    }

    @FXML
    public void clearLogs() {
        if (uiController != null) {
            uiController.clearLogs();
        }
    }

    @Override
    protected void onStartConnection() {
        log.debug("[Connection] Started");
        
        // Only request info manually if the extension was attached mid-session.
        // Otherwise, the client will send it naturally during the login sequence.
        if (isAlreadyConnected && plantManagerFeature != null) {
            plantManagerFeature.requestInfo();
            isAlreadyConnected = false; // Reset so reconnecting behaves normally
        }
    }

    @Override
    protected void onEndConnection() {
        if (plantManagerFeature != null) {
            plantManagerFeature.reset();
        }
        log.debug("[Connection] Ended");
    }
}
