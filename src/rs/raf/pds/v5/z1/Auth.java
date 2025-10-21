package rs.raf.pds.v5.z1;

import java.io.Serializable;

public class Auth implements Serializable {
    private static final long serialVersionUID = 1L;
    public String username;

    public Auth() { }
    public Auth(String username) {
        this.username = username;
    }
}