package rs.raf.pds.v4.z5;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

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

public class ChatClient implements Runnable{

    public static int DEFAULT_CLIENT_READ_BUFFER_SIZE = 1000000;
    public static int DEFAULT_CLIENT_WRITE_BUFFER_SIZE = 1000000;

    private volatile Thread thread = null;

    volatile boolean running = false;

    final Client client;
    final String hostName;
    final int portNumber;
    final String userName;

    public ChatClient(String hostName, int portNumber, String userName) {
        this.client = new Client(DEFAULT_CLIENT_WRITE_BUFFER_SIZE, DEFAULT_CLIENT_READ_BUFFER_SIZE);

        this.hostName = hostName;
        this.portNumber = portNumber;
        this.userName = userName;
        KryoUtil.registerKryoClasses(client.getKryo());
        registerListener();
    }

    private void registerListener() {
        client.addListener(new Listener() {
            public void connected (Connection connection) {
                Login loginMessage = new Login(userName);
                client.sendTCP(loginMessage);
            }

            public void received (Connection connection, Object object) {
                if (object instanceof ChatMessage) {
                    showChatMessage((ChatMessage)object);
                    return;
                }

                if (object instanceof RoomMessage) {
                    showRoomMessage((RoomMessage)object);
                    return;
                }

                if (object instanceof RoomHistory) {
                    showRoomHistory((RoomHistory)object);
                    return;
                }

                if (object instanceof ListUsers) {
                    ListUsers listUsers = (ListUsers)object;
                    showOnlineUsers(listUsers.getUsers());
                    return;
                }

                if (object instanceof ListRooms) {
                    ListRooms listRooms = (ListRooms)object;
                    showRooms(listRooms.getRooms());
                    return;
                }

                if (object instanceof InfoMessage) {
                    InfoMessage message = (InfoMessage)object;
                    showMessage("Server: " + message.getTxt());
                    return;
                }
            }

            public void disconnected(Connection connection) {
                showMessage("Server: Disconnected.");
            }
        });
    }

    private void showChatMessage(ChatMessage chatMessage) {
        String prefix;
        if (chatMessage.getToUsers() != null && chatMessage.getToUsers().length > 0) {
            String targets = String.join(",", chatMessage.getToUsers());
            prefix = chatMessage.getUser() + " -> [" + targets + "]";
        } else {
            prefix = chatMessage.getUser();
        }
        System.out.println(prefix + ": " + chatMessage.getTxt());
    }

    private void showRoomMessage(RoomMessage rm) {
        System.out.println("[" + rm.getRoomName() + "] " + rm.getUser() + ": " + rm.getTxt());
    }

    private void showRoomHistory(RoomHistory rh) {
        RoomMessage[] msgs = rh.getMessages();
        System.out.println("Server: last " + msgs.length + " messages in #" + rh.getRoomName() + ":");
        for (RoomMessage m : msgs) {
            System.out.println("[" + m.getRoomName() + "] " + m.getUser() + ": " + m.getTxt());
        }
    }

    private void showMessage(String txt) {
        System.out.println(txt);
    }

    private void showOnlineUsers(String[] users) {
        System.out.print("Server (users): ");
        for (int i=0; i<users.length; i++) {
            System.out.print(users[i]);
            System.out.printf((i==users.length-1?"\n":", "));
        }
    }

    private void showRooms(String[] rooms) {
        System.out.print("Server (rooms): ");
        if (rooms == null || rooms.length == 0) {
            System.out.println("(none)");
            return;
        }
        for (int i=0; i<rooms.length; i++) {
            System.out.print(rooms[i]);
            System.out.printf((i==rooms.length-1?"\n":", "));
        }
    }

