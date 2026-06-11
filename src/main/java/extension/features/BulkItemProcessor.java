package extension.features;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import extension.entity.ACTION_COMMAND_TYPE;
import extension.util.NotificationUtils;
import gearth.extensions.ExtensionForm;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BulkItemProcessor {

    private final PlantManagerFeature manager;
    private final ExtensionForm extension;
    private final ExecutorService executor;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    public BulkItemProcessor(PlantManagerFeature manager, ExtensionForm extension) {
        this.manager = manager;
        this.extension = extension;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "plants-processor");
            t.setDaemon(true);
            return t;
        });
    }

    public <T> boolean startProcessing(ItemProcessingHandler<T> handler, java.util.Collection<T> items) {
        if (!processing.compareAndSet(false, true)) {
            log.debug("[Plants] Processing already running");
            NotificationUtils.showSystemNotificationToUser(extension, "Plant processing is already running. Abort first the previous one");
            return false;
        }

        executor.submit(() -> runProcessing(handler, items));
        return true;
    }

    private <T> void runProcessing(ItemProcessingHandler<T> handler, java.util.Collection<T> items) {
        ACTION_COMMAND_TYPE actionType = handler.getActionCommandType();
        int count = 0;
        try {
            for (T item : items) {
                if (!processing.get()) {
                    break;
                }

                try {
                    if (handler.shouldProcess(item)) {
                        boolean processed = handler.process(item);
                        if (processed) {
                            count++;
                        }
                        sleep();
                    }
                } catch (Exception e) {
                    log.debug("[Plants] Handler processing encountered an error", e);
                    sleep();
                }
            }

            if (processing.get()) {
                log.debug("[Plants] Finished. {} items {}", count, actionType.getVerb());
                try {
                    handler.onFinished(count);
                } catch (Exception e) {
                    log.debug("[Plants] Handler onFinished threw an exception", e);
                }
                try {
                    handler.showSystemNotification(extension, count);
                } catch (Exception e) {
                    log.debug("[Plants] Handler showSystemNotification threw an exception", e);
                }
            } else {
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

    public boolean isProcessing() {
        return processing.get();
    }

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
