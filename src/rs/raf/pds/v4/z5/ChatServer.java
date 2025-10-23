package rs.raf.pds.v4.z5;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import rs.raf.pds.v4.z5.messages.ChatMessage;
import rs.raf.pds.v4.z5.messages.CreateRoom;
import rs.raf.pds.v4.z5.messages.InfoMessage;
import rs.raf.pds.v4.z5.messages.InviteToRoom;
import rs.raf.pds.v4.z5.messages.JoinRoomRequest;
import rs.raf.pds.v4.z5.messages.KryoUtil;
import rs.raf.pds.v4.z5.messages.ListRooms;
import rs.raf.pds.v4.z5.messages.ListRoomRequest;
import rs.raf.pds.v4.z5.messages.ListUsers;
import rs.raf.pds.v4.z5.messages.Login;
import rs.raf.pds.v4.z5.messages.RoomHistory;
import rs.raf.pds.v4.z5.messages.RoomMessage;
import rs.raf.pds.v4.z5.messages.WhoRequest;

public class ChatServer implements Runnable{

    private volatile Thread thread = null;

    volatile boolean running = false;
    final Server server;
    final int portNumber;

    ConcurrentMap<String, Connection> userConnectionMap = new ConcurrentHashMap<String, Connection>();
    ConcurrentMap<Connection, String> connectionUserMap = new ConcurrentHashMap<Connection, String>();

    
    private static final int HISTORY_LIMIT = 10_000;
    private final List<ChatMessage> history = Collections.synchronizedList(new ArrayList<>());

    /
    private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();

   
    private static class Room {
        final String name;
        final ConcurrentMap<String, Boolean> members = new ConcurrentHashMap<>(); 
        final List<RoomMessage> history = Collections.synchronizedList(new ArrayList<>()); 
        static final int ROOM_HISTORY_LIMIT = 1000; 

        Room(String name) { this.name = name; }

        void addMember(String user) { members.put(user, Boolean.TRUE); }

        boolean isMember(String user) { return members.containsKey(user); }

        void store(RoomMessage m) {
            synchronized (history) {
                if (history.size() >= ROOM_HISTORY_LIMIT) {
                    history.remove(0);
                }
                history.add(m);
            }
        }

        RoomMessage[] lastN(int n) {
            synchronized (history) {
                int size = history.size();
                int start = Math.max(0, size - n);
                List<RoomMessage> slice = history.subList(start, size);
                return slice.toArray(new RoomMessage[0]);
            }
        }
    }

    public ChatServer(int portNumber) {
        this.server = new Server();

        this.portNumber = portNumber;
        KryoUtil.registerKryoClasses(server.getKryo());
        registerListener();
    }

    private void registerListener() {
        server.addListener(new Listener() {
            public void received (Connection connection, Object object) {
                if (object instanceof Login) {
                    Login login = (Login)object;
                    newUserLogged(login, connection);
                    connection.sendTCP(new InfoMessage("Hello " + login.getUserName()));
                    return;
                }

                if (object instanceof ChatMessage) {
                    ChatMessage chatMessage = (ChatMessage)object;
                    store(chatMessage); 
                    logChatMessage(chatMessage);
                    routeChatMessage(chatMessage, connection);
                    return;
                }

                
                if (object instanceof CreateRoom) {
                    CreateRoom cr = (CreateRoom) object;
                    String creator = connectionUserMap.get(connection);
                    String room = cr.getRoomName();
                    if (room == null || room.isBlank()) {
                        connection.sendTCP(new InfoMessage("Room name cannot be empty."));
                        return;
                    }
                    rooms.compute(room, (k, existing) -> {
                        if (existing == null) {
                            Room r = new Room(k);
                            if (creator != null) r.addMember(creator);
                            return r;
                        } else {
                            return existing;
                        }
                    });
                    connection.sendTCP(new InfoMessage("Room '" + room + "' created (or already exists). You are now a member."));
                    return;
                }

                if (object instanceof ListRoomRequest) {
                    String[] all = rooms.keySet().toArray(new String[0]);
                    connection.sendTCP(new ListRooms(all));
                    return;
                }

                
                if (object instanceof JoinRoomRequest) {
                    JoinRoomRequest jr = (JoinRoomRequest) object;
                    String user = connectionUserMap.get(connection);
                    Room r = rooms.get(jr.getRoomName());
                    if (r == null) {
                        connection.sendTCP(new InfoMessage("Room '" + jr.getRoomName() + "' does not exist."));
                        return;
                    }
                    if (user != null) r.addMember(user);
                    connection.sendTCP(new InfoMessage("Joined room '" + r.name + "'."));
                    RoomMessage[] last = r.lastN(10);
                    connection.sendTCP(new RoomHistory(r.name, last));
                    return;
                }

                
                if (object instanceof InviteToRoom) {
                    InviteToRoom inv = (InviteToRoom) object;
                    Room r = rooms.get(inv.getRoomName());
                    String inviter = connectionUserMap.get(connection);
                    if (r == null) {
                        connection.sendTCP(new InfoMessage("Room '" + inv.getRoomName() + "' does not exist."));
                        return;
                    }
                    for (String u : inv.getUsers()) {
                        Connection c = userConnectionMap.get(u);
                        if (c != null && c.isConnected()) {
                            c.sendTCP(new InfoMessage("You were invited to room '" + r.name + "' by " + inviter + ". Use /room.join " + r.name));
                        }
                    }
                    connection.sendTCP(new InfoMessage("Invitations sent for room '" + r.name + "'."));
                    return;
                }

               
                if (object instanceof RoomMessage) {
                    RoomMessage rm = (RoomMessage) object;
                    Room r = rooms.get(rm.getRoomName());
                    if (r == null) {
                        connection.sendTCP(new InfoMessage("Room '" + rm.getRoomName() + "' does not exist."));
                        return;
                    }
                    String user = connectionUserMap.get(connection);
                    if (user == null) {
                        connection.sendTCP(new InfoMessage("Unknown user."));
                        return;
                    }
                   
                    rm = new RoomMessage(r.name, user, rm.getTxt());
                    r.store(rm);

                   
                    for (String member : r.members.keySet()) {
                        Connection c = userConnectionMap.get(member);
                        if (c != null && c.isConnected()) {
                            c.sendTCP(rm);
                        }
                    }
                    return;
                }

                if (object instanceof WhoRequest) {
                    ListUsers listUsers = new ListUsers(getAllUsers());
                    connection.sendTCP(listUsers);
                    return;
                }
            }

            public void disconnected(Connection connection) {
                String user = connectionUserMap.get(connection);
                connectionUserMap.remove(connection);
                if (user != null) {
                    userConnectionMap.remove(user);
                    showTextToAll(user + " has disconnected!", connection);
                }
            }
        });
    }

   
    private void store(ChatMessage m) {
        synchronized (history) {
            if (history.size() >= HISTORY_LIMIT) {
                history.remove(0);
            }
            history.add(m);
        }
        System.out.println("[STORE] total=" + getHistorySize());
    }

