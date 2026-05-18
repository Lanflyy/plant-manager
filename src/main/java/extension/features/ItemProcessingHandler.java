package extension.features;

import gearth.extensions.ExtensionForm;

import org.jspecify.annotations.NonNull;

import extension.entity.ACTION_COMMAND_TYPE;
import extension.util.NotificationUtils;

public interface ItemProcessingHandler<T> {
    @NonNull
    ACTION_COMMAND_TYPE getActionCommandType();
    boolean shouldProcess(T item);
    boolean process(T item);
    default void onFinished(int processedCount) {}

    default void showSystemNotification(ExtensionForm extension, int processedCount) {
        NotificationUtils.showSystemNotificationToUser(extension, "All plants have been " + getActionCommandType().getVerb() + " (" + processedCount + ")");
    }
}
