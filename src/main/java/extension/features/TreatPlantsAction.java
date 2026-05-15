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
public class TreatPlantsAction implements PlantUserAction, PlantProcessingHandler {

    private final PlantManagerFeature manager;
    private final PlantProcessor processor;

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
        String packetHeader = "RespectPet";
        boolean sent = manager.getExtension().sendToServer(new HPacket(packetHeader, HMessage.Direction.TOSERVER, plant.getId()));
        log.debug("[{}] Plant {} {}", packetHeader, plant.getId(), sent ? "sent" : "failed");
        if (sent) {
            manager.removePlantById(plant.getId());
            return true;
        }
        return false;
    }
}
