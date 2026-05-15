package extension.features;

import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HEntity;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import extension.util.NotificationUtils;
import extension.util.PlantUtils;

@Slf4j
@RequiredArgsConstructor
public final class PlantManagerFeature {

    public static final String TREAT_COMMAND = ":plants";
    public static final String COMPOST_COMMAND = ":plants compost";
    public static final String ABORT_COMMAND = ":plants abort";
    public static final int PROCESS_DELAY_MS = 600;

    @Getter
    private final ExtensionForm extension;
    private final Map<Integer, HEntity> plants = new ConcurrentHashMap<>();
    private PlantProcessor processor;
    @Getter
    @Setter
    private volatile boolean initialized;
    @Getter
    @Setter
    private volatile int currentRoomId = -1;

    public void install() {
        // initialize processor and handlers
        this.processor = new PlantProcessor(this, extension);

        extension.intercept(HMessage.Direction.TOCLIENT, "Users", this::handleUsers);
        extension.intercept(HMessage.Direction.TOCLIENT, "UserRemove", this::handleUserRemove);
        extension.intercept(HMessage.Direction.TOSERVER, "Chat", this::handleChat);
        extension.intercept(HMessage.Direction.TOSERVER, "GetGuestRoom", this::handleGetGuestRoom);
        extension.intercept(HMessage.Direction.TOSERVER, "Quit", this::handleQuit);
        extension.intercept(HMessage.Direction.TOSERVER, "RemovePetFromFlat", this::handleRemovePetFromFlat);
        log.info("[Plants] Extension installed");
    }

    public void reset() {
        plants.clear();
        abortProcessingSafe();
        initialized = false;
        currentRoomId = -1;
        log.info("[Plants] Extension reset, clearing everything..");
    }

    // Plant store operations - main source of truth for plants
    public void addPlant(HEntity plant) {
        if (plant == null) return;
        plants.put(plant.getId(), plant);
    }

    public HEntity removePlantById(int id) {
        return plants.remove(id);
    }

    public List<HEntity> findPlantsByIndex(int index) {
        return plants.values()
                .stream()
                .filter(plant -> plant.getIndex() == index)
                .collect(Collectors.toList());
    }

    public List<HEntity> getPlantsSnapshot() {
        return new ArrayList<>(plants.values());
    }

    public Set<Integer> getPlantIds() {
        return Collections.unmodifiableSet(plants.keySet());
    }

    public int getPlantCount() {
        return plants.size();
    }

    public void clearPlants() {
        plants.clear();
    }

    public void abortProcessingSafe() {
        if(processor != null) {
            processor.abortProcessing();
        }
    }

    public void handleUsers(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            HEntity[] entities = HEntity.parse(packet);
            int addedCount = 0;

            // Only clear plants if this is a full room user list (not a single user/plant update)
            if (entities.length > 1) {
                log.trace("[Users] Detected full room user list ({}) entities). Clearing plants. Plants before clear: {}", entities.length, getPlantIds());
                clearPlants();
            }

            log.trace("[Users] handleUsers called. Current plants before update: {}", getPlantIds());

            for (HEntity entity : entities) {
                if (PlantUtils.isPlant(entity)) {
                    addPlant(entity);
                    addedCount++;
                }
            }

            log.trace("[Users] handleUsers after update. Plants now: {}", getPlantIds());

            setInitialized(true);
            log.debug("[Users] Parsed {} entities. Added {} pets. Plants in memory: {}", entities.length, addedCount, getPlantCount());
        } catch (Exception e) {
            log.debug("[Users] Failed to parse room entities", e);
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

        PlantUserAction action = null;
        if (PlantManagerFeature.TREAT_COMMAND.equals(text)) {
            action = new TreatPlantsAction(this, processor);
        } else if (PlantManagerFeature.COMPOST_COMMAND.equals(text)) {
            action = new CompostPlantsAction(this, processor);
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
        return TREAT_COMMAND.equals(text) || COMPOST_COMMAND.equals(text) || ABORT_COMMAND.equals(text);
    }

    public void handleGetGuestRoom(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        int roomId = packet.readInteger();
        int requestType = packet.readInteger();

        if (requestType == 0) {
            log.trace("[GetGuestRoom] Room {} requested. Clearing plants. Plants before clear: {}", roomId, getPlantIds());
            clearPlants();
            abortProcessingSafe();
            setCurrentRoomId(roomId);
            log.debug("[GetGuestRoom] Room {} requested. Plants cleared and initialization reset", roomId);
        } else {
            log.debug("[GetGuestRoom] Background room request ignored. Room: {}, type: {}", roomId, requestType);
        }
    }

    public void handleQuit(HMessage hMessage) {
        log.trace("[Quit] Room quit. Clearing plants. Plants before clear: {}", getPlantIds());
        clearPlants();
        abortProcessingSafe();
        setInitialized(false);
        log.debug("[Quit] Room state cleared");
    }

    @Deprecated
    public void handleRemovePetFromFlat(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        int petId = packet.readInteger();

        if (removePlantById(petId) != null) {
            log.debug("[RemovePetFromFlat] Plant {} removed. Plants left: {}", petId, getPlantCount());
        } else {
            log.debug("[RemovePetFromFlat] Pet {} removed but was not tracked", petId);
        }
    }

    public void handleUserRemove(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();

        try {
            String idStr = packet.readString();
            int entityIndexId = Integer.parseInt(idStr);

            List<HEntity> foundPlants = findPlantsByIndex(entityIndexId);

            if (foundPlants.size() > 1) {
                log.error("More than one index found for the removed entity, not removing it from memory");
                return;
            }
            if (foundPlants.size() == 0) {
                log.debug("[UserRemove] User {} removed but was not tracked as a plant", entityIndexId);
                return;
            }
            HEntity uniqueFoundPlant = foundPlants.get(0);
            removePlantById(uniqueFoundPlant.getId());
            log.debug("[UserRemove] Plant {} removed (via UserRemove). Plants left: {}", entityIndexId, getPlantCount());
        } catch (Exception e) {
            log.debug("[UserRemove] Failed to parse UserRemove packet or remove plant", e);
        }
    }

    @Getter
    public enum ActionCommandType {
        TREAT("treated"),
        COMPOST("composted"),
        ABORT("aborted");

        private String verb;

        private ActionCommandType(String verb) {
            this.verb = verb;
        }
    }
}
