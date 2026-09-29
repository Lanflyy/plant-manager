package extension.ui;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import extension.entity.AutoBreedTrustedUser;
import extension.i18n.MessageService;
import extension.util.PlantSettings;
import extension.util.PreferenceCacher;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlantsController {

    private static final String LOG_LEVEL_CACHE_KEY = "plants.logLevel";
    private static final String DEFAULT_LOG_LEVEL = "INFO";
    private static final URL COPY_ICON_RESOURCE = requireIconResource("/icons/copy_icon.svg");
    private static final String COPY_ICON_PATH_DATA = loadSvgPathData(COPY_ICON_RESOURCE);
    private static final ObservableList<String> VALID_LOG_LEVELS = FXCollections.observableArrayList("ERROR", "WARN",
            "INFO", "DEBUG", "TRACE", "OFF");
    private final ComboBox<String> logLevelCombo;
    private final CheckBox chkRequestPetInfo;
    private final Button btnPetInfoHelp;
    private final CheckBox chkAutoBreed;
    private final ComboBox<String> cmbAutoBreedAcceptMode;
    private final CheckBox chkAutoBreedRequireSameOrHigherRarity;
    private final TextField txtAutoBreedUsername;
    private final Button btnAutoBreedAdd;
    private final Button btnAutoBreedRemove;
    private final TableView<AutoBreedTrustedUser> tblAutoBreedUsers;
    private final ObservableList<AutoBreedTrustedUser> autoBreedUsers = FXCollections.observableArrayList();
    private TableColumn<AutoBreedTrustedUser, String> autoBreedUsernameColumn;
    private Tooltip ttPetInfoHelp;

    private final VBox commandsBox;
    private Label connectedLbl;
    private Label roomLbl;
    private PauseTransition activeCopyResetPause;
    private Runnable activeCopyReset;

    private final PlantsView view;

    private Tab tabHome;
    private Tab tabLogs;
    private Tab tabSettings;
    private Label lblState;
    private Label lblLogLevel;
    private Label lblAdvancedOptions;
    private Label lblAutoAcceptBreeding;
    private Label lblTrustedUsers;
    private Label lblAvailableCommands;

    public PlantsController(
            VBox commandsBox,
            TextArea logArea,
            ComboBox<String> logLevelCombo,
            CheckBox chkRequestPetInfo,
            Button btnPetInfoHelp,
            CheckBox chkAutoBreed,
            ComboBox<String> cmbAutoBreedAcceptMode,
            CheckBox chkAutoBreedRequireSameOrHigherRarity,
            TextField txtAutoBreedUsername,
            Button btnAutoBreedAdd,
            Button btnAutoBreedRemove,
            TableView<AutoBreedTrustedUser> tblAutoBreedUsers,
            Label connectedLbl,
            Label roomLbl,
            Tab tabHome,
            Tab tabLogs,
            Tab tabSettings,
            Label lblState,
            Label lblLogLevel,
            Label lblAdvancedOptions,
            Label lblAutoAcceptBreeding,
            Label lblTrustedUsers,
            Label lblAvailableCommands
    ) {
        this.logLevelCombo = logLevelCombo;
        this.chkRequestPetInfo = chkRequestPetInfo;
        this.btnPetInfoHelp = btnPetInfoHelp;
        this.chkAutoBreed = chkAutoBreed;
        this.cmbAutoBreedAcceptMode = cmbAutoBreedAcceptMode;
        this.chkAutoBreedRequireSameOrHigherRarity = chkAutoBreedRequireSameOrHigherRarity;
        this.txtAutoBreedUsername = txtAutoBreedUsername;
        this.btnAutoBreedAdd = btnAutoBreedAdd;
        this.btnAutoBreedRemove = btnAutoBreedRemove;
        this.tblAutoBreedUsers = tblAutoBreedUsers;
        this.commandsBox = commandsBox;
        this.connectedLbl = connectedLbl;
        this.roomLbl = roomLbl;
        this.tabHome = tabHome;
        this.tabLogs = tabLogs;
        this.tabSettings = tabSettings;
        this.lblState = lblState;
        this.lblLogLevel = lblLogLevel;
        this.lblAdvancedOptions = lblAdvancedOptions;
        this.lblAutoAcceptBreeding = lblAutoAcceptBreeding;
        this.lblTrustedUsers = lblTrustedUsers;
        this.lblAvailableCommands = lblAvailableCommands;
        this.view = new PlantsView(commandsBox, logArea);
    }

    public void initialize() {
        view.initialize();
        log.debug("[Plants] UI initialized");
        refreshAllText();
        initCommandsBox();
        initLogLevelControl();
        initSettingsControl();
        initAutoBreedControls();
        initHelpIcon();
    }

    public void refreshAllText() {
        Platform.runLater(() -> {
            if (tabHome != null) tabHome.setText(MessageService.getValue("tab.home"));
            if (tabLogs != null) tabLogs.setText(MessageService.getValue("tab.logs"));
            if (tabSettings != null) tabSettings.setText(MessageService.getValue("tab.settings"));
            if (lblState != null) lblState.setText(MessageService.getValue("label.state"));
            if (lblAvailableCommands != null) lblAvailableCommands.setText(MessageService.getValue("label.available.commands"));
            if (lblLogLevel != null) lblLogLevel.setText(MessageService.getValue("label.log.level"));
            if (lblAdvancedOptions != null) lblAdvancedOptions.setText(MessageService.getValue("label.advanced.options"));
            if (lblAutoAcceptBreeding != null) lblAutoAcceptBreeding.setText(MessageService.getValue("label.auto.accept.breeding"));
            if (lblTrustedUsers != null) lblTrustedUsers.setText(MessageService.getValue("label.trusted.users"));
            if (chkRequestPetInfo != null) chkRequestPetInfo.setText(MessageService.getValue("chk.request.pet.info"));
            if (chkAutoBreed != null) chkAutoBreed.setText(MessageService.getValue("chk.enable.auto.breed"));
            if (chkAutoBreedRequireSameOrHigherRarity != null) chkAutoBreedRequireSameOrHigherRarity.setText(MessageService.getValue("chk.auto.breed.rarity"));
            if (txtAutoBreedUsername != null) txtAutoBreedUsername.setPromptText(MessageService.getValue("txt.username.prompt"));
            if (btnAutoBreedAdd != null) btnAutoBreedAdd.setText(MessageService.getValue("btn.add"));
            if (btnAutoBreedRemove != null) btnAutoBreedRemove.setText(MessageService.getValue("btn.remove.checked"));
            if (cmbAutoBreedAcceptMode != null) {
                String currentValue = cmbAutoBreedAcceptMode.getValue();
                cmbAutoBreedAcceptMode.setItems(FXCollections.observableArrayList(
                        MessageService.getValue("auto.breed.mode.trusted"),
                        MessageService.getValue("auto.breed.mode.any")));
                if (currentValue != null) {
                    boolean wasAnyUser = "Any user".equals(currentValue) || MessageService.getValue("auto.breed.mode.any").equals(currentValue);
                    cmbAutoBreedAcceptMode.getSelectionModel().select(wasAnyUser ? MessageService.getValue("auto.breed.mode.any") : MessageService.getValue("auto.breed.mode.trusted"));
                }
            }
            refreshCommandsBox();
            if (ttPetInfoHelp != null) {
                ttPetInfoHelp.setText(MessageService.getValue("tooltip.pet.info.help"));
            }
        });
    }

    private void refreshCommandsBox() {
        if (commandsBox == null) return;
        commandsBox.getChildren().clear();
        String[][] commands = {
            {":plants", MessageService.getValue("cmd.treat.description")},
            {":plants compost", MessageService.getValue("cmd.compost.description")},
            {":plants seed", MessageService.getValue("cmd.seed.description")},
            {":plants canreproduce on", MessageService.getValue("cmd.canreproduce.on.description")},
            {":plants canreproduce off", MessageService.getValue("cmd.canreproduce.off.description")},
            {":plants countcanbreed", MessageService.getValue("cmd.countcanbreed.description")},
            {":plants autobreed on", MessageService.getValue("cmd.autobreed.on.description")},
            {":plants autobreed off", MessageService.getValue("cmd.autobreed.off.description")},
            {":plants autobreed adduser NAME", MessageService.getValue("cmd.autobreed.adduser.description")},
            {":plants autobreed adduser", MessageService.getValue("cmd.autobreed.adduser.click.description")},
            {":plants autobreed removeuser NAME", MessageService.getValue("cmd.autobreed.removeuser.description")},
            {":plants autobreed removeuser", MessageService.getValue("cmd.autobreed.removeuser.click.description")},
            {":plants abort", MessageService.getValue("cmd.abort.description")}
        };
        for (int i = 0; i < commands.length; i++) {
            commandsBox.getChildren().add(createCommandRow(commands[i][0], commands[i][1], i));
        }
    }

    private void initCommandsBox() {
        if (commandsBox == null) return;
        Platform.runLater(this::refreshCommandsBox);
    }

    private HBox createCommandRow(String command, String description, int index) {
        HBox row = new HBox(4.0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(index % 2 == 0
                ? "-fx-padding: 1 6;"
                : "-fx-background-color: rgba(127,127,127,0.15); -fx-padding: 1 6;");

        javafx.scene.control.TextField commandField = new javafx.scene.control.TextField(command);
        commandField.setEditable(false);
        commandField.setPrefColumnCount(command.length() + 1);
        commandField.setMaxHeight(Double.MAX_VALUE);
        commandField.setStyle("-fx-font-size: 12px; -fx-font-family: monospace; -fx-background-color: transparent; -fx-border-color: transparent; -fx-background-insets: 0; -fx-padding: 0 0 0 6;");

        String baseButtonStyle = "-fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0; -fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-cursor: hand;";

        Button copyButton = new Button();
        javafx.scene.shape.SVGPath copyIcon = createCopyIcon();
        copyIcon.setFill(javafx.scene.paint.Color.web("#2b7df6"));
        copyIcon.setScaleX(0.6);
        copyIcon.setScaleY(0.6);
        copyButton.setGraphic(copyIcon);
        copyButton.setStyle(baseButtonStyle);
        copyButton.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        copyButton.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        copyButton.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        Button helpButton = new Button("(?)");
        helpButton.setStyle("-fx-text-fill: #2b7df6; -fx-font-weight: bold; " + baseButtonStyle);
        helpButton.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        helpButton.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        helpButton.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        Label copiedLabel = new Label(MessageService.getValue("tooltip.copied"));
        copiedLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 11px; -fx-font-weight: bold;");
        copiedLabel.setVisible(false);

        Runnable updateHighlight = () -> {
            boolean highlight = row.isHover() || commandField.isFocused();
            if (highlight) {
                commandField.setStyle(
                        "-fx-font-size: 12px; -fx-font-family: monospace; -fx-background-color: transparent; -fx-border-color: #2b7df6; -fx-border-radius: 3; -fx-background-insets: 0; -fx-padding: 0 0 0 6;");
            } else {
                commandField.setStyle(
                        "-fx-font-size: 12px; -fx-font-family: monospace; -fx-background-color: transparent; -fx-border-color: transparent; -fx-background-insets: 0; -fx-padding: 0 0 0 6;");
            }
        };

        row.hoverProperty().addListener((obs, oldVal, newVal) -> updateHighlight.run());
        commandField.focusedProperty().addListener((obs, oldVal, newVal) -> updateHighlight.run());

        boolean[] clickedOnce = { false };

        PauseTransition copyResetPause = new PauseTransition(javafx.util.Duration.seconds(1));
        Runnable resetCopyFeedback = () -> {
            copyButton.setText("");
            copyButton.setGraphic(copyIcon);
            copyButton.setStyle(baseButtonStyle);
            copiedLabel.setVisible(false);
            clickedOnce[0] = false;
            if (activeCopyResetPause == copyResetPause) {
                activeCopyResetPause = null;
                activeCopyReset = null;
            }
        };
        copyResetPause.setOnFinished(ev -> resetCopyFeedback.run());

        Runnable doCopy = () -> {
            if (activeCopyResetPause != null) {
                activeCopyResetPause.stop();
            }
            if (activeCopyReset != null) {
                activeCopyReset.run();
            }
            copyResetPause.stop();
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(command);
            clipboard.setContent(content);

            copyButton.setGraphic(null);
            copyButton.setText("\u2713");
            copyButton.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold; " + baseButtonStyle);
            copiedLabel.setVisible(true);
            activeCopyResetPause = copyResetPause;
            activeCopyReset = resetCopyFeedback;
            copyResetPause.playFromStart();
        };

        copyButton.setOnAction(e -> doCopy.run());

        Tooltip copyTooltip = new Tooltip(MessageService.getValue("tooltip.copy.command"));
        copyButton.setOnMouseEntered(e -> {
            if (!copyTooltip.isShowing()) {
                Point2D p = copyButton.localToScreen(0, copyButton.getHeight());
                copyTooltip.show(copyButton, p.getX() + 8.0, p.getY() + 6.0);
            }
        });
        copyButton.setOnMouseExited(e -> copyTooltip.hide());

        Tooltip tooltip = new Tooltip(description);
        helpButton.setOnMouseEntered(e -> {
            if (!tooltip.isShowing()) {
                Point2D p = helpButton.localToScreen(0, helpButton.getHeight());
                tooltip.show(helpButton, p.getX() + 8.0, p.getY() + 6.0);
            }
        });
        helpButton.setOnMouseExited(e -> tooltip.hide());

        row.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            Node target = (Node) e.getTarget();
            boolean isButton = false;
            while (target != null && target != row) {
                if (target instanceof Button) {
                    isButton = true;
                    break;
                }
                target = target.getParent();
            }
            if (!clickedOnce[0] && !isButton) {
                clickedOnce[0] = true;
                doCopy.run();
            }
        });

        EventHandler<MouseEvent> mouseHandler = e -> {
            if (e.getTarget() == row) {
                commandField.requestFocus();
                if (e.getClickCount() >= 2) {
                    commandField.selectAll();
                } else {
                    Point2D p = commandField.sceneToLocal(e.getSceneX(), e.getSceneY());
                    int charIndex = (int) Math.round((p.getX() - 6) / 7.2);
                    charIndex = Math.max(0, Math.min(command.length(), charIndex));

                    if (e.getEventType() == MouseEvent.MOUSE_PRESSED) {
                        commandField.positionCaret(charIndex);
                        commandField.deselect();
                    } else if (e.getEventType() == MouseEvent.MOUSE_DRAGGED) {
                        commandField.selectPositionCaret(charIndex);
                    }
                }
                e.consume();
            }
        };
        row.setOnMousePressed(mouseHandler);
        row.setOnMouseDragged(mouseHandler);

        row.getChildren().addAll(commandField, copyButton, helpButton, copiedLabel);
        return row;
    }

    public void updateConnectionState(boolean connected) {
        Platform.runLater(() -> {
            if (connectedLbl != null) {
                connectedLbl.setText(connected ? MessageService.getValue("label.connected") : MessageService.getValue("label.not.connected"));
                connectedLbl.setStyle(connected
                        ? "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;"
                        : "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
            }
        });
    }

    private static javafx.scene.shape.SVGPath createCopyIcon() {
        javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
        icon.setContent(COPY_ICON_PATH_DATA);
        return icon;
    }

    private static URL requireIconResource(String resourcePath) {
        URL resource = PlantsController.class.getResource(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Missing icon resource: " + resourcePath);
        }
        return resource;
    }

    private static String loadSvgPathData(URL resource) {
        try (InputStream input = resource.openStream()) {
            NodeList paths = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(input)
                    .getElementsByTagName("path");
            StringBuilder pathData = new StringBuilder();
            for (int i = 0; i < paths.getLength(); i++) {
                String path = ((Element) paths.item(i)).getAttribute("d").trim();
                if (!path.isEmpty()) {
                    if (pathData.length() > 0) {
                        pathData.append(' ');
                    }
                    pathData.append(path);
                }
            }
            if (pathData.length() == 0) {
                throw new IllegalStateException("Icon contains no SVG path: " + resource);
            }
            return pathData.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not load SVG icon: " + resource, e);
        }
    }

    public void updateRoomState(boolean inRoom) {
        Platform.runLater(() -> {
            if (roomLbl != null) {
                roomLbl.setText(inRoom ? MessageService.getValue("label.room.loaded") : MessageService.getValue("label.room.not.loaded"));
                roomLbl.setStyle(inRoom
                        ? "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;"
                        : "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4;");
            }
        });
    }

    private void initSettingsControl() {
        try {
            if (chkRequestPetInfo == null) return;
            chkRequestPetInfo.setSelected(PlantSettings.isRequestPetInfoBeforeTreat());
            chkRequestPetInfo.setOnAction(e -> PlantSettings.setRequestPetInfoBeforeTreat(chkRequestPetInfo.isSelected()));
        } catch (Exception e) {
            log.error("Failed to initialize settings control", e);
        }
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
                chkAutoBreed.setOnAction(e -> {
                    PlantSettings.setAutoBreedEnabled(chkAutoBreed.isSelected());
                    updateAutoBreedAcceptModeEnabled();
                });
            }
            if (cmbAutoBreedAcceptMode != null) {
                cmbAutoBreedAcceptMode.setItems(FXCollections.observableArrayList(
                        MessageService.getValue("auto.breed.mode.trusted"),
                        MessageService.getValue("auto.breed.mode.any")));
                cmbAutoBreedAcceptMode.setOnAction(e -> {
                    String value = cmbAutoBreedAcceptMode.getValue();
                    if (value != null) {
                        PlantSettings.setAutoBreedAcceptAllUsers(MessageService.getValue("auto.breed.mode.any").equals(value));
                    }
                });
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
                btnAutoBreedRemove.setText(MessageService.getValue("btn.remove.checked"));
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
        tblAutoBreedUsers.setItems(autoBreedUsers);

        TableColumn<AutoBreedTrustedUser, Boolean> checkedColumn = new TableColumn<>("");
        checkedColumn.setMinWidth(42.0);
        checkedColumn.setMaxWidth(42.0);
        checkedColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        checkedColumn.setCellFactory(CheckBoxTableCell.forTableColumn(checkedColumn));
        checkedColumn.setEditable(true);
        checkedColumn.setStyle("-fx-alignment: CENTER;");

        autoBreedUsernameColumn = new TableColumn<>(MessageService.getValue("txt.username.prompt"));
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
            private final Button editButton = createActionButton("\u270E");
            private final Button deleteButton = createActionButton("\u274C");
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
            if (cmbAutoBreedAcceptMode != null) {
                boolean acceptAllUsers = PlantSettings.isAutoBreedAcceptAllUsers();
                cmbAutoBreedAcceptMode.getSelectionModel().select(acceptAllUsers ? MessageService.getValue("auto.breed.mode.any") : MessageService.getValue("auto.breed.mode.trusted"));
            }
            if (chkAutoBreedRequireSameOrHigherRarity != null) {
                chkAutoBreedRequireSameOrHigherRarity.setSelected(PlantSettings.isAutoBreedRequireSameOrHigherRarity());
            }
            autoBreedUsers.setAll(PlantSettings.getAutoBreedTrustedUsers());
            updateAutoBreedAcceptModeEnabled();
        };
        if (Platform.isFxApplicationThread()) {
            refresh.run();
        } else {
            Platform.runLater(refresh);
        }
    }

    private void updateAutoBreedAcceptModeEnabled() {
        if (cmbAutoBreedAcceptMode != null && chkAutoBreed != null) {
            cmbAutoBreedAcceptMode.setDisable(!chkAutoBreed.isSelected());
        }
    }

    private void initHelpIcon() {
        try {
            if (btnPetInfoHelp == null) return;
            try {
                ttPetInfoHelp = new Tooltip(MessageService.getValue("tooltip.pet.info.help"));

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

    private void initLogLevelControl() {
        try {
            if (logLevelCombo == null) return;
            logLevelCombo.setItems(VALID_LOG_LEVELS);

            String cached = loadCachedLogLevel();
            logLevelCombo.getSelectionModel().select(cached);

            logLevelCombo.setOnAction(e -> {
                String lvl = logLevelCombo.getValue();
                if (lvl != null) setLogLevel(lvl);
            });

            setLogLevel(logLevelCombo.getValue());
        } catch (Exception e) {
            log.error("Failed to initialize log level control", e);
        }
    }

    private String loadCachedLogLevel() {
        try {
            Object cached = PreferenceCacher.get(LOG_LEVEL_CACHE_KEY);
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
            PreferenceCacher.put(LOG_LEVEL_CACHE_KEY, level.toString());
            log.info("[Plants] Set log level to {}", level);
        } catch (Exception e) {
            log.error("Failed to set log level", e);
        }
    }

    public void clearLogs() {
        view.clearLogs();
    }
}
