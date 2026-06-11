package extension.features;

import java.util.List;
import java.util.stream.Collectors;

import extension.entity.ACTION_COMMAND_TYPE;
import extension.entity.HEntity_Plant_Stuff_Index_Enum;
import extension.util.NotificationUtils;
import extension.util.PlantUtils;
import gearth.extensions.parsers.HEntity;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CompostPlantsAction implements UserActionExecutor, ItemProcessingHandler<HEntity> {

    private final PlantManagerFeature manager;
    private final BulkItemProcessor processor;

    @Override
    public void execute() {
        List<HEntity> plants = manager.getPlantsSnapshot().stream()
                .filter(this::shouldProcess)
                .collect(Collectors.toList());
        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Composting plants started... (Found " + plants.size() + " compostable plants)");
        log.debug("[Plants] Compost command started. Plants to process: {}, Total plants: {}", plants.size(), manager.getPlantCount());
        processor.startProcessing(this, plants);
    }

    @Override
    public boolean shouldProcess(HEntity plant) {
        if (!PlantUtils.isDeadPlant(plant)) {
            return false;
        }
        int ownerIndex = HEntity_Plant_Stuff_Index_Enum.OWNER_ID.getIndex();
        Object[] stuff = plant.getStuff();
        if (stuff.length <= ownerIndex || !(stuff[ownerIndex] instanceof Number)) {
            log.warn("[Compost] Plant {} has no readable owner id, skipping", plant.getId());
            return false;
        }
        int ownerId = ((Number) stuff[ownerIndex]).intValue();
        return ownerId == manager.getCurrentUserId();
    }

    @Override
    public boolean process(HEntity plant) {
        String packetHeader = "CompostPlant";
        boolean sent = manager.getExtension().sendToServer(new HPacket(packetHeader, HMessage.Direction.TOSERVER, plant.getId()));
        log.debug("[{}] Plant {} {}", packetHeader, plant.getId(), sent ? "sent" : "failed");
        if (sent) {
            manager.removePlantById(plant.getId());
            return true;
        }
        return false;
    }

	@Override
	public ACTION_COMMAND_TYPE getActionCommandType() {
		return ACTION_COMMAND_TYPE.COMPOST;
	}

}
