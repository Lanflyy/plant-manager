package extension.features;

import gearth.extensions.parsers.HEntity;

public interface PlantProcessingHandler {
    boolean shouldProcess(HEntity plant);
    boolean process(HEntity plant);
}
