package extension;

import extension.entity.AutoBreedTrustedUser;
import extension.features.PlantManagerFeature;
import extension.i18n.MessageService;
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
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtensionInfo(
        Title = "Plants",
        Description = "Automate treating and composting monster plants",
        Version = "1.6.0",
        Author = "Lanflyy"
)
public class Plants extends ExtensionForm {

    @FXML
    private VBox commandsList;
    @FXML
    private Button btnReloadFurniData;
    @FXML
    private Button btnClearLogs;
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
    @FXML
    private Tab tabHome;
    @FXML
    private Tab tabLogs;
    @FXML
    private Tab tabSettings;
    @FXML
    private Label lblState;
    @FXML
    private Label lblLogLevel;
    @FXML
    private Label lblAdvancedOptions;
    @FXML
    private Label lblAutoAcceptBreeding;
    @FXML
    private Label lblTrustedUsers;
    @FXML
    private Label lblAvailableCommands;

    private PlantsController uiController;
    private PlantManagerFeature plantManagerFeature;
    private FurniDataStateHandler furniDataState;
    private String currentHost;

    public void initialize() {
        uiController = new PlantsController(logArea, logLevelCombo, chkRequestPetInfo, btnPetInfoHelp, chkAutoBreed, cmbAutoBreedAcceptMode, chkAutoBreedRequireSameOrHigherRarity, txtAutoBreedUsername, btnAutoBreedAdd, btnAutoBreedRemove, tblAutoBreedUsers, connectedLbl, roomLbl, tabHome, tabLogs, tabSettings, lblState, lblLogLevel, lblAdvancedOptions, lblAutoAcceptBreeding, lblTrustedUsers, lblAvailableCommands, commandsList, btnReloadFurniData, btnClearLogs);
        uiController.initialize();
    }

    private boolean isAlreadyConnected = false;

    @Override
    public void initExtension() {
        isAlreadyConnected = getPacketInfoManager() != null && !getPacketInfoManager().getPacketInfoList().isEmpty();

        furniDataState = new FurniDataStateHandler(this::updateFurniDataStateLabel);
        // Apply a language change that may have been received before initialization
        updateFurniDataStateLabel(furniDataState.getState());

        plantManagerFeature = new PlantManagerFeature(this);
        plantManagerFeature.setFurniDataStateHandler(furniDataState);
        plantManagerFeature.install();
        plantManagerFeature.setOnStateChanged(this::updateStateLabels);

        onConnect((host, port, hotelversion, clientIdentifier, clientType) -> {
            currentHost = host;
            furniDataState.load(host);
        });
    }

    @Override
    public void handleLanguageChange(String locale) {
        MessageService.setLanguage(locale);
        MessageService.clearCache();
        if (uiController != null) {
            uiController.refreshAllText();
        }
        // G-Earth sends host info (and thus the language) on init/update before initExtension() runs,
        // in which case the furni data handler does not exist yet.
        if (furniDataState != null) {
            updateFurniDataStateLabel(furniDataState.getState());
        }
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
                    furniDataStateLbl.setText(MessageService.getValue("furni.data.loaded"));
                    furniDataStateLbl.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
                    break;
                case CACHED:
                    furniDataStateLbl.setText(MessageService.getValue("furni.data.cache.hit"));
                    furniDataStateLbl.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
                    break;
                case CACHED_WITH_REFRESH_ERROR:
                    furniDataStateLbl.setText(MessageService.getValue("furni.data.cache.hit.refresh.error"));
                    furniDataStateLbl.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
                    break;
                case LOADING:
                    furniDataStateLbl.setText(MessageService.getValue("furni.data.loading"));
                    furniDataStateLbl.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
                    break;
                case FAILED:
                    furniDataStateLbl.setText(MessageService.getValue("furni.data.failed"));
                    furniDataStateLbl.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
                    break;
                case UNAVAILABLE:
                default:
                    furniDataStateLbl.setText(MessageService.getValue("furni.data.not.loaded"));
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
