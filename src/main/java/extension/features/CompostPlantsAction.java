package extension.features;

import extension.util.NotificationUtils;
import extension.util.PlantUtils;
import gearth.extensions.parsers.HEntity;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CompostPlantsAction implements PlantUserAction, PlantProcessingHandler {

    private final PlantManagerFeature manager;
    private final PlantProcessor processor;

    @Override
    public void execute() {
        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Composting plants started... (Found " + manager.getPlantCount() + " plants)");
        log.debug("[Plants] Compost command started. Plants in memory: {}", manager.getPlantCount());
        processor.startProcessing(PlantManagerFeature.ActionCommandType.COMPOST, this);
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
}
