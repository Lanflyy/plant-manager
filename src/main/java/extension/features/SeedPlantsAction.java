package extension.features;

import extension.entity.ACTION_COMMAND_TYPE;
import extension.util.NotificationUtils;
import extension.util.SleepRateLimit;
import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HFloorItem;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;

@Slf4j
@RequiredArgsConstructor
public class SeedPlantsAction implements UserActionExecutor, ItemProcessingHandler<HFloorItem> {

    @NonNull
    private final PlantManagerFeature manager;
    @NonNull
    private final BulkItemProcessor processor;

    // Type ID for the plant seed furni as observed in the packet examples
    private static final int SEED_TYPE_ID = 4284;

    @Override
    public void execute() {
        if (manager.getCurrentUserId() == -1 && manager.getCurrentUsername() == null) {
            NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Error: User info not loaded yet. Try again in a moment.");
            log.debug("[Plants] Seed command rejected because user info is missing");
            // Optionally, request it again
            manager.getExtension().sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
            return;
        }

        List<HFloorItem> targetSeeds = manager.getFloorItemsSnapshot().stream()
                .filter(this::shouldProcess)
                .collect(Collectors.toList());

        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Seeding plants started... (Found " + targetSeeds.size() + " seeds)");
        log.debug("[Plants] Seed command started. Target seeds: {}", targetSeeds.size());
        processor.startProcessing(this, targetSeeds);
    }

    @Override
    public boolean shouldProcess(HFloorItem item) {
        if (item.getTypeId() != SEED_TYPE_ID) {
            return false;
        }

        if (item.getOwnerId() <= 0) {
            log.warn("[Plants] Item {} has no owner. Skipping.", item.getId());
            return false;
        }

        if (manager.getCurrentUserId() <= 0) {
            log.warn("[Plants] Current user id has not been parsed. Skipping.");
            return false;
        }

        if(item.getOwnerId() != manager.getCurrentUserId()){
            log.trace("[Plants] Item {} is not owned by current user. Skipping.", item.getId());
            return false;
        }

        return true;
    }

    @Override
    public boolean process(HFloorItem item) {
        SleepRateLimit.sleepRateLimit();

        String packetHeader = "UseFurniture";
        boolean sent = manager.getExtension().sendToServer(new HPacket(packetHeader, HMessage.Direction.TOSERVER, item.getId(), 0));
        log.debug("[{}] Seed {} {}", packetHeader, item.getId(), sent ? "used" : "failed");
        return sent;
    }

    @Override
    public void showSystemNotification(ExtensionForm extension, int processedCount) {
        NotificationUtils.showSystemNotificationToUser(extension, "All plant seeds have been " + getActionCommandType().getVerb() + " (" + processedCount + ")");
    }

	@Override
	public ACTION_COMMAND_TYPE getActionCommandType() {
		return ACTION_COMMAND_TYPE.SEED;
	}
}
