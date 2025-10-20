package rs.raf.pds.v5.z1;

import java.io.Serializable;

public class Message implements Serializable{
	
	private static final long serialVersionUID = -6876585313096075649L;
	
	Integer messageId;
	String tipPoruke;
	String userName;
	String poruka;
	
	private Message() { }
	
	public Message(int messageId, String userName) {
		this.messageId = messageId;
		this.userName = userName;
	}
	
}
