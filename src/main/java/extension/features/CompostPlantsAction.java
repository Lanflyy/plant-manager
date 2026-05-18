package extension.features;

import extension.entity.ACTION_COMMAND_TYPE;
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
        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Composting plants started... (Found " + manager.getPlantCount() + " plants)");
        log.debug("[Plants] Compost command started. Plants in memory: {}", manager.getPlantCount());
        processor.startProcessing(this, manager.getPlantsSnapshot());
    }

    @Override
    public boolean shouldProcess(HEntity plant) {
        return PlantUtils.isDeadPlant(plant);
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
