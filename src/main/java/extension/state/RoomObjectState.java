package extension.state;

import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HFloorItem;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RoomObjectState {

    private final Map<Integer, HFloorItem> floorItems = new ConcurrentHashMap<>();

    public RoomObjectState(ExtensionForm extension) {
        extension.intercept(HMessage.Direction.TOCLIENT, "Objects", this::handleObjects);
        extension.intercept(HMessage.Direction.TOCLIENT, "ObjectAdd", this::handleObjectAdd);
        extension.intercept(HMessage.Direction.TOCLIENT, "ObjectRemove", this::handleObjectRemove);
        extension.intercept(HMessage.Direction.TOSERVER, "GetGuestRoom", this::handleGetGuestRoom);
        extension.intercept(HMessage.Direction.TOSERVER, "Quit", this::handleQuit);
    }

    public List<HFloorItem> getFloorItemsSnapshot() {
        return new ArrayList<>(floorItems.values());
    }

    public void clearFloorItems() {
        floorItems.clear();
    }

    private void handleObjects(HMessage hMessage) {
        try {
            HFloorItem[] items = HFloorItem.parse(hMessage.getPacket());
            for (HFloorItem item : items) {
                floorItems.put(item.getId(), item);
            }
            log.debug("[Objects] Parsed {} items", items.length);
        } catch (Exception e) {
            log.debug("[Objects] Failed to parse room objects", e);
        }
    }

    private void handleObjectAdd(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            HFloorItem item = new HFloorItem(packet);
            floorItems.put(item.getId(), item);
        } catch (Exception e) {
            log.debug("[ObjectAdd] Failed to parse added object", e);
        }
    }

    private void handleObjectRemove(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            int id = Integer.parseInt(packet.readString());
            floorItems.remove(id);
        } catch (Exception e) {
            log.debug("[ObjectRemove] Failed to parse removed object", e);
        }
    }

    private void handleGetGuestRoom(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        packet.readInteger(); // roomId
        int requestType = packet.readInteger();

        if (requestType == 0) {
            clearFloorItems();
        }
    }

    private void handleQuit(HMessage hMessage) {
        clearFloorItems();
    }
}
