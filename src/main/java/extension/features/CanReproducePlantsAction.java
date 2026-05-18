package extension.features;

import extension.entity.ACTION_COMMAND_TYPE;
import extension.entity.HEntity_Plant_Stuff_Index_Enum;
import extension.util.NotificationUtils;
import extension.util.PlantUtils;
import extension.util.SleepRateLimit;
import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HEntity;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Handles the `:plants canreproduce on` and `:plants canreproduce off` commands.
 *
 * The server sends a {@code PetStatusUpdate} packet when the canReproduce flag changes.
 * Packet structure: {@code {i:index}{i:petId}{b:?}{s:""}{b:canReproduceState}}
 * The last boolean is the new canReproduce state.
 * We wait for that confirmation before moving to the next plant.
 */
@Slf4j
@RequiredArgsConstructor
public class CanReproducePlantsAction implements UserActionExecutor, ItemProcessingHandler<HEntity> {

    private final PlantManagerFeature manager;
    private final BulkItemProcessor processor;
    private final boolean targetState; // true = turn ON, false = turn OFF

    private static final Map<Integer, CompletableFuture<Boolean>> petStatusRequests = new ConcurrentHashMap<>();
    private static final long TIMEOUT_MS = 5000;

    @Override
    public void execute() {
        List<HEntity> targets = manager.getPlantsSnapshot().stream()
                .filter(this::shouldProcess)
                .collect(Collectors.toList());

        String stateLabel = targetState ? "on" : "off";
        NotificationUtils.showSystemNotificationToUser(
                manager.getExtension(),
                "Setting canReproduce " + stateLabel + " started... (Found " + targets.size() + " plants)");
        log.debug("[CanReproduce] Command started. target={}, plants to process={}", stateLabel, targets.size());
        processor.startProcessing(this, targets);
    }

    @Override
    public boolean shouldProcess(HEntity plant) {
        if (PlantUtils.isDeadPlant(plant)) {
            return false;
        }
        // Only process plants owned by the current user
        int ownerIndex = HEntity_Plant_Stuff_Index_Enum.OWNER_ID.getIndex();
        Object[] stuff = plant.getStuff();
        if (stuff.length <= ownerIndex || !(stuff[ownerIndex] instanceof Number)) {
            log.warn("[CanReproduce] Plant {} has no readable owner id, skipping", plant.getId());
            return false;
        }
        int ownerId = ((Number) stuff[ownerIndex]).intValue();
        if (ownerId != manager.getCurrentUserId()) {
            log.debug("[CanReproduce] Plant {} is not owned by current user, skipping", plant.getId());
            return false;
        }
        // Only process plants that are NOT already in the desired state
        boolean currentState = PlantUtils.isCanReproduceEnabled(plant);
        return currentState != targetState;
    }

    @Override
    public boolean process(HEntity plant) {
        SleepRateLimit.sleepRateLimit();

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        petStatusRequests.put(plant.getId(), future);

        boolean sent = manager.getExtension().sendToServer(
                new HPacket("TogglePetBreedingPermission", HMessage.Direction.TOSERVER, plant.getId()));

        if (!sent) {
            petStatusRequests.remove(plant.getId());
            log.debug("[CanReproduce] Failed to send PetStatusUpdate for plant {}", plant.getId());
            return false;
        }

        try {
            Boolean confirmedState = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            log.debug("[CanReproduce] Plant {} canReproduce confirmed={}", plant.getId(), confirmedState);
            return true;
        } catch (Exception e) {
            petStatusRequests.remove(plant.getId());
            log.debug("[CanReproduce] Timed out or failed waiting for PetStatusUpdate for plant {}", plant.getId());
            return false;
        }
    }

    /**
     * Registered as a dedicated {@code PetStatusUpdate} listener in {@link PlantManagerFeature}.
     * Resets the read index independently because multiple listeners share the same packet.
     *
     * Packet structure: {@code {i:index}{i:petId}{b:?}{s:""}{b:canReproduceState}}
     */
    public static void handlePetStatusUpdate(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            packet.readInteger(); // ignored
            int petId = packet.readInteger();
            packet.readBoolean(); // unknown boolean
            packet.readString();  // unknown string (empty)
            boolean canReproduceState = packet.readBoolean();

            CompletableFuture<Boolean> future = petStatusRequests.remove(petId);
            if (future != null) {
                future.complete(canReproduceState);
            }
        } catch (Exception e) {
            log.debug("[CanReproduce][PetStatusUpdate] Failed to parse PetStatusUpdate packet", e);
        }
    }

    @Override
    public void showSystemNotification(ExtensionForm extension, int processedCount) {
        String stateLabel = targetState ? "on" : "off";
        NotificationUtils.showSystemNotificationToUser(extension,
                "canReproduce set to " + stateLabel + " for " + processedCount + " plant(s).");
    }

    @Override
    public ACTION_COMMAND_TYPE getActionCommandType() {
        return targetState ? ACTION_COMMAND_TYPE.CAN_REPRODUCE_ON : ACTION_COMMAND_TYPE.CAN_REPRODUCE_OFF;
    }
}
