package extension.util;

import extension.entity.HEntity_Plant_Stuff_Index_Enum;
import extension.entity.PET_TYPES;
import gearth.extensions.parsers.HEntity;
import gearth.extensions.parsers.HEntityType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlantUtils {
    private PlantUtils() {
        // Utility class, prevent instantiation
    }
    
    public static boolean isPlant(HEntity entity) {
    	if(entity.getEntityType() != HEntityType.PET) {
    		return false;
    	}
    	Object[] stuff = entity.getStuff();
        Object petType = stuff[0];
        if (petType == null) {
            return false;
        }
        if(!(petType instanceof Number)) {
        	log.error("Checking if plant, but stuff[0] is not even a number: {}", stuff[0]);
        	return false;
        }
    	if(((Number) petType).intValue() == PET_TYPES.PLANT.getPetType()) {
    		return true;
    	}
    	return false;
    }
    
    public static boolean updateIndexPlantEntity(HEntity entity, int index, Object value){
        if (!isPlant(entity)) {
            return false;
        }
        Object[] stuff = entity.getStuff();
        if (stuff.length <= index) {
            log.warn("Checking update index {}, but stuff length is {}, expected at least {}", index, stuff.length, index + 1);
            return false;
        }
        stuff[index] = value;
        return true;
    }

    public static boolean isCanReproduceEnabled(HEntity entity) {
        if (!isPlant(entity)) {
            return false;
        }
        int index = HEntity_Plant_Stuff_Index_Enum.CAN_REPRODUCE.getIndex();
        Object[] stuff = entity.getStuff();
        if (stuff.length <= index) {
            log.error("Checking canReproduce, but stuff length is {}, expected at least {}", stuff.length, index + 1);
            return false;
        }
        return Boolean.TRUE.equals(stuff[index]);
    }

    public static boolean isDeadPlant(HEntity entity) {
    	if(!isPlant(entity)) {
    		log.error("Checking if dead plant, but it is not even a plant !");
    		return true;
    	}
        int index = HEntity_Plant_Stuff_Index_Enum.IS_DEAD.getIndex();
        Object[] stuff = entity.getStuff();
        if(stuff.length <= index) {
        	log.error("Checking if dead plant, but stuff length is {}, expected at least {}", stuff.length, index + 1);
        	return true;
        }
        return Boolean.TRUE.equals(stuff[index]);
    }
}
