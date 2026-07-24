package extension;

import extension.entity.AutoBreedTrustedUser;
import extension.features.PlantManagerFeature;
import extension.ui.PlantsController;
import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtensionInfo(
        Title = "Plants",
        Description = "Automate treating and composting monster plants",
        Version = "1.3.3",
        Author = "Lanflyy"
)
public class Plants extends ExtensionForm {

    @FXML
    private VBox commandsBox;
    @FXML
    private TextArea logArea;
    @FXML
    private ComboBox<String> logLevelCombo;
    @FXML
    private CheckBox chkRequestPetInfo;
    @FXML
    private Button btnPetInfoHelp;
    @FXML
    private CheckBox chkAutoBreed;
    @FXML
    private ComboBox<String> cmbAutoBreedAcceptMode;
    @FXML
    private CheckBox chkAutoBreedRequireSameOrHigherRarity;
    @FXML
    private TextField txtAutoBreedUsername;
    @FXML
    private Button btnAutoBreedAdd;
    @FXML
    private Button btnAutoBreedRemove;
    @FXML
    private TableView<AutoBreedTrustedUser> tblAutoBreedUsers;
    @FXML
    private Label connectedLbl;
    @FXML
    private Label roomLbl;

    private PlantsController uiController;
    private PlantManagerFeature plantManagerFeature;

    public void initialize() {
        uiController = new PlantsController(commandsBox, logArea, logLevelCombo, chkRequestPetInfo, btnPetInfoHelp, chkAutoBreed, cmbAutoBreedAcceptMode, chkAutoBreedRequireSameOrHigherRarity, txtAutoBreedUsername, btnAutoBreedAdd, btnAutoBreedRemove, tblAutoBreedUsers, connectedLbl, roomLbl);
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
        isAlreadyConnected = getPacketInfoManager() != null && !getPacketInfoManager().getPacketInfoList().isEmpty();
        
        plantManagerFeature = new PlantManagerFeature(this);
        plantManagerFeature.install();
        plantManagerFeature.setOnStateChanged(this::updateStateLabels);
    }

    private void updateStateLabels() {
        if (uiController == null) return;
        boolean inRoom = plantManagerFeature.isInitialized();
        uiController.updateRoomState(inRoom);
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
        uiController.updateConnectionState(true);
        
        if (isAlreadyConnected && plantManagerFeature != null) {
            plantManagerFeature.requestInfo();
            isAlreadyConnected = false;
        }
    }

    @Override
    protected void onEndConnection() {
        uiController.updateConnectionState(false);
        uiController.updateRoomState(false);
        if (plantManagerFeature != null) {
            plantManagerFeature.reset();
        }
        log.debug("[Connection] Ended");
    }
}
