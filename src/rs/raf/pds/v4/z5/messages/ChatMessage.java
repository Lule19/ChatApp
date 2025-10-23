package rs.raf.pds.v4.z5.messages;

public class ChatMessage {
    String user;      
    String txt;      
    String[] toUsers; 

    protected ChatMessage() {
    }

    public ChatMessage(String user, String txt) {
        this(user, txt, null);
    }

    public ChatMessage(String user, String txt, String[] toUsers) {
        this.user = user;
        this.txt = txt;
        this.toUsers = toUsers;
    }

    public String getUser() {
        return user;
    }

    public String getTxt() {
        return txt;
    }

    public String[] getToUsers() {
        return toUsers;
    }

    public boolean isBroadcast() {
        return toUsers == null || toUsers.length == 0;
    }

    public boolean isDirect() {
        return toUsers != null && toUsers.length == 1;
    }

    public boolean isMulticast() {
        return toUsers != null && toUsers.length > 1;
    }
}