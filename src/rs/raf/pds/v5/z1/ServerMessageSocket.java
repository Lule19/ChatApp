package rs.raf.pds.v5.z1;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.*;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import org.fusesource.jansi.AnsiConsole;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerMessageSocket {

    // Svi aktivni klijenti
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java rs.raf.pds.v5.z1.ServerMessageSocket <port>");
            System.exit(1);
        }

        int portNumber = Integer.parseInt(args[0]);
        AnsiConsole.systemInstall();
        System.out.println(ansi().fg(RED).a("Server osluskuje port: " + portNumber).reset());

        new ServerMessageSocket().run(portNumber);
    }

    private void run(int portNumber) {
        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, this);
                clients.add(handler);
                new Thread(handler, "client-" + clientSocket.getPort()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Exception caught when trying to listen on port " + portNumber + " or accepting connections");
            System.out.println(e.getMessage());
        }
    }

    // Broadcast poruke svim klijentima (uključujući pošiljaoca)
    void broadcast(Message m) {
        for (ClientHandler ch : clients) {
            try {
                ch.send(m);
            } catch (IOException e) {
                // ako ne može da se pošalje, zatvori klijenta
                ch.closeQuietly();
                clients.remove(ch);
            }
        }
    }

    // Uklanjanje klijenta kada se zatvori konekcija
    void remove(ClientHandler ch) {
        clients.remove(ch);
    }

    // ================= ClientHandler =================
    static class ClientHandler implements Runnable {
        private final Socket socket;
        private final ServerMessageSocket server;
        private final Kryo kryo = new Kryo();
        private Input in;
        private Output out;
        private volatile String username;

        ClientHandler(Socket socket, ServerMessageSocket server) {
            this.socket = socket;
            this.server = server;
            KryoUtil.registerKryoClasses(kryo);
        }

        @Override
        public void run() {
            try {
                in = new Input(socket.getInputStream());
                out = new Output(socket.getOutputStream());

                // Handshake: očekujemo Auth kao prvu poruku
                Auth auth = kryo.readObject(in, Auth.class);
                username = auth.username;
                System.out.println("Username: " + username);

                // Petlja čitanja Message poruka
                boolean running = true;
                while (running) {
                    Message m = kryo.readObject(in, Message.class);
                    // Server ispis (opciono u boji)
                    System.out.println(ansi().fg(RED).a(m.tipPoruke + ", Message #" + m.messageId + ":").reset());
                    System.out.println(m.userName + ": " + m.poruka);

                    // Broadcast poruke svima
                    server.broadcast(m);

                    // Ako klijent šalje "Bye", očekuj zatvaranje konekcije sa njegove strane
                    if (m.poruka != null && "Bye".equalsIgnoreCase(m.poruka.trim())) {
                        running = false;
                    }
                }
            } catch (Exception e) {
                // konekcija zatvorena ili desila se greška; tih izlaz
            } finally {
                closeQuietly();
                server.remove(this);
            }
        }

        // Slanje poruke klijentu
        synchronized void send(Message m) throws IOException {
            kryo.writeObject(out, m);
            out.flush();
        }

        void closeQuietly() {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
}