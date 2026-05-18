package extension.features;

import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HEntity;
import gearth.extensions.parsers.HFloorItem;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import extension.state.RoomEntityState;
import extension.state.RoomObjectState;
import extension.state.SessionState;
import extension.util.NotificationUtils;

@Slf4j
@RequiredArgsConstructor
public final class PlantManagerFeature {

    public static final String TREAT_COMMAND = ":plants";
    public static final String COMPOST_COMMAND = ":plants compost";
    public static final String SEED_COMMAND = ":plants seed";
    public static final String ABORT_COMMAND = ":plants abort";
    public static final int PROCESS_DELAY_MS = 600;

    @Getter
    private final ExtensionForm extension;
    
    @Getter
    private RoomEntityState roomEntityState;
    @Getter
    private RoomObjectState roomObjectState;
    @Getter
    private SessionState sessionState;

    private BulkItemProcessor processor;

    public void install() {
        // initialize states
        this.roomEntityState = new RoomEntityState(extension);
        this.roomObjectState = new RoomObjectState(extension);
        this.sessionState = new SessionState(extension);

        // initialize processor and handlers
        this.processor = new BulkItemProcessor(this, extension);

        extension.intercept(HMessage.Direction.TOCLIENT, "PetInfo", TreatPlantsAction::handlePetInfo);
        extension.intercept(HMessage.Direction.TOSERVER, "Chat", this::handleChat);
        extension.intercept(HMessage.Direction.TOSERVER, "GetGuestRoom", this::handleGetGuestRoom);
        extension.intercept(HMessage.Direction.TOSERVER, "Quit", this::handleQuit);
        
        log.info("[Plants] Extension installed");
    }

    public void requestInfo() {
        boolean sent = extension.sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
        log.debug("[InfoRetrieve] Info requested {}", sent ? "sent" : "failed");
    }

    public void reset() {
        if (roomEntityState != null) roomEntityState.clearEntities();
        if (roomObjectState != null) roomObjectState.clearFloorItems();
        abortProcessingSafe();
        if (roomEntityState != null) roomEntityState.setInitialized(false);
        if (sessionState != null) sessionState.setCurrentRoomId(-1);
        log.info("[Plants] Extension reset, clearing everything..");
    }

    // Delegated Plant store operations
    public HEntity removePlantById(int id) {
        return roomEntityState != null ? roomEntityState.removeEntityById(id) : null;
    }

    public List<HEntity> getPlantsSnapshot() {
        return roomEntityState != null ? roomEntityState.getPlantsSnapshot() : Collections.emptyList();
    }

    public Set<Integer> getPlantIds() {
        return roomEntityState != null ? roomEntityState.getPlantIds() : Collections.emptySet();
    }

    public int getPlantCount() {
        return roomEntityState != null ? roomEntityState.getPlantCount() : 0;
    }

    public List<HFloorItem> getFloorItemsSnapshot() {
        return roomObjectState != null ? roomObjectState.getFloorItemsSnapshot() : Collections.emptyList();
    }

    public boolean isInitialized() {
        return roomEntityState != null && roomEntityState.isInitialized();
    }
    
    public int getCurrentUserId() {
        return sessionState != null ? sessionState.getCurrentUserId() : -1;
    }
    
    public String getCurrentUsername() {
        return sessionState != null ? sessionState.getCurrentUsername() : null;
    }
    
    public int getCurrentRoomId() {
        return sessionState != null ? sessionState.getCurrentRoomId() : -1;
    }

    public void abortProcessingSafe() {
        if (processor != null) {
            processor.abortProcessing();
        }
    }

    public void handleChat(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        String text = packet.readString();

        if (!isCommand(text)) {
            return;
        }

        hMessage.setBlocked(true);
        log.debug("[Chat] Command received: {}", text);

        if (!isInitialized()) {
            NotificationUtils.showSystemNotificationToUser(getExtension(), "Error: Room not initialized yet! Please wait for the room to load or reload it.");
            log.debug("[Chat] Command rejected because the room is not initialized");
            return;
        }

        UserActionExecutor action = null;
        if (PlantManagerFeature.TREAT_COMMAND.equals(text)) {
            action = new TreatPlantsAction(this, processor);
        } else if (PlantManagerFeature.COMPOST_COMMAND.equals(text)) {
            action = new CompostPlantsAction(this, processor);
        } else if (PlantManagerFeature.SEED_COMMAND.equals(text)) {
            action = new SeedPlantsAction(this, processor);
        } else if (PlantManagerFeature.ABORT_COMMAND.equals(text)) {
            action = new AbortPlantsAction(this, processor);
        } else {
            log.error("[Chat] Unrecognized command: {}", text);
        }

        if (action != null) {
            action.execute();
        }
    }

    private boolean isCommand(String text) {
        return TREAT_COMMAND.equals(text) || COMPOST_COMMAND.equals(text) || SEED_COMMAND.equals(text) || ABORT_COMMAND.equals(text);
    }

    public void handleGetGuestRoom(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        int roomId = packet.readInteger();
        int requestType = packet.readInteger();

        if (requestType == 0) {
            log.trace("[GetGuestRoom] Room {} requested. Aborting processing.", roomId);
            abortProcessingSafe();
        }
    }

    public void handleQuit(HMessage hMessage) {
        log.trace("[Quit] Room quit. Aborting processing.");
        abortProcessingSafe();
    }
}
