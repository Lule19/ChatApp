package rs.raf.pds.v4.z5.messages;

/**
 * CreateRoom:
 * - Zahtev za kreiranje sobe.
 * - Server može automatski da doda kreatora kao člana sobe.
 */
public class CreateRoom {
    String roomName;
    String requestedBy;

    protected CreateRoom() {}

    public CreateRoom(String roomName, String requestedBy) {
        this.roomName = roomName;
        this.requestedBy = requestedBy;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getRequestedBy() {
        return requestedBy;
    }
}