    public void start() throws IOException {
        client.start();
        connect();

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

    public void connect() throws IOException {
        client.connect(1000, hostName, portNumber);
    }

    public void run() {
        try (BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {
            String userInput;
            running = true;

            printHelpOnce();

            while (running) {
                userInput = stdIn.readLine();
                if (userInput == null || "BYE".equalsIgnoreCase(userInput)) {
                    running = false;

                } else if ("WHO".equalsIgnoreCase(userInput)) {
                    client.sendTCP(new WhoRequest());

                } else if (userInput.startsWith("/msg ")) {
                    String rest = userInput.substring(5).trim();
                    int spaceIdx = rest.indexOf(' ');
                    if (spaceIdx <= 0) { showMessage("Usage: /msg <user> <text>"); continue; }
                    String target = rest.substring(0, spaceIdx).trim();
                    String text = rest.substring(spaceIdx + 1).trim();
                    if (text.isEmpty()) { showMessage("Usage: /msg <user> <text>"); continue; }
                    client.sendTCP(new ChatMessage(userName, text, new String[]{target}));

                } else if (userInput.startsWith("/to ")) {
                    String rest = userInput.substring(4).trim();
                    int spaceIdx = rest.indexOf(' ');
                    if (spaceIdx <= 0) { showMessage("Usage: /to <user1,user2,...> <text>"); continue; }
                    String targetsPart = rest.substring(0, spaceIdx).trim();
                    String text = rest.substring(spaceIdx + 1).trim();
                    if (text.isEmpty()) { showMessage("Usage: /to <user1,user2,...> <text>"); continue; }
                    String[] targets = Arrays.stream(targetsPart.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
                    if (targets.length == 0) { showMessage("Usage: /to <user1,user2,...> <text>"); continue; }
                    client.sendTCP(new ChatMessage(userName, text, targets));

                } else if (userInput.equalsIgnoreCase("/rooms") || userInput.equalsIgnoreCase("/room.list")) {
                    client.sendTCP(new ListRoomRequest());

                } else if (userInput.startsWith("/room.create ")) {
                    String room = userInput.substring("/room.create ".length()).trim();
                    if (room.isEmpty()) { showMessage("Usage: /room.create <room>"); continue; }
                    client.sendTCP(new CreateRoom(room, userName));

                } else if (userInput.startsWith("/room.join ")) {
                    String room = userInput.substring("/room.join ".length()).trim();
                    if (room.isEmpty()) { showMessage("Usage: /room.join <room>"); continue; }
                    client.sendTCP(new JoinRoomRequest(room));

                } else if (userInput.startsWith("/room.invite ")) {
                    String rest = userInput.substring("/room.invite ".length()).trim();
                    int spaceIdx = rest.indexOf(' ');
                    if (spaceIdx <= 0) { showMessage("Usage: /room.invite <room> <user1,user2,...>"); continue; }
                    String room = rest.substring(0, spaceIdx).trim();
                    String usersPart = rest.substring(spaceIdx + 1).trim();
                    String[] targets = Arrays.stream(usersPart.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
                    if (room.isEmpty() || targets.length == 0) { showMessage("Usage: /room.invite <room> <user1,user2,...>"); continue; }
                    client.sendTCP(new InviteToRoom(room, userName, targets));

                } else if (userInput.startsWith("@")) {
                    
                    int spaceIdx = userInput.indexOf(' ');
                    if (spaceIdx <= 1) { showMessage("Usage: @<room> <text>"); continue; }
                    String room = userInput.substring(1, spaceIdx).trim();
                    String text = userInput.substring(spaceIdx + 1).trim();
                    if (text.isEmpty()) { showMessage("Usage: @<room> <text>"); continue; }
                    client.sendTCP(new RoomMessage(room, userName, text));

                } else {
                    // broadcast
                    client.sendTCP(new ChatMessage(userName, userInput));
                }

                if (!client.isConnected() && running)
                    connect();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            running = false;
            System.out.println("CLIENT SE DISCONNECTUJE");
            client.close();
        }
    }

    private void printHelpOnce() {
        System.out.println("Commands:");
        System.out.println("  WHO                           - list online users");
        System.out.println("  BYE                           - disconnect");
        System.out.println("  <text>                        - broadcast message");
        System.out.println("  /msg <user> <text>            - direct message to user");
        System.out.println("  /to <u1,u2,...> <text>        - multicast message to listed users");
        System.out.println("  /rooms | /room.list           - list all chat rooms");
        System.out.println("  /room.create <room>           - create a chat room");
        System.out.println("  /room.join <room>             - join a chat room (receive last 10 msgs)");
        System.out.println("  /room.invite <room> <users>   - invite users to a room");
        System.out.println("  @<room> <text>                - send message to a room");
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java -jar chatClient.jar <host name> <port number> <username>");
            System.out.println("Recommended port number is 54555");
            System.exit(1);
        }

        String hostName = args[0];
        int portNumber = Integer.parseInt(args[1]);
        String userName = args[2];

        try{
            ChatClient chatClient = new ChatClient(hostName, portNumber, userName);
            chatClient.start();
        }catch(IOException e) {
            e.printStackTrace();
            System.err.println("Error:"+e.getMessage());
            System.exit(-1);
        }
    }
}