package extension.features;

import extension.util.NotificationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AbortPlantsAction implements PlantUserAction {

    private final PlantManagerFeature manager;
    private final PlantProcessor processor;

    @Override
    public void execute() {
        processor.abortProcessing();
        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Plant processing aborted.");
        log.debug("[Plants] Abort command executed");
    }
}
