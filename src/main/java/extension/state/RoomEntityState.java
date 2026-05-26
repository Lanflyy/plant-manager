package extension.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import extension.entity.HEntity_Plant_Stuff_Index_Enum;
import extension.util.PlantSettings;
import extension.util.PlantUtils;
import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HEntity;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RoomEntityState {

    private final Map<Integer, HEntity> entities = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private volatile boolean initialized;

    public RoomEntityState(ExtensionForm extension) {
        extension.intercept(HMessage.Direction.TOCLIENT, "Users", this::handleUsers);
        extension.intercept(HMessage.Direction.TOCLIENT, "UserRemove", this::handleUserRemove);
        extension.intercept(HMessage.Direction.TOSERVER, "GetGuestRoom", this::handleGetGuestRoom);
        extension.intercept(HMessage.Direction.TOSERVER, "Quit", this::handleQuit);
        extension.intercept(HMessage.Direction.TOSERVER, "RemovePetFromFlat", this::handleRemovePetFromFlat);
        extension.intercept(HMessage.Direction.TOCLIENT, "PetStatusUpdate", this::handlePetStatusUpdate);
    }

    public void addEntity(HEntity entity) {
        if (entity == null) return;
        entities.put(entity.getId(), entity);
        PlantSettings.resolveAutoBreedTrustedUserEntity(entity);
    }

    public HEntity removeEntityById(int id) {
        return entities.remove(id);
    }

    public HEntity getEntityById(int id) {
        return entities.get(id);
    }

    public List<HEntity> findEntitiesByIndex(int index) {
        return entities.values()
                .stream()
                .filter(entity -> entity.getIndex() == index)
                .collect(Collectors.toList());
    }

    public List<HEntity> getEntitiesSnapshot() {
        return new ArrayList<>(entities.values());
    }

    public List<HEntity> getPlantsSnapshot() {
        return entities.values().stream()
                .filter(PlantUtils::isPlant)
                .collect(Collectors.toList());
    }

    public int getPlantCount() {
        return (int) entities.values().stream()
                .filter(PlantUtils::isPlant)
                .count();
    }

    public Set<Integer> getPlantIds() {
        return entities.values().stream()
                .filter(PlantUtils::isPlant)
                .map(HEntity::getId)
                .collect(Collectors.toSet());
    }

    public void clearEntities() {
        entities.clear();
    }

    /**
     * Updates the canReproduce flag on the in-memory HEntity for the given pet id.
     * Called after receiving PetStatusUpdate confirmation that the toggle was applied server-side.
     */
    public void updateCanReproduce(int petId, boolean canReproduce) {
        HEntity entity = entities.get(petId);
        if (entity == null) {
            log.warn("[RoomEntityState] updateCanReproduce: entity {} not in memory", petId);
            return;
        }
        int index = HEntity_Plant_Stuff_Index_Enum.CAN_REPRODUCE.getIndex();
        boolean updated = PlantUtils.updateIndexPlantEntity(entity, index, canReproduce);
        if (updated) {
            log.debug("[RoomEntityState] Updated canReproduce={} for entity {}", canReproduce, petId);
        } else {
            log.error("[RoomEntityState] updateCanReproduce: failed to update entity {}", petId);
        }
    }

    private void handlePetStatusUpdate(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            packet.readInteger();
            int petId = packet.readInteger();
            packet.readBoolean();
            packet.readString();
            boolean canReproduce = packet.readBoolean();
            updateCanReproduce(petId, canReproduce);
        } catch (Exception e) {
            log.debug("[RoomEntityState] Failed to parse PetStatusUpdate packet", e);
        }
    }

    private void handleUsers(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            HEntity[] parsedEntities = HEntity.parse(packet);

            if (parsedEntities.length > 1) {
                log.trace("[Users] Detected full room user list ({} entities). Clearing entities.", parsedEntities.length);
                clearEntities();
            }

            for (HEntity entity : parsedEntities) {
                addEntity(entity);
            }

            setInitialized(true);
            log.debug("[Users] Parsed {} entities. Plants in memory: {}", parsedEntities.length, getPlantCount());
        } catch (Exception e) {
            log.debug("[Users] Failed to parse room entities", e);
        }
    }

    private void handleUserRemove(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();

        try {
            String idStr = packet.readString();
            int entityIndexId = Integer.parseInt(idStr);

            List<HEntity> foundEntities = findEntitiesByIndex(entityIndexId);

            if (foundEntities.size() > 1) {
                log.error("More than one index found for the removed entity, not removing it from memory");
                return;
            }
            if (foundEntities.size() == 0) {
                log.trace("[UserRemove] Entity {} removed but was not tracked", entityIndexId);
                return;
            }
            HEntity uniqueFoundEntity = foundEntities.get(0);
            removeEntityById(uniqueFoundEntity.getId());
            log.debug("[UserRemove] Entity {} removed. Plants left: {}", entityIndexId, getPlantCount());
        } catch (Exception e) {
            log.debug("[UserRemove] Failed to parse UserRemove packet", e);
        }
    }

    private void handleGetGuestRoom(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        packet.readInteger(); // roomId
        int requestType = packet.readInteger();

        if (requestType == 0) {
            clearEntities();
            setInitialized(false);
        }
    }

    private void handleQuit(HMessage hMessage) {
        clearEntities();
        setInitialized(false);
    }

    @Deprecated
    private void handleRemovePetFromFlat(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        int petId = packet.readInteger();

        if (removeEntityById(petId) != null) {
            log.debug("[RemovePetFromFlat] Entity {} removed. Plants left: {}", petId, getPlantCount());
        } else {
            log.debug("[RemovePetFromFlat] Pet {} removed but was not tracked", petId);
        }
    }
}
