package extension.state;

import java.util.function.Consumer;

import furnidata.FurniDataState;
import furnidata.FurniDataTools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FurniDataStateHandler {

    private final Consumer<FurniDataState> onStateChange;

    private volatile FurniDataTools furniDataTools;
    private volatile FurniDataState state = FurniDataState.UNAVAILABLE;

    public FurniDataStateHandler(Consumer<FurniDataState> onStateChange) {
        this.onStateChange = onStateChange;
    }

    public FurniDataState getState() {
        return state;
    }

    public FurniDataTools get() {
        return furniDataTools;
    }

    public boolean isReady() {
        return state == FurniDataState.LOADED || state == FurniDataState.CACHED;
    }

    public void load(String host) {
        loadInternal(host, false);
    }

    public void reload(String host) {
        loadInternal(host, true);
    }

    private void loadInternal(String host, boolean forceRefresh) {
        if (host == null || host.length() < 7) return;

        updateState(FurniDataState.LOADING);
        furniDataTools = null;

        log.info("[FurniData] {} furni data for host: {}", forceRefresh ? "Reloading" : "Loading", host);
        furniDataTools = new FurniDataTools(host, result -> {
            if (result == null) {
                log.error("[FurniData] Retrieved unexpected load result as null.");
                updateState(FurniDataState.UNAVAILABLE);
                return;
            }
            switch (result) {
                case LOADED:
                    log.info("[FurniData] Load completed successfully from URL");
                    updateState(FurniDataState.LOADED);
                    break;
                case CACHED:
                    log.info("[FurniData] Cache Hit - using cached data without full download");
                    updateState(FurniDataState.CACHED);
                    break;
                case CACHED_WITH_REFRESH_ERROR:
                    log.warn("[FurniData] Cache Hit (refresh from URL failed), using cached data as fallback");
                    updateState(FurniDataState.CACHED_WITH_REFRESH_ERROR);
                    break;
                case FAILED:
                    log.error("[FurniData] Load failed and no cache was available to fallback to");
                    updateState(FurniDataState.FAILED);
                    break;
                case UNAVAILABLE:
                default:
                    log.error("[FurniData] Retrieved unexpected load result {}.", result);
                    updateState(FurniDataState.UNAVAILABLE);
                    break;
            }
        }, forceRefresh);
    }

    public void clear() {
        furniDataTools = null;
        updateState(FurniDataState.UNAVAILABLE);
        log.info("[FurniData] Cleared");
    }

    private void updateState(FurniDataState newState) {
        if (state != newState) {
            state = newState;
            if (onStateChange != null) {
                onStateChange.accept(state);
            }
        }
    }
}
