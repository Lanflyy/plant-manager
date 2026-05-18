package extension.entity;

public enum HEntity_Plant_Stuff_Index_Enum {
    PET_TYPE(0),
    OWNER_ID(1),
    OWNER_USERNAME(2),
    RARITY_LEVEL(3),
    UNKNOWN_4(4),
    UNKNOWN_5(5),
    UNKNOWN_6(6),
    UNKNOWN_7(7),
    IS_DEAD(8),
    CAN_REPRODUCE(9),
    PET_CURRENT_LEVEL(10),
    UNKNOWN_11(11);

    private final int index;
    HEntity_Plant_Stuff_Index_Enum(int index) {
        this.index = index;
    }
    public int getIndex() {
        return index;
    }

}
