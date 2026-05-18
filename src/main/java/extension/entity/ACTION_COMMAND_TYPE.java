package extension.entity;

import lombok.Getter;

@Getter
public enum ACTION_COMMAND_TYPE {
    TREAT("treated"),
    COMPOST("composted"),
    ABORT("aborted"),
    SEED("seeded"),
    CAN_REPRODUCE_ON("set to can-reproduce on"),
    CAN_REPRODUCE_OFF("set to can-reproduce off");

    private final String verb;

    ACTION_COMMAND_TYPE(String verb) {
        this.verb = verb;
    }
}