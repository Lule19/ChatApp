package rs.raf.pds.v5.z1;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ClientMessageSocket implements Runnable{
	
	private volatile Thread thread = null;
	
	Socket socket;
	volatile boolean running = false;
	final String userName;

	private final Kryo kryo = new Kryo();
	private Input kryoInput;
	private Output kryoOutput;
	
	public ClientMessageSocket(Socket socket, String userName) {
		this.socket = socket;
		this.userName = userName;
		KryoUtil.registerKryoClasses(kryo);
	}
	
	public void start() {
		if (thread == null) {
			thread = new Thread(this, "client-main");
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
		try (
		     BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))
		) {
				kryoInput = new Input(socket.getInputStream());
				kryoOutput = new Output(socket.getOutputStream());

				// Handshake: pošalji username kroz Auth paket
				kryo.writeObject(kryoOutput, new Auth(userName));
				kryoOutput.flush();

				// Reader nit: prima poruke sa servera i ispisuje ih
				Thread reader = new Thread(this::readLoop, "client-reader");
				reader.setDaemon(true);
				reader.start();

				String userInput;
				running = true;
				int counter = 0;

	            System.out.println("Kryo chat (multi-klijent). Ukucaj poruku i Enter. Za kraj: BYE");

	            while (running) {
	            	userInput = stdIn.readLine();
	            	if (userInput == null || "BYE".equalsIgnoreCase(userInput)) {
	            		userInput = "Bye";
	            		running = false;
	            	}
	            	// Sastavi Message i pošalji preko Kryo
	            	Message m = new Message(++counter, userName);
            		m.tipPoruke = "Kryo";
            		m.poruka = userInput;
            		synchronized (this) {
						kryo.writeObject(kryoOutput, m);
						kryoOutput.flush();
					}
	            }
	            
	    } catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				running = false;
				if (kryoOutput != null) kryoOutput.close();
				if (kryoInput != null) kryoInput.close();
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void readLoop() {
		try {
			while (true) {
				Message m = kryo.readObject(kryoInput, Message.class);
				// Ispiši svaku poruku dobijenu od servera (broadcast)
				System.out.println("[" + m.messageId + "] " + m.userName + ": " + m.poruka);
			}
		} catch (Exception e) {
			// konekcija zatvorena
		}
	}

	public static void main(String[] args) {
		if (args.length != 3) {
            System.err.println(
                "Usage: java rs.raf.pds.v5.z1.ClientMessageSocket <host> <port> <username>");
            System.exit(1);
        }
 
        String hostName = args[0];
        int portNumber = Integer.parseInt(args[1]);
        String userName = args[2];
        
        try{
             Socket socket = new Socket(hostName, portNumber);
        	
             ClientMessageSocket client = new ClientMessageSocket(socket, userName);
        	 client.start();
        	 
        	 client.thread.join();
         } catch (IOException e) {
           System.err.println("Couldn't get I/O for the connection to " +
                    hostName);
                
         } catch (InterruptedException e) {
			e.printStackTrace();
		} 
        System.exit(0);
	}
}