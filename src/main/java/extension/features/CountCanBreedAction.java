package extension.features;

import extension.util.NotificationUtils;
import extension.util.PlantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CountCanBreedAction implements UserActionExecutor {

    private final PlantManagerFeature manager;

    @Override
    public void execute() {
        int currentUserId = manager.getCurrentUserId();
        long ownedCanBreed = manager.getPlantsSnapshot().stream()
                .filter(PlantUtils::isCanBreed)
                .filter(plant -> PlantUtils.getOwnerId(plant) == currentUserId)
                .count();
        long notOwnedCanBreed = manager.getPlantsSnapshot().stream()
                .filter(PlantUtils::isCanBreed)
                .filter(plant -> PlantUtils.getOwnerId(plant) != currentUserId)
                .count();
        NotificationUtils.showSystemNotificationToUser(manager.getExtension(),
                "Plants that can breed - Owned: " + ownedCanBreed + ", Not owned: " + notOwnedCanBreed);
        log.debug("[Chat] Count can breed: owned={}, notOwned={}", ownedCanBreed, notOwnedCanBreed);
    }
}
