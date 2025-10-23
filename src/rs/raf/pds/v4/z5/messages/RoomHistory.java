package rs.raf.pds.v4.z5.messages;

/**
 * RoomHistory:
 * - Odgovor servera prilikom /room.join.
 * - Vraća poslednjih N (tipično 10) poruka iz sobe.
 */
public class RoomHistory {
    String roomName;
    RoomMessage[] messages;

    protected RoomHistory() {}

    public RoomHistory(String roomName, RoomMessage[] messages) {
        this.roomName = roomName;
        this.messages = messages;
    }

    public String getRoomName() {
        return roomName;
    }

    public RoomMessage[] getMessages() {
        return messages;
    }
}