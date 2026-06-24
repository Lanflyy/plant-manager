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
import gearth.extensions.parsers.HEntityType;
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
        extension.intercept(HMessage.Direction.TOCLIENT, "RoomReady", this::handleRoomReady);
        extension.intercept(HMessage.Direction.TOCLIENT, "CloseConnection", this::handleCloseConnection);
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

    public List<HEntity> findEntitiesByTypeIndex(HEntityType entityType,int index) {
        return entities.values()
                .stream()
                .filter(entity -> entity.getIndex() == index && entity.getEntityType() == entityType)
                .collect(Collectors.toList());
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
     * Updates the canBreed flag on the in-memory HEntity for the given pet id.
     */
    public void updateCanBreed(int petId, boolean canBreed) {
        HEntity entity = entities.get(petId);
        if (entity == null) {
            log.warn("[RoomEntityState] updateCanBreed: entity {} not in memory", petId);
            return;
        }
        int index = HEntity_Plant_Stuff_Index_Enum.CAN_BREED.getIndex();
        boolean updated = PlantUtils.updateIndexPlantEntity(entity, index, canBreed);
        if (updated) {
            log.debug("[RoomEntityState] Updated canBreed={} for entity {}", canBreed, petId);
        } else {
            log.error("[RoomEntityState] updateCanBreed: failed to update entity {}", petId);
        }
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

    /**
     * Updates the isDead flag on the in-memory HEntity for the given pet id.
     */
    public void updateIsDead(int petId, boolean isDead) {
        HEntity entity = entities.get(petId);
        if (entity == null) {
            log.warn("[RoomEntityState] updateIsDead: entity {} not in memory", petId);
            return;
        }
        int index = HEntity_Plant_Stuff_Index_Enum.IS_DEAD.getIndex();
        boolean updated = PlantUtils.updateIndexPlantEntity(entity, index, isDead);
        if (updated) {
            log.debug("[RoomEntityState] Updated isDead={} for entity {}", isDead, petId);
        } else {
            log.error("[RoomEntityState] updateIsDead: failed to update entity {}", petId);
        }
    }

    /**
     * Packet structure: {@code {i:index}{i:petId}{b:canBreed}{b:false}{b:isDead}{b:canReproduce}}
     * The 4 booleans after petId are: canBreed, always-false, isDead, canReproduce.
     * An empty string ({@code 00 00}) has identical bytes to two false booleans,
     * which is why the old boolean+string+boolean parse happened to work.
     */
    private void handlePetStatusUpdate(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            packet.readInteger(); // index (ignored)
            int petId = packet.readInteger();
            boolean canBreed = packet.readBoolean();
            packet.readBoolean(); // always false
            boolean isDead = packet.readBoolean();
            boolean canReproduce = packet.readBoolean();
            updateCanBreed(petId, canBreed);
            updateIsDead(petId, isDead);
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

    private void handleRoomReady(HMessage hMessage) {
        clearEntities();
        setInitialized(false);
        log.debug("[RoomReady] Room entities reset");
    }

    private void handleCloseConnection(HMessage hMessage) {
        clearEntities();
        setInitialized(false);
        log.debug("[CloseConnection] Room entities reset");
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
