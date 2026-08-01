package extension.state;

import java.util.function.Consumer;

import furnidata.FurniDataTools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FurniDataState {

    public enum State {
        UNAVAILABLE,
        LOADING,
        LOADED
    }

    private final Consumer<State> onStateChange;

    private volatile FurniDataTools furniDataTools;
    private volatile State state = State.UNAVAILABLE;
    private volatile int loadGeneration = 0;
    private volatile String currentHost;

    public FurniDataState(Consumer<State> onStateChange) {
        this.onStateChange = onStateChange;
    }

    public State getState() {
        return state;
    }

    public FurniDataTools get() {
        return furniDataTools;
    }

    public boolean isReady() {
        return state == State.LOADED && furniDataTools != null && furniDataTools.isReady();
    }

    public void load(String host) {
        if (host == null || host.length() < 7) return;
        this.currentHost = host;

        int generation = ++loadGeneration;
        updateState(State.LOADING);
        furniDataTools = null;

        log.debug("[FurniData] Loading furni data for host: {}", host);

        final FurniDataTools[] holder = new FurniDataTools[1];
        holder[0] = new FurniDataTools(host, () -> {
            if (loadGeneration != generation) {
                log.debug("[FurniData] Load result discarded (stale generation)");
                return;
            }
            furniDataTools = holder[0];
            log.debug("[FurniData] Load completed successfully");
            updateState(State.LOADED);
        });
    }

    public void clear() {
        ++loadGeneration;
        furniDataTools = null;
        currentHost = null;
        updateState(State.UNAVAILABLE);
        log.debug("[FurniData] Cleared");
    }

    public String getCurrentHost() {
        return currentHost;
    }

    private void updateState(State newState) {
        if (state != newState) {
            state = newState;
            if (onStateChange != null) {
                onStateChange.accept(state);
            }
        }
    }
}
