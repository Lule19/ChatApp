package rs.raf.pds.v4.z5.messages;

/**
 * ListRooms:
 * - Odgovor servera na ListRoomsRequest. Sadrži listu naziva soba.
 */
public class ListRooms {
    String[] rooms;

    protected ListRooms() {}

    public ListRooms(String[] rooms) {
        this.rooms = rooms;
    }

    public String[] getRooms() {
        return rooms;
    }
}