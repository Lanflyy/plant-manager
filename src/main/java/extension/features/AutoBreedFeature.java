package extension.features;

import extension.entity.HEntity_Plant_Stuff_Index_Enum;
import extension.state.RoomEntityState;
import extension.util.NotificationUtils;
import extension.util.PlantSettings;
import extension.util.PlantUtils;
import gearth.extensions.parsers.HEntity;
import gearth.extensions.parsers.HEntityType;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class AutoBreedFeature {

    private enum PendingClickAction {
        NONE,
        ADD,
        REMOVE
    }

    private static final int BREED_PETS_COUNT = 2;
    private static final int AUTO_BREED_DELAY_MS = 250;
    private static final String CLICK_ADD_USER_MESSAGE = "Please click on the user to add to auto-breed list";
    private static final String CLICK_REMOVE_USER_MESSAGE = "Please click on the user to remove from auto-breed list";
    private static final String ABORT_MESSAGE = "Auto-breed add/remove user action aborted.";

    private final PlantManagerFeature manager;
    private volatile PendingClickAction pendingClickAction = PendingClickAction.NONE;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Plants-AutoBreed");
            thread.setDaemon(true);
            return thread;
        }
    });

    public AutoBreedFeature(PlantManagerFeature manager) {
        this.manager = manager;
    }

    public void install() {
        manager.getExtension().intercept(HMessage.Direction.TOCLIENT, "PetBreeding", this::handlePetBreeding);
        manager.getExtension().intercept(HMessage.Direction.TOSERVER, "ClickCharacter", this::handleClickCharacter);
        PlantSettings.addAutoBreedListener(this::resolveLoadedTrustedUsers);
    }

    public void beginClickAddUser() {
        pendingClickAction = PendingClickAction.ADD;
        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), CLICK_ADD_USER_MESSAGE);
    }

    public void beginClickRemoveUser() {
        pendingClickAction = PendingClickAction.REMOVE;
        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), CLICK_REMOVE_USER_MESSAGE);
    }

    public boolean isClickActionPending() {
        return pendingClickAction != PendingClickAction.NONE;
    }

    public void stopClickUser(boolean showNotification) {
        PendingClickAction prev = pendingClickAction;
        pendingClickAction = PendingClickAction.NONE;
        if (prev == PendingClickAction.ADD || prev == PendingClickAction.REMOVE) {
            if (showNotification) {
                NotificationUtils.showSystemNotificationToUser(manager.getExtension(), ABORT_MESSAGE);
            }
        }
    }

    public void reset() {
         stopClickUser(false);
     }

    public void addTrustedUsername(String username) {
        boolean added = PlantSettings.addAutoBreedTrustedUsername(username);
        HEntity entity = findCurrentHabboEntityByUsername(username);
        if (entity != null) {
            PlantSettings.resolveAutoBreedTrustedUserEntity(entity);
        }
        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), added ? "Added " + username + " to auto-breed list." : username + " is already in auto-breed list.");
    }

    public void removeTrustedUsername(String username) {
        boolean removed = PlantSettings.removeAutoBreedTrustedUsername(username);
        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), removed ? "Removed " + username + " from auto-breed list." : username + " was not in auto-breed list.");
    }

    private void resolveLoadedTrustedUsers() {
        RoomEntityState roomEntityState = manager.getRoomEntityState();
        if (roomEntityState == null) {
            return;
        }
        for (HEntity entity : roomEntityState.getEntitiesSnapshot()) {
            PlantSettings.resolveAutoBreedTrustedUserEntity(entity);
        }
    }

    private void handlePetBreeding(HMessage hMessage) {
        if (!PlantSettings.isAutoBreedEnabled()) {
            return;
        }

        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            int requesterEntityIndex = packet.readInteger();
            int myPlantId = packet.readInteger();
            int senderPlantId = packet.readInteger();

            boolean acceptAllUsers = PlantSettings.isAutoBreedAcceptAllUsers();
            String senderPlantOwnerUsername = findSenderPlantOwnerUsername(senderPlantId, requesterEntityIndex);

            if (!acceptAllUsers) {
                if (senderPlantOwnerUsername == null || !PlantSettings.containsAutoBreedTrustedUsername(senderPlantOwnerUsername)) {
                    log.debug("[AutoBreed] Breeding request ignored. requesterEntityIndex={}, myPlantId={}, senderPlantId={}, senderPlantOwnerUsername={}", requesterEntityIndex, myPlantId, senderPlantId, senderPlantOwnerUsername);
                    return;
                }
            }

            if (!canAutoAcceptByRarity(myPlantId, senderPlantId, senderPlantOwnerUsername)) {
                return;
            }

            hMessage.setBlocked(true);
            scheduler.schedule(() -> acceptBreeding(myPlantId, senderPlantId, senderPlantOwnerUsername), AUTO_BREED_DELAY_MS, TimeUnit.MILLISECONDS);
            log.debug("[AutoBreed] Breeding request queued. myPlantId={}, senderPlantId={}, senderPlantOwnerUsername={}, acceptAll={}", myPlantId, senderPlantId, senderPlantOwnerUsername, acceptAllUsers);
        } catch (Exception e) {
            log.debug("[AutoBreed] Failed to parse PetBreeding packet", e);
        }
    }

    private boolean canAutoAcceptByRarity(int myPlantId, int senderPlantId, String senderPlantOwnerUsername) {
        if (!PlantSettings.isAutoBreedRequireSameOrHigherRarity()) {
            return true;
        }

        RoomEntityState roomEntityState = manager.getRoomEntityState();
        if (roomEntityState == null) {
            log.debug("[AutoBreed] Breeding request from {} ignored because room entity state is not loaded", senderPlantOwnerUsername);
            return false;
        }

        HEntity myPlantEntity = roomEntityState.getEntityById(myPlantId);
        HEntity senderPlantEntity = roomEntityState.getEntityById(senderPlantId);
        int myPlantRarityLevel = PlantUtils.getRarityLevel(myPlantEntity);
        int senderPlantRarityLevel = PlantUtils.getRarityLevel(senderPlantEntity);
        if (myPlantRarityLevel < 0 || senderPlantRarityLevel < 0) {
            log.debug("[AutoBreed] Breeding request from {} ignored because rarity levels could not be read. myPlantId={}, senderPlantId={}", senderPlantOwnerUsername, myPlantId, senderPlantId);
            return false;
        }

        boolean canAccept = senderPlantRarityLevel >= myPlantRarityLevel;
        if (!canAccept) {
            log.debug("[AutoBreed] Breeding request from {} ignored because sender plant rarity {} is lower than my plant rarity {}", senderPlantOwnerUsername, senderPlantRarityLevel, myPlantRarityLevel);
        }
        return canAccept;
    }
    private void handleClickCharacter(HMessage hMessage) {
        PendingClickAction action = pendingClickAction;
        if (action == PendingClickAction.NONE) {
            return;
        }

        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            int entityIndex = packet.readInteger();
            HEntity entity = findCurrentHabboEntityByIndex(entityIndex);
            if (entity == null) {
                NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Could not find that user in memory.");
                pendingClickAction = PendingClickAction.NONE;
                return;
            }

            hMessage.setBlocked(true);
            if (action == PendingClickAction.ADD) {
                addTrustedUsername(entity.getName());
            } else if (action == PendingClickAction.REMOVE) {
                removeTrustedUsername(entity.getName());
            }
            pendingClickAction = PendingClickAction.NONE;
        } catch (Exception e) {
            pendingClickAction = PendingClickAction.NONE;
            log.debug("[AutoBreed] Failed to parse ClickCharacter packet", e);
        }
    }

    private void acceptBreeding(int myPlantId, int senderPlantId, String senderPlantOwnerUsername) {
        try {
            if (!PlantSettings.isAutoBreedEnabled()) {
                log.debug("[AutoBreed] Queued breeding accept skipped because auto-breed is disabled. myPlantId={}, senderPlantId={}", myPlantId, senderPlantId);
                return;
            }
            boolean acceptAllUsers = PlantSettings.isAutoBreedAcceptAllUsers();
            if (!acceptAllUsers && !PlantSettings.containsAutoBreedTrustedUsername(senderPlantOwnerUsername)) {
                log.debug("[AutoBreed] Queued breeding accept skipped because user is not trusted. myPlantId={}, senderPlantId={}", myPlantId, senderPlantId);
                return;
            }
            boolean sent = manager.getExtension().sendToServer(new HPacket("BreedPets", HMessage.Direction.TOSERVER, BREED_PETS_COUNT, myPlantId, senderPlantId));
            log.info("[AutoBreed] Accepted breeding from {}. myPlantId={}, senderPlantId={} ({})", senderPlantOwnerUsername, myPlantId, senderPlantId, sent ? "sent" : "failed");
        } catch (Exception e) {
            log.debug("[AutoBreed] Failed to send BreedPets packet", e);
        }
    }

    private String findSenderPlantOwnerUsername(int senderPlantId, int requesterEntityIndex) {
        RoomEntityState roomEntityState = manager.getRoomEntityState();
        if (roomEntityState == null) {
            return null;
        }

        HEntity senderPlantEntity = roomEntityState.getEntityById(senderPlantId);
        String senderPlantOwnerUsername = getPlantOwnerUsername(senderPlantEntity);
        if (senderPlantOwnerUsername != null) {
            return senderPlantOwnerUsername;
        }

        HEntity requesterEntity = findCurrentHabboEntityByIndex(requesterEntityIndex);
        return requesterEntity == null ? null : emptyToNull(requesterEntity.getName());
    }

    private HEntity findCurrentHabboEntityByIndex(int entityIndex) {
        RoomEntityState roomEntityState = manager.getRoomEntityState();
        if (roomEntityState == null) {
            return null;
        }
        List<HEntity> entities = roomEntityState.findEntitiesByTypeIndex(HEntityType.HABBO, entityIndex);
        return entities.isEmpty() ? null : entities.get(0);
    }

    private HEntity findCurrentHabboEntityByUsername(String username) {
        RoomEntityState roomEntityState = manager.getRoomEntityState();
        String cleaned = emptyToNull(username);
        if (roomEntityState == null || cleaned == null) {
            return null;
        }
        for (HEntity entity : roomEntityState.getEntitiesSnapshot()) {
            if (entity.getEntityType() == HEntityType.HABBO && cleaned.equalsIgnoreCase(entity.getName())) {
                return entity;
            }
        }
        return null;
    }

    private String getPlantOwnerUsername(HEntity senderPlantEntity) {
        if (senderPlantEntity == null || !PlantUtils.isPlant(senderPlantEntity)) {
            return null;
        }
        Object[] stuff = senderPlantEntity.getStuff();
        int ownerUsernameIndex = HEntity_Plant_Stuff_Index_Enum.OWNER_USERNAME.getIndex();
        if (stuff.length <= ownerUsernameIndex || !(stuff[ownerUsernameIndex] instanceof String)) {
            return null;
        }
        return emptyToNull((String) stuff[ownerUsernameIndex]);
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
