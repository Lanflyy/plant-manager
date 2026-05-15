package extension.features;

import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HEntity;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import extension.util.NotificationUtils;

@Slf4j
public class PlantProcessor {

    private final PlantManagerFeature manager;
    private final ExtensionForm extension;
    private final ExecutorService executor;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    public PlantProcessor(PlantManagerFeature manager, ExtensionForm extension) {
        this.manager = manager;
        this.extension = extension;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "plants-processor");
            t.setDaemon(true);
            return t;
        });
    }

    public boolean startProcessing(PlantManagerFeature.ActionCommandType actionType, PlantProcessingHandler handler) {
        if (!processing.compareAndSet(false, true)) {
            log.debug("[Plants] Processing already running");
            NotificationUtils.showSystemNotificationToUser(extension, "Plant processing is already running. Abort first the previous one");
            return false;
        }

        executor.submit(() -> runProcessing(actionType, handler));
        return true;
    }

    private void runProcessing(PlantManagerFeature.ActionCommandType actionType, PlantProcessingHandler handler) {
        int count = 0;
        try {
            if (handler == null) {
                log.error("No handler provided for action {}", actionType);
                return;
            }

            for (HEntity plant : manager.getPlantsSnapshot()) {
                if (!processing.get()) {
                    break;
                }

                boolean processed = false;
                try {
                    if (handler.shouldProcess(plant)) {
                        processed = handler.process(plant);
                    }
                } catch (Exception e) {
                    log.debug("[Plants] Handler processing encountered an error", e);
                }

                if (processed) {
                    count++;
                }

                sleep();
            }

            if (processing.get()) {
                NotificationUtils.showSystemNotificationToUser(extension, "All plants have been " + actionType.getVerb() + " (" + count + ")");
                log.debug("[Plants] Finished. {} plants {}", count, actionType.getVerb());
            } else {
                NotificationUtils.showSystemNotificationToUser(extension, "Plant processing aborted.");
                log.debug("[Plants] Processing aborted");
            }
        } catch (Exception e) {
            log.debug("[Plants] Processing encountered an error", e);
        } finally {
            processing.set(false);
        }
    }

    public void abortProcessing() {
        processing.set(false);
    }

    // Per-action processing moved to PlantProcessingHandler implementations

    private void sleep() {
        try {
            Thread.sleep(PlantManagerFeature.PROCESS_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("[Plants] Processing sleep interrupted", e);
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