    private int getHistorySize() {
        synchronized (history) {
            return history.size();
        }
    }

    String[] getAllUsers() {
        String[] users = new String[userConnectionMap.size()];
        int i=0;
        for (String user: userConnectionMap.keySet()) {
            users[i] = user;
            i++;
        }
        return users;
    }

    void newUserLogged(Login loginMessage, Connection conn) {
        userConnectionMap.put(loginMessage.getUserName(), conn);
        connectionUserMap.put(conn, loginMessage.getUserName());
        showTextToAll("User " + loginMessage.getUserName() + " has connected!", conn);
    }

    private void logChatMessage(ChatMessage m) {
        if (m.getToUsers() == null || m.getToUsers().length == 0) {
            System.out.println(m.getUser() + ": " + m.getTxt());
        } else {
            String targets = String.join(",", m.getToUsers());
            System.out.println(m.getUser() + " -> [" + targets + "]: " + m.getTxt());
        }
    }

    private void routeChatMessage(ChatMessage message, Connection senderConn) {
        if (message.getToUsers() == null || message.getToUsers().length == 0) {
            broadcastChatMessage(message, senderConn); // everyone except sender
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String target : message.getToUsers()) {
            Connection c = userConnectionMap.get(target);
            if (c != null && c.isConnected()) {
                c.sendTCP(message);
            } else {
                missing.add(target);
            }
        }
        if (senderConn != null && senderConn.isConnected()) {
            senderConn.sendTCP(message); // echo to sender
        }
        if (!missing.isEmpty() && senderConn != null && senderConn.isConnected()) {
            senderConn.sendTCP(new InfoMessage("Users offline/unknown: " + String.join(", ", missing)));
        }
    }

    private void broadcastChatMessage(ChatMessage message, Connection exception) {
        for (Connection conn: userConnectionMap.values()) {
            if (conn.isConnected() && conn != exception)
                conn.sendTCP(message);
        }
    }

    private void showTextToAll(String txt, Connection exception) {
        System.out.println(txt);
        for (Connection conn: userConnectionMap.values()) {
            if (conn.isConnected() && conn != exception)
                conn.sendTCP(new InfoMessage(txt));
        }
    }

    public void start() throws IOException {
        server.start();
        server.bind(portNumber);

        if (thread == null) {
            thread = new Thread(this);
            thread.start();
        }
    }

    public void stop() {
        Thread stopThread = thread;
        thread = null;
        running = false;
        if (stopThread != null)
            stopThread.interrupt();
    }

    @Override
    public void run() {
        running = true;

        while(running) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Usage: java -jar chatServer.jar <port number>");
            System.out.println("Recommended port number is 54555");
            System.exit(1);
        }

        int portNumber = Integer.parseInt(args[0]);
        try {
            ChatServer chatServer = new ChatServer(portNumber);
            chatServer.start();

            chatServer.thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}