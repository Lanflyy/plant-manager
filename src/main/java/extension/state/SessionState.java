package extension.state;

import gearth.extensions.ExtensionForm;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SessionState {

    @Getter
    private volatile int currentUserId = -1;
    @Getter
    private volatile String currentUsername = null;
    @Getter
    @Setter
    private volatile int currentRoomId = -1;

    public SessionState(ExtensionForm extension) {
        extension.intercept(HMessage.Direction.TOCLIENT, "UserObject", this::handleUserObject);
        extension.intercept(HMessage.Direction.TOSERVER, "GetGuestRoom", this::handleGetGuestRoom);
    }

    private void handleUserObject(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            currentUserId = packet.readInteger();
            currentUsername = packet.readString();
            log.debug("[UserObject] Current user: {} ({})", currentUsername, currentUserId);
        } catch (Exception e) {
            log.debug("[UserObject] Failed to parse user object", e);
        }
    }

    private void handleGetGuestRoom(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        int roomId = packet.readInteger();
        int requestType = packet.readInteger();

        if (requestType == 0) {
            setCurrentRoomId(roomId);
            log.debug("[GetGuestRoom] Current room set to {}", roomId);
        }
    }
}
