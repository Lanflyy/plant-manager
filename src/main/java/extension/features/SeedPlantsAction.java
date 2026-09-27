package extension.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;

import extension.entity.ACTION_COMMAND_TYPE;
import extension.state.FurniDataStateHandler;
import extension.util.NotificationUtils;
import extension.util.SleepRateLimit;
import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HFloorItem;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SeedPlantsAction implements UserActionExecutor, ItemProcessingHandler<HFloorItem> {

    private static final List<String> SUPPORTED_SEED_FURNI_CLASSNAME_LIST =
            Collections.unmodifiableList(Arrays.asList("mnstr_seed_nt", "mnstr_seed"));

    @NonNull
    private final PlantManagerFeature manager;
    @NonNull
    private final BulkItemProcessor processor;

    private volatile List<Integer> seedTypeIds = Collections.emptyList();

    @Override
    public void execute() {
        if (manager.getCurrentUserId() == -1 && manager.getCurrentUsername() == null) {
            NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Error: User info not loaded yet. Try again in a moment.");
            log.debug("[Plants] Seed command rejected because user info is missing");
            manager.getExtension().sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
            return;
        }

        FurniDataStateHandler furniDataState = manager.getFurniDataStateHandler();
        if (furniDataState == null) {
            NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Error: Furni data not enabled for this extension.");
            return;
        }

        if (!furniDataState.isReady()) {
            NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Furni data is not loaded.");
            return;
        }

        List<Integer> resolvedSeedFloorTypeIds = new ArrayList<>();
        for (String supportedSeedClassName : SUPPORTED_SEED_FURNI_CLASSNAME_LIST) {
            Integer resolvedSeedFloorTypeId = manager.getFloorTypeId(supportedSeedClassName);
            if (resolvedSeedFloorTypeId != null) {
                resolvedSeedFloorTypeIds.add(resolvedSeedFloorTypeId);
            } else {
                log.warn("[Plants] Cannot find floor type ID for classname '{}'", supportedSeedClassName);
            }
        }

        if (resolvedSeedFloorTypeIds.isEmpty()) {
            log.error("[Plants] Cannot find floor type IDs for classnames {}", SUPPORTED_SEED_FURNI_CLASSNAME_LIST);
            NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Error: Could not find seed type in furni data.");
            return;
        }
        seedTypeIds = Collections.unmodifiableList(resolvedSeedFloorTypeIds);

        List<HFloorItem> targetSeeds = manager.getFloorItemsSnapshot().stream()
                .filter(this::shouldProcess)
                .collect(Collectors.toList());

        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Seeding plants started... (Found " + targetSeeds.size() + " seeds)");
        log.debug("[Plants] Seed command started. Target seeds: {}", targetSeeds.size());
        processor.startProcessing(this, targetSeeds);
    }

    @Override
    public boolean shouldProcess(HFloorItem item) {
        if (seedTypeIds.isEmpty()) {
            log.warn("[Plants] Seed type IDs not resolved, skipping item {}", item.getId());
            return false;
        }

        if (!seedTypeIds.contains(item.getTypeId())) {
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
    @NonNull
	public ACTION_COMMAND_TYPE getActionCommandType() {
		return ACTION_COMMAND_TYPE.SEED;
	}
}
