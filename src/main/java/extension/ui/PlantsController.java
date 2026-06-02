package extension.ui;

import extension.entity.AutoBreedTrustedUser;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import lombok.extern.slf4j.Slf4j;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import extension.util.PlantSettings;
import gearth.app.misc.Cacher;

import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class PlantsController {

    private final ComboBox<String> logLevelCombo;
    private final CheckBox chkRequestPetInfo;
    private final Button btnPetInfoHelp;
    private final CheckBox chkAutoBreed;
    private final CheckBox chkAutoBreedRequireSameOrHigherRarity;
    private final TextField txtAutoBreedUsername;
    private final Button btnAutoBreedAdd;
    private final Button btnAutoBreedRemove;
    private final TableView<AutoBreedTrustedUser> tblAutoBreedUsers;
    private final ObservableList<AutoBreedTrustedUser> autoBreedUsers = FXCollections.observableArrayList();
    private TableColumn<AutoBreedTrustedUser, String> autoBreedUsernameColumn;
    private Tooltip ttPetInfoHelp;

    private final PlantsView view;

    public PlantsController(
        TextArea commandsArea,
        TextArea logArea,
        ComboBox<String> logLevelCombo,
        CheckBox chkRequestPetInfo,
        Button btnPetInfoHelp,
        CheckBox chkAutoBreed,
        CheckBox chkAutoBreedRequireSameOrHigherRarity,
        TextField txtAutoBreedUsername,
        Button btnAutoBreedAdd,
        Button btnAutoBreedRemove,
        TableView<AutoBreedTrustedUser> tblAutoBreedUsers
    ) {
        this.logLevelCombo = logLevelCombo;
        this.chkRequestPetInfo = chkRequestPetInfo;
        this.btnPetInfoHelp = btnPetInfoHelp;
        this.chkAutoBreed = chkAutoBreed;
        this.chkAutoBreedRequireSameOrHigherRarity = chkAutoBreedRequireSameOrHigherRarity;
        this.txtAutoBreedUsername = txtAutoBreedUsername;
        this.btnAutoBreedAdd = btnAutoBreedAdd;
        this.btnAutoBreedRemove = btnAutoBreedRemove;
        this.tblAutoBreedUsers = tblAutoBreedUsers;
        this.view = new PlantsView(commandsArea, logArea);
    }

    public void initialize() {
        view.initialize();
        log.debug("[Plants] UI initialized");
        initLogLevelControl();
        initSettingsControl();
        initLabeledControlSizing();
        initAutoBreedControls();
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

    private void initLabeledControlSizing() {
        prepareLabeledControl(chkRequestPetInfo);
        prepareLabeledControl(chkAutoBreed);
        prepareLabeledControl(chkAutoBreedRequireSameOrHigherRarity);
        prepareLabeledControl(btnAutoBreedAdd);
        prepareLabeledControl(btnAutoBreedRemove);
    }

    private void prepareLabeledControl(Labeled control) {
        if (control == null) return;
        control.setMinHeight(Region.USE_PREF_SIZE);
        control.setMaxHeight(Region.USE_COMPUTED_SIZE);
        control.setStyle(appendStyle(control.getStyle(), "-fx-padding: 2 4 2 4;"));
    }

    private String appendStyle(String existingStyle, String styleToAppend) {
        if (existingStyle == null || existingStyle.trim().isEmpty()) {
            return styleToAppend;
        }
        return existingStyle.endsWith(";") ? existingStyle + " " + styleToAppend : existingStyle + "; " + styleToAppend;
    }
    private void initAutoBreedControls() {
        try {
            initAutoBreedTable();
            if (chkAutoBreed != null) {
                chkAutoBreed.setOnAction(e -> PlantSettings.setAutoBreedEnabled(chkAutoBreed.isSelected()));
            }
            if (chkAutoBreedRequireSameOrHigherRarity != null) {
                chkAutoBreedRequireSameOrHigherRarity.setOnAction(e -> PlantSettings.setAutoBreedRequireSameOrHigherRarity(chkAutoBreedRequireSameOrHigherRarity.isSelected()));
            }
            if (btnAutoBreedAdd != null) {
                btnAutoBreedAdd.setOnAction(e -> addAutoBreedUsername());
            }
            if (txtAutoBreedUsername != null) {
                txtAutoBreedUsername.setOnAction(e -> addAutoBreedUsername());
            }
            if (btnAutoBreedRemove != null) {
                btnAutoBreedRemove.setText("Remove checked");
                btnAutoBreedRemove.setOnAction(e -> removeCheckedAutoBreedUsernames());
            }
            PlantSettings.addAutoBreedListener(this::refreshAutoBreedControls);
            refreshAutoBreedControls();
        } catch (Exception e) {
            log.error("Failed to initialize auto-breed controls", e);
        }
    }

    private void initAutoBreedTable() {
        if (tblAutoBreedUsers == null) return;
        tblAutoBreedUsers.setEditable(true);
        tblAutoBreedUsers.setItems(autoBreedUsers);
        tblAutoBreedUsers.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblAutoBreedUsers.setFixedCellSize(30.0);
        tblAutoBreedUsers.setStyle(appendStyle(tblAutoBreedUsers.getStyle(), "-fx-cell-size: 30px;"));

        TableColumn<AutoBreedTrustedUser, Boolean> checkedColumn = new TableColumn<>("");
        checkedColumn.setMinWidth(42.0);
        checkedColumn.setMaxWidth(42.0);
        checkedColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        checkedColumn.setCellFactory(CheckBoxTableCell.forTableColumn(checkedColumn));
        checkedColumn.setEditable(true);
        checkedColumn.setStyle("-fx-alignment: CENTER;");

        autoBreedUsernameColumn = new TableColumn<>("Username");
        autoBreedUsernameColumn.setMinWidth(42.0);
        autoBreedUsernameColumn.setCellValueFactory(cellData -> cellData.getValue().usernameProperty());
        autoBreedUsernameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        autoBreedUsernameColumn.setOnEditCommit(event -> renameAutoBreedUsername(event.getRowValue(), event.getNewValue()));
        autoBreedUsernameColumn.setEditable(true);
        autoBreedUsernameColumn.setStyle("-fx-alignment: CENTER_LEFT;");

        TableColumn<AutoBreedTrustedUser, String> userIdColumn = new TableColumn<>("User ID");
        userIdColumn.setMinWidth(110.0);
        userIdColumn.setCellValueFactory(cellData -> cellData.getValue().userIdProperty());
        userIdColumn.setStyle("-fx-alignment: CENTER_LEFT;");

        TableColumn<AutoBreedTrustedUser, Void> actionColumn = new TableColumn<>("Actions");
        actionColumn.setMinWidth(90.0);
        actionColumn.setMaxWidth(100.0);
        actionColumn.setStyle("-fx-alignment: CENTER;");
        actionColumn.setCellFactory(column -> new TableCell<AutoBreedTrustedUser, Void>() {
            private final Button editButton = createActionButton("✎");
            private final Button deleteButton = createActionButton("❌");
            private final HBox actions = new HBox(6.0, editButton, deleteButton);

            {
                actions.setAlignment(Pos.CENTER);
                deleteButton.setOnAction(e -> {
                    AutoBreedTrustedUser user = getTableView().getItems().get(getIndex());
                    PlantSettings.removeAutoBreedTrustedUsername(user.getUsername());
                });
                editButton.setOnAction(e -> {
                    getTableView().getSelectionModel().select(getIndex());
                    getTableView().edit(getIndex(), autoBreedUsernameColumn);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : actions);
            }
        });

        tblAutoBreedUsers.getColumns().setAll(checkedColumn, autoBreedUsernameColumn, userIdColumn, actionColumn);
    }

    private Button createActionButton(String text) {
        Button button = new Button(text);
        button.setMnemonicParsing(false);
        button.setMinHeight(Region.USE_PREF_SIZE);
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: #2b7df6; -fx-font-weight: bold; -fx-border-color: transparent; -fx-cursor: hand; -fx-padding: 1 4 1 4;");
        return button;
    }

    private void addAutoBreedUsername() {
        if (txtAutoBreedUsername == null) return;
        String username = txtAutoBreedUsername.getText();
        if (PlantSettings.addAutoBreedTrustedUsername(username)) {
            txtAutoBreedUsername.clear();
        }
    }

    private void removeCheckedAutoBreedUsernames() {
        List<String> checkedUsernames = autoBreedUsers.stream()
                .filter(AutoBreedTrustedUser::isSelected)
                .map(AutoBreedTrustedUser::getUsername)
                .collect(Collectors.toList());
        PlantSettings.removeAutoBreedTrustedUsernames(checkedUsernames);
    }

    private void renameAutoBreedUsername(AutoBreedTrustedUser user, String newUsername) {
        if (user == null) return;
        String oldUsername = user.getUsername();
        if (!PlantSettings.renameAutoBreedTrustedUsername(oldUsername, newUsername)) {
            user.setUsername(oldUsername);
            refreshAutoBreedControls();
        }
    }

    private void refreshAutoBreedControls() {
        Runnable refresh = () -> {
            if (chkAutoBreed != null) {
                chkAutoBreed.setSelected(PlantSettings.isAutoBreedEnabled());
            }
            if (chkAutoBreedRequireSameOrHigherRarity != null) {
                chkAutoBreedRequireSameOrHigherRarity.setSelected(PlantSettings.isAutoBreedRequireSameOrHigherRarity());
            }
            autoBreedUsers.setAll(PlantSettings.getAutoBreedTrustedUsers());
        };
        if (Platform.isFxApplicationThread()) {
            refresh.run();
        } else {
            Platform.runLater(refresh);
        }
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

    public void setLanguage(String lang) {
        view.setLanguage(lang);
    }
}








