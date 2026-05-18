package extension.features;

import extension.util.NotificationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AbortPlantsAction implements UserActionExecutor {

    private final PlantManagerFeature manager;
    private final BulkItemProcessor processor;

    @Override
    public void execute() {
        if (processor.isProcessing()) {
            processor.abortProcessing();
            NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Plant processing aborted.");
            log.debug("[Plants] Abort command executed");
        } else {
            NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "No plant processing is currently running.");
            log.debug("[Plants] Abort command executed but no processing was running");
        }
    }
}
