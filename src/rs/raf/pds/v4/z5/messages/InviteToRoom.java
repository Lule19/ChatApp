package rs.raf.pds.v4.z5.messages;

/**
 * InviteToRoom:
 * - RPC zahtev da se drugi korisnici pozovu u sobu.
 * - Server šalje InfoMessage pozvanim korisnicima sa instrukcijom da izvrše /room.join.
 */
public class InviteToRoom {
    String roomName;
    String invitedBy;
    String[] users;

    protected InviteToRoom() {}

    public InviteToRoom(String roomName, String invitedBy, String[] users) {
        this.roomName = roomName;
        this.invitedBy = invitedBy;
        this.users = users;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getInvitedBy() {
        return invitedBy;
    }

    public String[] getUsers() {
        return users;
    }
}