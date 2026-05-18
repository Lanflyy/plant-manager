package extension.entity;

import lombok.Getter;

@Getter
public enum ACTION_COMMAND_TYPE {
    TREAT("treated"),
    COMPOST("composted"),
    ABORT("aborted"),
    SEED("seeded");

    private final String verb;

    ACTION_COMMAND_TYPE(String verb) {
        this.verb = verb;
    }
}