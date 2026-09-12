package extension;

import extension.entity.AutoBreedTrustedUser;
import extension.features.PlantManagerFeature;
import extension.state.FurniDataStateHandler;
import extension.ui.PlantsController;
import furnidata.FurniDataState;
import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import javafx.application.Platform;
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
        Version = "1.5.0",
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
    @FXML
    private Label furniDataStateLbl;

    private PlantsController uiController;
    private PlantManagerFeature plantManagerFeature;
    private FurniDataStateHandler furniDataState;
    private String currentHost;

    public void initialize() {
        uiController = new PlantsController(commandsBox, logArea, logLevelCombo, chkRequestPetInfo, btnPetInfoHelp, chkAutoBreed, cmbAutoBreedAcceptMode, chkAutoBreedRequireSameOrHigherRarity, txtAutoBreedUsername, btnAutoBreedAdd, btnAutoBreedRemove, tblAutoBreedUsers, connectedLbl, roomLbl);
        uiController.initialize();
    }

    private boolean isAlreadyConnected = false;

    @Override
    public void initExtension() {
        isAlreadyConnected = getPacketInfoManager() != null && !getPacketInfoManager().getPacketInfoList().isEmpty();

        furniDataState = new FurniDataStateHandler(this::updateFurniDataStateLabel);

        plantManagerFeature = new PlantManagerFeature(this);
        plantManagerFeature.setFurniDataStateHandler(furniDataState);
        plantManagerFeature.install();
        plantManagerFeature.setOnStateChanged(this::updateStateLabels);

        onConnect((host, port, hotelversion, clientIdentifier, clientType) -> {
            currentHost = host;
            furniDataState.load(host);
        });
    }

    private void updateStateLabels() {
        if (uiController == null) return;
        boolean inRoom = plantManagerFeature.isInitialized();
        uiController.updateRoomState(inRoom);
    }

    private void updateFurniDataStateLabel(FurniDataState state) {
        if (furniDataStateLbl == null) return;
        Platform.runLater(() -> {
            switch (state) {
                case LOADED:
                    furniDataStateLbl.setText("Furni Data Loaded");
                    furniDataStateLbl.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
                    break;
                case CACHED:
                    furniDataStateLbl.setText("Furni Data Cache Hit");
                    furniDataStateLbl.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
                    break;
                case CACHED_WITH_REFRESH_ERROR:
                    furniDataStateLbl.setText("Furni Data Cache Hit (Refresh error)");
                    furniDataStateLbl.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
                    break;
                case LOADING:
                    furniDataStateLbl.setText("Loading Furni Data...");
                    furniDataStateLbl.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
                    break;
                case FAILED:
                    furniDataStateLbl.setText("Furni Data Failed");
                    furniDataStateLbl.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
                    break;
                case UNAVAILABLE:
                default:
                    furniDataStateLbl.setText("Furni Data Not Loaded");
                    furniDataStateLbl.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
                    break;
            }
        });
    }

    @FXML
    public void onRequestFurniData() {
        if (currentHost == null || currentHost.length() < 7) {
            log.warn("[FurniData] Cannot reload furni data: not connected to server");
            return;
        }
        furniDataState.reload(currentHost);
    }

    public FurniDataStateHandler getFurniDataState() {
        return furniDataState;
    }

    public String getCurrentHost() {
        return currentHost;
    }

    @FXML
    public void clearLogs() {
        if (uiController != null) {
            uiController.clearLogs();
        }
    }

    @Override
    public void onStartConnection() {
        log.debug("[Connection] Started");
        uiController.updateConnectionState(true);

        if (isAlreadyConnected && plantManagerFeature != null) {
            plantManagerFeature.requestInfo();
            isAlreadyConnected = false;
        }
    }

    @Override
    public void onEndConnection() {
        uiController.updateConnectionState(false);
        uiController.updateRoomState(false);
        if (plantManagerFeature != null) {
            plantManagerFeature.reset();
        }
        if (furniDataState != null) {
            furniDataState.clear();
        }
        currentHost = null;
        log.debug("[Connection] Ended");
    }
}
