package rs.raf.pds.v4.z5.messages;

/**
 * RoomMessage:
 * - Poruka poslata u okviru chat sobe.
 * - Polja: roomName, user (pošiljalac), txt (sadržaj).
 */
public class RoomMessage {
    String roomName;
    String user;
    String txt;

    protected RoomMessage() {}

    public RoomMessage(String roomName, String user, String txt) {
        this.roomName = roomName;
        this.user = user;
        this.txt = txt;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getUser() {
        return user;
    }

    public String getTxt() {
        return txt;
    }
}