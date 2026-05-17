package extension.features;

import extension.util.NotificationUtils;
import extension.util.PlantSettings;
import extension.util.PlantUtils;
import extension.entity.PetInfoEntity;
import gearth.extensions.parsers.HEntity;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import gearth.extensions.ExtensionForm;
import extension.util.SleepRateLimit;

@Slf4j
@RequiredArgsConstructor
public class TreatPlantsAction implements PlantUserAction, PlantProcessingHandler {

    private final PlantManagerFeature manager;
    private final PlantProcessor processor;
    private final AtomicInteger skippedDueWellbeing = new AtomicInteger(0);
    private static final int WELLBEING_THRESHOLD_SECONDS = 3600; // 1 hour
    private static final Map<Integer, CompletableFuture<PetInfoEntity>> petInfoRequests = new ConcurrentHashMap<>();
    private static final int PETINFO_MAX_RETRIES = 5;

    @Override
    public void execute() {
        long livingCount = manager.getPlantsSnapshot().stream().filter(plant -> !PlantUtils.isDeadPlant(plant)).count();
        long deadCount = manager.getPlantCount() - livingCount;
        StringBuilder msg = new StringBuilder("Treating plants started... (");
        msg.append(livingCount).append(" living");
        if (deadCount > 0) {
            msg.append(", ").append(deadCount).append(" dead ignored)");
        } else {
            msg.append(")");
        }
        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), msg.toString());
        log.debug("[Plants] Treat command started. Plants in memory: {}", manager.getPlantCount());
        processor.startProcessing(PlantManagerFeature.ActionCommandType.TREAT, this);
    }

    @Override
    public boolean shouldProcess(HEntity plant) {
        return !PlantUtils.isDeadPlant(plant);
    }

    @Override
    public boolean process(HEntity plant) {
        // Optionally request pet info and check wellbeing
        if (PlantSettings.isRequestPetInfoBeforeTreat()) {
            PetInfoEntity info = requestPetInfo(plant.getId(), 3000);
            if (!processor.isProcessing()) {
                log.debug("[Treat] Processing aborted during wellbeing check for plant {}", plant.getId());
                return false;
            }

            if (info == null) {
                log.debug("[Treat] No PetInfo received for plant {} - skipping", plant.getId());
                return false;
            }

            long currentMissingWellbeing = info.getMaxWellbeingSeconds() - info.getCurrentWellbeingSeconds();
            if (currentMissingWellbeing >= 0 && currentMissingWellbeing <= WELLBEING_THRESHOLD_SECONDS) {
                skippedDueWellbeing.incrementAndGet();
                log.debug("[Treat] Skipping plant {} due to missing wellbeing {} <= {}", plant.getId(), currentMissingWellbeing, WELLBEING_THRESHOLD_SECONDS);
                return false;
            }
        }

        // rate-limit before sending Treat
        SleepRateLimit.sleepRateLimit();

        String packetHeader = "RespectPet";
        boolean sent = manager.getExtension().sendToServer(new HPacket(packetHeader, HMessage.Direction.TOSERVER, plant.getId()));
        log.debug("[{}] Plant {} {}", packetHeader, plant.getId(), sent ? "sent" : "failed");
        if (sent) {
            return true;
        }
        return false;
    }

    private PetInfoEntity requestPetInfo(int petId, long timeoutMillis) {
        for (int attempt = 1; attempt <= PETINFO_MAX_RETRIES && processor.isProcessing(); attempt++) {
            CompletableFuture<PetInfoEntity> future = new CompletableFuture<>();
            petInfoRequests.put(petId, future);
            // rate-limit before sending GetPetInfo
            SleepRateLimit.sleepRateLimit();
            boolean sent = manager.getExtension().sendToServer(new HPacket("GetPetInfo", HMessage.Direction.TOSERVER, petId));
            if (!sent) {
                petInfoRequests.remove(petId);
                log.debug("[PetInfo] Failed to send GetPetInfo for {}", petId);
                return null;
            }
            try {
                PetInfoEntity info = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
                return info;
            } catch (Exception e) {
                petInfoRequests.remove(petId);
                log.debug("[PetInfo] Timed out or failed waiting for PetInfo for {} (attempt {}/{})", petId, attempt, PETINFO_MAX_RETRIES);
                if (attempt < PETINFO_MAX_RETRIES) {
                    log.debug("[PetInfo] Retrying GetPetInfo for {} (next attempt {}/{})", petId, attempt + 1, PETINFO_MAX_RETRIES);
                } else {
                    log.debug("[PetInfo] All retries exhausted for {}", petId);
                    NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Failed to retrieve plant info for pet " + petId + " after " + PETINFO_MAX_RETRIES + " attempts");
                }
            }
        }
        if(!processor.isProcessing()){
            log.debug("[TreatPlantsAction] requestPetInfo for {} aborted because processing was aborted", petId);
        }
        return null;
    }

    public static void handlePetInfo(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            PetInfoEntity info = PetInfoEntity.fromPacket(packet);
            CompletableFuture<PetInfoEntity> future = petInfoRequests.remove(info.getPetId());
            if (future != null) {
                future.complete(info);
            } else {
                log.trace("[PetInfo] Received PetInfo for {} but no pending request", info.getPetId());
            }
        } catch (Exception e) {
            log.debug("[PetInfo] Failed to parse PetInfo packet", e);
        }
    }

    @Override
    public void onFinished(int processedCount) {
        int skipped = skippedDueWellbeing.get();
        log.debug("[Treat] Processing complete. treated={}, skippedWellbeing={}", processedCount, skipped);
    }

    @Override
    public void showSystemNotification(ExtensionForm extension, PlantManagerFeature.ActionCommandType actionType, int processedCount) {
        int skipped = skippedDueWellbeing.get();
        String msg = "All plants have been " + actionType.getVerb() + " (" + processedCount + ")";
        if (skipped > 0) {
            msg += ", skipped due to wellbeing: " + skipped;
        }
        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), msg);
    }
}
