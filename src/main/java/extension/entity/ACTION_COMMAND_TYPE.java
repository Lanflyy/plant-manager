package extension.entity;

import extension.i18n.MessageService;
import lombok.Getter;

@Getter
public enum ACTION_COMMAND_TYPE {
    TREAT("action.treat"),
    COMPOST("action.compost"),
    SEED("action.seed"),
    CAN_REPRODUCE_ON("action.can.reproduce.on"),
    CAN_REPRODUCE_OFF("action.can.reproduce.off"),
    ABORT("action.abort");

    private final String messageKey;

    ACTION_COMMAND_TYPE(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getVerb() {
        return MessageService.getValue(messageKey);
    }
}