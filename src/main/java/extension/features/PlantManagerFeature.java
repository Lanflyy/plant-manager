package extension.features;

import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HEntity;
import gearth.extensions.parsers.HEntityType;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public final class PlantManagerFeature {

    private static final String TREAT_COMMAND = ":plants";
    private static final String COMPOST_COMMAND = ":plants compost";
    private static final String ABORT_COMMAND = ":plants abort";
    private static final int PROCESS_DELAY_MS = 600;

    private final ExtensionForm extension;
    private final Map<Integer, Boolean> plants = new ConcurrentHashMap<>();
    private volatile boolean processing;
    private volatile boolean initialized;
    private volatile int currentRoomId = -1;

    public void install() {
        extension.intercept(HMessage.Direction.TOCLIENT, "Users", this::handleUsers);
        extension.intercept(HMessage.Direction.TOSERVER, "Chat", this::handleChat);
        extension.intercept(HMessage.Direction.TOSERVER, "GetGuestRoom", this::handleGetGuestRoom);
        extension.intercept(HMessage.Direction.TOSERVER, "Quit", this::handleQuit);
        extension.intercept(HMessage.Direction.TOSERVER, "RemovePetFromFlat", this::handleRemovePetFromFlat);
        log.debug("[Plants] Feature installed");
    }

    public void reset() {
        plants.clear();
        processing = false;
        initialized = false;
        currentRoomId = -1;
        log.debug("[Plants] State reset");
    }

    private void handleUsers(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            HEntity[] entities = HEntity.parse(packet);
            int addedCount = 0;

            for (HEntity entity : entities) {
                if (entity.getEntityType() == HEntityType.PET) {
                    plants.put(entity.getId(), isDeadPlant(entity));
                    addedCount++;
                }
            }

            initialized = true;
            log.debug("[Users] Parsed {} entities. Added {} pets. Plants in memory: {}", entities.length, addedCount, plants.size());
        } catch (Exception e) {
            log.debug("[Users] Failed to parse room entities", e);
        }
    }

    private boolean isDeadPlant(HEntity entity) {
        Object[] stuff = entity.getStuff();
        return stuff.length > 8 && Boolean.TRUE.equals(stuff[8]);
    }

    private void handleChat(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        String text = packet.readString();

        if (!isCommand(text)) {
            return;
        }

        hMessage.setBlocked(true);
        log.debug("[Chat] Command received: {}", text);

        if (!initialized) {
            sendSystemMessage("Error: Room not initialized yet! Please wait for the room to load or reload it.");
            log.debug("[Chat] Command rejected because the room is not initialized");
            return;
        }

        if (TREAT_COMMAND.equals(text)) {
            processing = true;
            long livingCount = plants.values().stream().filter(isDead -> !isDead).count();
            long deadCount = plants.size() - livingCount;
            StringBuilder msg = new StringBuilder("Treating plants started... (");
            msg.append(livingCount).append(" living");
            if (deadCount > 0) {
                msg.append(", ").append(deadCount).append(" dead ignored)");
            } else {
                msg.append(")");
            }
            sendSystemMessage(msg.toString());
            log.debug("[Plants] Treat command started. Plants in memory: {}", plants.size());
            processPlants(ActionType.TREAT);
        } else if (COMPOST_COMMAND.equals(text)) {
            processing = true;
            sendSystemMessage("Composting plants started... (Found " + plants.size() + " plants)");
            log.debug("[Plants] Compost command started. Plants in memory: {}", plants.size());
            processPlants(ActionType.COMPOST);
        } else if (ABORT_COMMAND.equals(text)) {
            processing = false;
            log.debug("[Plants] Abort command executed");
        }
    }

    private boolean isCommand(String text) {
        return TREAT_COMMAND.equals(text) || COMPOST_COMMAND.equals(text) || ABORT_COMMAND.equals(text);
    }

    private void processPlants(ActionType actionType) {
        Thread processThread = new Thread(() -> {
            int count = 0;

            for (Map.Entry<Integer, Boolean> plant : plants.entrySet()) {
                if (!processing) {
                    break;
                }

                int plantId = plant.getKey();
                boolean isDead = plant.getValue();
                boolean shouldCompost = actionType == ActionType.COMPOST && isDead;
                boolean shouldTreat = actionType == ActionType.TREAT && !isDead;

                if (shouldCompost || shouldTreat) {
                    String packetName = shouldCompost ? "CompostPlant" : "RespectPet";
                    boolean sent = extension.sendToServer(new HPacket(packetName, HMessage.Direction.TOSERVER, plantId));
                    if (sent) {
                        plants.remove(plantId);
                        count++;
                    }
                    log.debug("[{}] Plant {} {}", packetName, plantId, sent ? "sent" : "failed");
                    sleep();
                }
            }

            if (processing) {
                String actionName = actionType == ActionType.COMPOST ? "composted" : "treated";
                sendSystemMessage("All plants have been " + actionName + " (" + count + ")");
                log.debug("[Plants] Finished. {} plants {}", count, actionName);
                processing = false;
            } else {
                sendSystemMessage("Plant processing aborted.");
                log.debug("[Plants] Processing aborted");
            }
        }, "plants-processor");
        processThread.setDaemon(true);
        processThread.start();
    }

    private void sleep() {
        try {
            Thread.sleep(PROCESS_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("[Plants] Processing sleep interrupted", e);
        }
    }

    private void handleGetGuestRoom(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        int roomId = packet.readInteger();
        int requestType = packet.readInteger();

        if (requestType == 0) {
            plants.clear();
            processing = false;
            initialized = false;
            currentRoomId = roomId;
            log.debug("[GetGuestRoom] Room {} requested. Plants cleared and initialization reset", roomId);
        } else {
            log.debug("[GetGuestRoom] Background room request ignored. Room: {}, type: {}", roomId, requestType);
        }
    }

    private void handleQuit(HMessage hMessage) {
        plants.clear();
        processing = false;
        initialized = false;
        log.debug("[Quit] Room state cleared");
    }

    private void handleRemovePetFromFlat(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        int petId = packet.readInteger();

        if (plants.remove(petId) != null) {
            log.debug("[RemovePetFromFlat] Plant {} removed. Plants left: {}", petId, plants.size());
        } else {
            log.debug("[RemovePetFromFlat] Pet {} removed but was not tracked", petId);
        }
    }

    private void sendSystemMessage(String message) {
        boolean sent = extension.sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, -1, message, 0, 30, 0, -1));
        log.debug("[Whisper] {} ({})", message, sent ? "sent" : "failed");
    }

    private enum ActionType {
        TREAT,
        COMPOST
    }
}
