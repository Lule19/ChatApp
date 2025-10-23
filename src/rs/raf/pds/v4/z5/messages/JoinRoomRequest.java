package rs.raf.pds.v4.z5.messages;

/**
 * JoinRoomRequest:
 * - RPC zahtev da korisnik (pošiljalac) pristupi sobi roomName.
 * - Server koristi Connection->username mapu, pa userName nije potreban u poruci.
 */
public class JoinRoomRequest {
    String roomName;

    protected JoinRoomRequest() {}

    public JoinRoomRequest(String roomName) {
        this.roomName = roomName;
    }

    public String getRoomName() {
        return roomName;
    }
}