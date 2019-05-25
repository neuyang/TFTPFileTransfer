/**
 * ErrorSim is the intermediate host between the TFTP server and client.
 */
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.apache.commons.cli.*;

/**
 * Errors class stores all errors currently pending creation
 */
class Errors {
	private LinkedList<ErrorInstruction> errors = new LinkedList<ErrorInstruction>();
	
	/**
	 * Adds a new ErrorInstruction to the error simulators already pending errors
	 * @param error the new error to add
	 * @return true if the error was added, false if it already exists
	 */
	public synchronized boolean add(ErrorInstruction error) {
		
		//Don't allow adding duplicate commands
		for(ErrorInstruction ei : errors) {
			if(ei.equals(error)) {
				return false;
			}
		}
		
		errors.add(error);
		return true;
	}
	
	/**
	 * Removes an error from the pending errors
	 * @param error the error to remove
	 * @return true if the error was removed, false if it was not found
	 */
	public synchronized boolean remove(ErrorInstruction error) {
		return errors.remove(error);
	}
	
	/**
	 * Checks a packet to see if any of the pending errors are applicable to it
	 * @param packet the packet to check
	 * @return null if no errors apply to the packet, or the ErrorInstruction that does
	 */
	public synchronized ErrorInstruction checkPacket(DatagramPacket packet) {
		if(errors.size() == 0) {
			return null;
		}
		
		TFTPPacket parsedPacket = TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength()));
		
		int i;
		for(i = 0; i < errors.size(); i++) {
			ErrorInstruction ei = errors.get(i);
			
			if(ei.packetType == ErrorInstruction.packetTypes.RRQ && parsedPacket instanceof TFTPPacket.RRQ) {
				if(ei.skipped == ei.packetNumber) {
					ei.occurances++;
					if(ei.occurances == ei.timesToPerform) {
						errors.remove(i);
					}else {
						ei.skipped = 0;
					}
					return ei;
				}
				ei.skipped++;
			}
			else if(ei.packetType == ErrorInstruction.packetTypes.WRQ && parsedPacket instanceof TFTPPacket.WRQ) {
				if(ei.skipped == ei.packetNumber) {
					ei.occurances++;
					if(ei.occurances == ei.timesToPerform) {
						errors.remove(i);
					}else {
						ei.skipped = 0;
					}
					return ei;
				}
				ei.skipped++;
			}
			else if(ei.packetType == ErrorInstruction.packetTypes.DATA && parsedPacket instanceof TFTPPacket.DATA) {
				TFTPPacket.DATA data = (TFTPPacket.DATA)parsedPacket; 
				if(data.getBlockNum() == ei.packetNumber) {
					ei.occurances++;
					if(ei.occurances == ei.timesToPerform) {
						errors.remove(i);
					}else {
						ei.skipped = 0;
					}
					return ei;
				}
				ei.skipped++;
			}
			else if(ei.packetType == ErrorInstruction.packetTypes.ACK && parsedPacket instanceof TFTPPacket.ACK) {
				TFTPPacket.ACK ack = (TFTPPacket.ACK)parsedPacket; 
				if(ack.getBlockNum() == ei.packetNumber) {
					ei.occurances++;
					if(ei.occurances == ei.timesToPerform) {
						errors.remove(i);
					}else {
						ei.skipped = 0;
					}
					return ei;
				}
				ei.skipped++;
			}
			else if(ei.packetType == ErrorInstruction.packetTypes.ERROR && parsedPacket instanceof TFTPPacket.ERROR) {
				if(ei.skipped == ei.packetNumber) {
					ei.occurances++;
					if(ei.occurances == ei.timesToPerform) {
						errors.remove(i);
					}else {
						ei.skipped = 0;
					}
					return ei;
				}
				ei.skipped++;
			}
		}
		return null;
	}
	
	/**
	 * Provides a list of all pending errors
	 */
	public String toString() {
		
		if(errors.size() == 0) {
			return "No errors pending creation.";
		}
		String desc = "";
		
		desc += "The following errors will created:\n";
		
		int i = 1;
		for(ErrorInstruction ei : errors) {
			desc += i + ". " + ei.toString() + "\n";
			i++;
		}
		return desc;
	}
}

/**
 * ErrorInstruction class represents an network error that the error simulator should simulate
 */
class ErrorInstruction {
	
	//All possible packet types that an error can be applied to
	enum packetTypes{
		RRQ, WRQ, DATA, ACK, ERROR
	}
	
	//All possible error types that can be created
	enum errorTypes{
		DROP, DUPLICATE, DELAY
	}
	
	packetTypes packetType;
	errorTypes errorType;
	int packetNumber;
	int delay;
	int timesToPerform;
	int occurances;
	int skipped = 0;
	
	/**
	 * Constructor for ErrorInstruction
	 * @param packetType the type of packet this error applies to
	 * @param errorType the type of error that should occur
	 * @param packetNumber the block number of the packet (for DATA and ACK) or the number of packets to skip before causing error (RRQ, WRQ, ERROR)
	 * @param delay how much delay to add (for DELAY and DUPLICATE only)
	 * @param timesToPerform how many times to create this error (negative is infinite)
	 */
	ErrorInstruction(packetTypes packetType, errorTypes errorType, int packetNumber, int delay, int timesToPerform)
	{
		if(packetNumber < 0) {
			throw new IllegalArgumentException("packet number can't be less than 0");
		}
		if(delay < 0) {
			throw new IllegalArgumentException("delay can't be less than 0");
		}
		if(timesToPerform == 0 ) {
			throw new IllegalArgumentException("cant perform error 0 times");
		}
		
		this.packetType = packetType;
		this.errorType = errorType;
		this.packetNumber = packetNumber;
		this.delay = delay;
		this.timesToPerform = timesToPerform;
	}
	
	/**
	 * Checks if two ErrorInstructions are equivalent
	 */
	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}
		
		if(!(o instanceof ErrorInstruction)){
			return false;
		}
		
		ErrorInstruction error = (ErrorInstruction)o;
		
		if(this.packetType != error.packetType ||
		   this.errorType != error.errorType) {
			return false;
		}
		return this.packetNumber == error.packetNumber &&
				this.delay == error.delay &&
				this.timesToPerform == error.timesToPerform &&
				this.occurances == error.occurances;
	}
	
	/**
	 * Provides a string representation of an ErrorInstruction
	 */
	public String toString() {
		String desc = "";
		
		switch(errorType) {
		case DROP:
			desc += "Drop ";
			break;
		case DUPLICATE:
			desc += "Duplicate ";
			break;
		case DELAY:
			desc += "Delay ";
			break;
		}
		
		switch(packetType) {
		case RRQ:
			desc += "RRQ packet ";
			break;
		case WRQ:
			desc += "WRQ packet ";
			break;
		case DATA:
			desc += "DATA packet ";
			break;
		case ACK:
			desc += "ACK packet ";
			break;
		case ERROR:
			desc += "ERROR packet ";
			break;
		}
		
		desc += packetNumber;
		
		if(errorType == errorTypes.DELAY) {
			desc += " by " + delay + " ms";
		}
		else if(errorType == errorTypes.DUPLICATE) {
			desc += " with " + delay + " ms between packets";
		}
		
		if(timesToPerform < 0) {
			desc += ". Repeat forever.";
		}
		else {
			desc += ". Perform " + timesToPerform + " time(s), " + (timesToPerform - occurances) + " remaining.";
		}
		return desc;
	}
	
	/**
	 * Converts a string into an ErrorType
	 * @param typeString a string representing the error type
	 * @return the errorType enum corresponding to the string
	 */
	public static errorTypes getErrorType(String typeString) {
		switch(typeString.toLowerCase()) {
		case "drop":
			return errorTypes.DROP;
		case "duplicate":
			return errorTypes.DUPLICATE;
		case "delay":
			return errorTypes.DELAY;
		default:
			return null;
		}
	}
	
	/**
	 * Converts a string into a PacketType
	 * @param typeString a string representing a packet type
	 * @return the packetType enum corresponding to the string
	 */
	public static packetTypes getPacketType(String typeString) {
		switch(typeString.toLowerCase()) {
		case "rrq":
			return packetTypes.RRQ;
		case "wrq":
			return packetTypes.WRQ;
		case "data":
			return packetTypes.DATA;
		case "ack":
			return packetTypes.ACK;
		case "error":
			return packetTypes.ERROR;
		default:
			return null;
		}
	}
}

/**
 * Handles all communications to and from the client
 */
class ErrorSimClientListener{
	private ErrorSim errorSim;
	private DatagramSocket knownSocket;
	private DatagramSocket TIDSocket;
	private InetAddress clientAddress;
    private int clientPort;
    boolean verbose;
    Timer timer;
    SocketListener knownPortListener;
    SocketListener TIDPortListener;
    Thread knownPortListenerThread;
    Thread TIDPortListenerThread;
	
    /**
     * Constructor for ErrorSimClientListener
     * @param port the port to listen to client on
     * @param verbose true means debug info will be shown, false is basic output
     * @param errorSim the instance of errorSim using this class
     */
	public ErrorSimClientListener(int port, boolean verbose, ErrorSim errorSim) {
		this.clientPort = port;
		this.verbose = verbose;
		this.errorSim = errorSim;
		
		try { //Set up the socket that will be used to receive packets from client on known port
			knownSocket = new DatagramSocket(port);
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
		
		try { //Set up the socket that will be used to communicate with TID port
			TIDSocket = new DatagramSocket();
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
		
		timer = new Timer();
		knownPortListener = new SocketListener(knownSocket);
		TIDPortListener = new SocketListener(TIDSocket);
		knownPortListenerThread = new Thread(knownPortListener);
		TIDPortListenerThread = new Thread(TIDPortListener);
	}
	
	/**
	 * Sends a packet to the client and applies any applicable pending errors to it
	 * @param packet the packet to send
	 */
	public synchronized void sendToClient(DatagramPacket packet) {
		ErrorInstruction ei = errorSim.errors.checkPacket(packet);
		if(ei != null) {
			System.out.println("Applying the following error before sending packet to client:");
			System.out.println(ei);
		
			if(ei.errorType == ErrorInstruction.errorTypes.DUPLICATE) {
				//Send the packet now, and its duplicate later
				timer.schedule(new DelayedSendToClient(packet.getData(), packet.getLength()), 0);
				timer.schedule(new DelayedSendToClient(packet.getData(), packet.getLength()), ei.delay);
			}
			else if(ei.errorType == ErrorInstruction.errorTypes.DELAY) {
				//Send the packet later
				timer.schedule(new DelayedSendToClient(packet.getData(), packet.getLength()), ei.delay);
			}
		}
		else {
			//Send the packet to the client without introducing any errors
			timer.schedule(new DelayedSendToClient(packet.getData(), packet.getLength()), 0);
		}
	}

	/**
	 * Makes ErrorSimClientListener start listening to the client
	 */
	public void start() {
		knownPortListenerThread.start();
		TIDPortListenerThread.start();
	}
	
	/**
	 * Gets the port that this listens to for new requests from the client
	 * @return the port number
	 */
	public int getClientKnownPort() {
		return clientPort;
	}
	
	/**
	 * sets the known port that this listens to for new requests from the client
	 * @param port the port number
	 */
	public synchronized void setClientKnownPort(int port) {
		knownSocket.close();
		clientPort = port;
		try { //Set up the socket that will be used to receive packets from client on known port
			knownSocket = new DatagramSocket(port);
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
		
		knownPortListener = new SocketListener(knownSocket);
		knownPortListenerThread = new Thread(knownPortListener);
		knownPortListenerThread.start();
	}
	
	/**
	 * stops the ErrorSimClientListener
	 */
	public void close()
	{
		knownSocket.close();
	    TIDSocket.close();
	}
	
	/**
	 * Sets the TID port
	 * @param port the port number
	 */
	private synchronized void setClientPort(int port){
		clientPort = port;
	}
	
	/**
	 * Sets the IP address of the client
	 * @param address the IP address
	 */
	private synchronized void setClientAddress(InetAddress address){
		clientAddress = address;
	}
	
	/**
	 * Waits to receive a packet from the client
	 * @param socket the socket to listen to
	 * @return a packet if one was received or null if the socket was closed
	 */
	private DatagramPacket receiveFromClient(DatagramSocket socket) {

		byte data[] = new byte[TFTPPacket.MAX_SIZE];
	    DatagramPacket packet = new DatagramPacket(data, data.length);
	    
    	try { //Wait for a packet to come in from the client.
    		socket.receive(packet);
    	} catch(IOException e) {
    		if(e.getMessage().toLowerCase().equals("socket closed")){
    			return null;
    		}
    		e.printStackTrace();
			System.exit(1);
    	}
    	
    	if(verbose) {
    		System.out.println("Received packet from client.");
    	    System.out.println("From address: " + packet.getAddress());
    	    System.out.println("From port: " + packet.getPort());
    	    System.out.println("Length: " + packet.getLength());
    	    TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength())).print();
    	    System.out.print("\n");
    	}
    	
    	return packet;
	}
	
	/**
	 * Listens to a socket on a new thread so the ErrorSimClientListener can listen to multiple sockets at the same time
	 */
	private class SocketListener implements Runnable{
		private DatagramSocket socket;
		
		/**
		 * Constructor for SocketListener
		 * @param socket the socket to listen to
		 */
		public SocketListener(DatagramSocket socket) {
			this.socket = socket;
		}
		
		/**
		 * The overridden run method for this thread
		 */
		public void run() {
			DatagramPacket receivePacket;
		    
			while(true) {	
				receivePacket = receiveFromClient(socket);
	    	
				if(receivePacket == null) {
					return;
				}
				setClientAddress(receivePacket.getAddress());
				setClientPort(receivePacket.getPort());	
				errorSim.serverListener.sendToServer(receivePacket);
			}
		}
	}

	/**
	 * DelayedSendToClient class allows a packet to be sent at some time in the future
	 */
	private class DelayedSendToClient extends TimerTask{
		byte data[];
		int length;

		/**
		 * Creates a new task that will send a packet
		 * @param data the data to send
		 * @param length the length of the data
		 */
		public DelayedSendToClient(byte data[], int length) {
			this.data = data;
			this.length = length;
		}
		
		/**
		 * The overridden run method for this thread
		 */
		public synchronized void run() {
			
			DatagramPacket packet = new DatagramPacket(data, length, clientAddress, clientPort);
			
			if(verbose) {
	    		System.out.println("Sending packet to client.");
	    	    System.out.println("To address: " + packet.getAddress());
	    	    System.out.println("To port: " + packet.getPort());
	    	    System.out.println("Length: " + packet.getLength());
	    	    TFTPPacket.parse(Arrays.copyOf(data, length)).print();
	    	    System.out.print("\n");
	    	}
			
			try { //Send the packet to the client
	    		TIDSocket.send(packet);
	    	} catch (IOException e) {
	    		if(e.getMessage().equals("socket closed")){
	    			return;
	    		}
	    		e.printStackTrace();
				System.exit(1);
	    	}
			this.cancel();
		}	
	}
}

/**
 * Handles all communication to and from the server
 */
class ErrorSimServerListener implements Runnable {
	private ErrorSim errorSim;
	private DatagramSocket socket;
	private InetAddress serverAddress;
    private int serverPort;
    private int serverTID;
    boolean verbose;
    Timer timer;
	
    /**
     * Constructor for ErrorSimServerListener
     * @param port the servers known port
     * @param address the servers IP address
     * @param verbose true prints extra debug info, false prints only basic info
     * @param errorSim the instance of ErrorSim using this class
     */
	public ErrorSimServerListener(int port, InetAddress address, boolean verbose, ErrorSim errorSim) {
		serverPort = port;
		serverAddress = address;
		this.verbose = verbose;
		this.errorSim = errorSim;
		
		try { //Set up the socket that will be used to communicate with the server
			socket = new DatagramSocket();
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
		
		timer = new Timer();
	}
	
	/**
	 * Sends a packet to the server
	 * @param packet the packet to send
	 */
	public synchronized void sendToServer(DatagramPacket packet) {
		ErrorInstruction ei = errorSim.errors.checkPacket(packet);
		if(ei != null) {
			System.out.println("Applying the following error before sending packet to server:");
			System.out.println(ei);
		
			if(ei.errorType == ErrorInstruction.errorTypes.DUPLICATE) {
				//Send the packet now, and its duplicate later
				timer.schedule(new DelayedSendToServer(packet.getData(), packet.getLength()), 0);
				timer.schedule(new DelayedSendToServer(packet.getData(), packet.getLength()), ei.delay);
			}
			else if(ei.errorType == ErrorInstruction.errorTypes.DELAY) {
				//Send the packet later
				timer.schedule(new DelayedSendToServer(packet.getData(), packet.getLength()), ei.delay);
			}
		}
		else {
			//Send the packet to the client without introducing any errors
			timer.schedule(new DelayedSendToServer(packet.getData(), packet.getLength()), 0);
		}
	}
	
	/**
	 * Gets the servers known port
	 * @return the port number
	 */
	public int getServerKnownPort() {
		return serverPort;
	}
	
	/**
	 * Gets the servers IP address
	 * @return the IP address
	 */
	public InetAddress getServerAddress() {
		return serverAddress;
	}
	
	/**
	 * Closes the ErrorSimServerListener
	 */
	public void close()
	{
		socket.close();
	}

	/**
	 * The overridden run method for this thread
	 */
	public void run() {
	    DatagramPacket receivePacket;
		
	    while(true){
	    	receivePacket = receiveFromServer();
    	
	    	if(receivePacket == null) {
	    		return;
	    	}
	    	else {
	    		setServerTID(receivePacket.getPort());
	    	}
	    	errorSim.clientListener.sendToClient(receivePacket);
	    }
	}
	
	/**
	 * sets the TID used to communicate with the server
	 * @param port the port number
	 */
	private synchronized void setServerTID(int port){
		serverTID = port;
	}
	
	/**
	 * Waits to receive a packet from the server
	 * @return the packet if one was received, or null if the socket was closed
	 */
	private DatagramPacket receiveFromServer() {

		byte data[] = new byte[TFTPPacket.MAX_SIZE];
	    DatagramPacket packet = new DatagramPacket(data, data.length);
	    
    	try { //Wait for a packet to come in from the client.
    		socket.receive(packet);
    	} catch(IOException e) {
    		if(e.getMessage().toLowerCase().equals("socket closed")){
    			return null;
    		}
    		e.printStackTrace();
			System.exit(1);
    	}
    	
    	if(verbose) {
    		System.out.println("Received packet from server.");
    	    System.out.println("From address: " + packet.getAddress());
    	    System.out.println("From port: " + packet.getPort());
    	    System.out.println("Length: " + packet.getLength());
    	    TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength())).print();
    	    System.out.print("\n");
    	}
    	
    	return packet;
	}
	
	/**
	 * DelayedSendToServer class allows a packet to be sent at some time in the future
	 */
	private class DelayedSendToServer extends TimerTask{
		byte data[];
		int length;

		/**
		 * Constructor for DelayedSendToServer
		 * @param data the data to send
		 * @param length the length of the data
		 */
		public DelayedSendToServer(byte data[], int length) {
			this.data = data;
			this.length = length;
		}
		
		/**
		 * The overridden run method
		 */
		public synchronized void run() {
			DatagramPacket packet;
			TFTPPacket parsedPacket = TFTPPacket.parse(Arrays.copyOf(data, length));
			
			if(parsedPacket instanceof TFTPPacket.WRQ || parsedPacket instanceof TFTPPacket.RRQ) {
				packet = new DatagramPacket(data, length, serverAddress, serverPort);
			}
			else {
				packet = new DatagramPacket(data, length, serverAddress, serverTID);
			}
			
			if(verbose) {
	    		System.out.println("Sending packet to server.");
	    	    System.out.println("To address: " + packet.getAddress());
	    	    System.out.println("To port: " + packet.getPort());
	    	    System.out.println("Length: " + packet.getLength());
	    	    TFTPPacket.parse(Arrays.copyOf(data, length)).print();
	    	    System.out.print("\n");
	    	}
			
			try { //Send the packet to the client
	    		socket.send(packet);
	    	} catch (IOException e) {
	    		if(e.getMessage().equals("socket closed")){
	    			return;
	    		}
	    		e.printStackTrace();
				System.exit(1);
	    	}
			this.cancel();
		}	
	}
}

/**
 * ErrorSim class handles the setup of the error simulator and acts as the UI thread.
 */
public class ErrorSim {
	public ErrorSimClientListener clientListener;
	public ErrorSimServerListener serverListener;
	private Thread serverListenerThread;
	public Errors errors;
	
	/**
	 * Constructor for the error sim class
	 * @param clientPort the port to listen to client requests on
	 * @param serverPort the port to send requests to the server on
	 * @param serverAddress the IP address of the server
	 * @param verbose true prints extra debug info, false prints only basic info
	 */
	public ErrorSim (int clientPort, int serverPort, InetAddress serverAddress, boolean verbose) {
		//Create the errors instance
		errors = new Errors();
		
		//Create the listeners
		clientListener = new ErrorSimClientListener(clientPort, verbose, this);
		serverListener = new ErrorSimServerListener(serverPort, serverAddress, verbose, this);
		serverListenerThread = new Thread(serverListener);
	}
	
	/**
	 * Starts the error simulator listener threads
	 */
	public void start () {
		clientListener.start();
		serverListenerThread.start();
	}
	
	//Handles the shutdown command
	private void shutdown (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		} else {
			clientListener.close();
			serverListener.close();
			try {
				serverListenerThread.join();
			} catch (InterruptedException e) {
				c.printerr("Error closing server listener thread.");
				System.exit(1);
			}
			try {
				c.close();
			} catch (IOException e) {
				c.printerr("Error closing console thread.");
				System.exit(1);
			}
			System.exit(0);
		}
	}
	
	//Handles the verbose command
	private void setVerboseCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		} else {
			clientListener.verbose = true;
			serverListener.verbose = true;
		}
	}
	
	//Handles the quiet command
	private void setQuietCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		} else {
			clientListener.verbose = false;
			serverListener.verbose = false;
		}
	}
	
	//Handles the clientport command
	private void setClientPortCmd (Console c, String[] args) {
		if(args.length > 2) {
			c.println("Error: Too many parameters.");
		} else if(args.length == 1) {
			c.println("Client port: " + this.clientListener.getClientKnownPort());
		} else if(args.length == 2) {
			int port = Integer.parseInt(args[1]);
			
			if(port > 0 && port < 65536) {
				clientListener.setClientKnownPort(port);
			}
			else {
				c.println("Invalid argument");
			}
		}
	}
	
	//Handles the serverport command
	private void setServerPortCmd (Console c, String[] args) {
		if(args.length > 2) {
			c.println("Error: Too many parameters.");
		} else if(args.length == 1) {
			c.println("Server port: " + this.serverListener.getServerKnownPort());
		} else if(args.length == 2) {
			int port = Integer.parseInt(args[1]);
			
			if(port > 0 && port < 65536) {
				InetAddress serverAddress = this.serverListener.getServerAddress();
				boolean verbose = this.clientListener.verbose;
				
				serverListener.close();
				try {
					serverListenerThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				//Restart the listener
				serverListener = new ErrorSimServerListener(port, serverAddress, verbose, this);
				serverListenerThread = new Thread(serverListener);
				serverListenerThread.start();
			}
			else {
				c.println("Invalid argument");
			}
		}
	}
	
	//Handles the serverip command
	private void setServerIPCmd (Console c, String[] args) {
		if(args.length > 2) {
			c.println("Error: Too many parameters.");
		} else if(args.length == 1) {
			c.println("Server ip " + this.serverListener.getServerAddress());
		} else if(args.length == 2) {
			try {
				int serverPort = this.serverListener.getServerKnownPort();
				boolean verbose = this.serverListener.verbose;
				
				InetAddress serverAddress = InetAddress.getByName(args[1]);
				
				serverListener.close();
				serverListenerThread.join();
				
				//Restart the listener
				serverListener = new ErrorSimServerListener(serverPort, serverAddress, verbose, this);
				serverListenerThread = new Thread(serverListener);
				serverListenerThread.start();
			} catch (UnknownHostException e) {
				c.println("Invalid argument");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	//Handles the drop command
	private void dropCmd (Console c, String[] args) {
		if(args.length > 4) {
			c.println("Error: Too many parameters.");
		} else if(args.length < 4){
			c.println("Error: Not enough parameters.");
		} else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]), 
						ErrorInstruction.errorTypes.DROP, 	//Error type
						Integer.parseInt(args[2]),			//Affected packet type
						0,									//Time delay - not used here
						Integer.parseInt(args[3])));		//How many times to perform
			}
			catch(IllegalArgumentException e) {
				c.println("Error: Invalid argument");
			}
		}
	}
	
	//Handles the delay command
	private void delayCmd (Console c, String[] args) {
		if(args.length > 5) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 5){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]), 
						ErrorInstruction.errorTypes.DELAY, 	//Error type
						Integer.parseInt(args[2]),			//Affected packet type
						Integer.parseInt(args[3]),			//Time delay
						Integer.parseInt(args[4])));		//How many times to perform
			}
			catch(IllegalArgumentException e) {
				c.println("Error: Invalid argument");
			}
		}
	}
	
	//Handles the duplicate command
	private void duplicateCmd (Console c, String[] args) {
		if(args.length > 5) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 5){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]), 
						ErrorInstruction.errorTypes.DUPLICATE, 	//Error type
						Integer.parseInt(args[2]),			//Affected packet type
						Integer.parseInt(args[3]),			//Time delay
						Integer.parseInt(args[4])));		//How many times to perform
			}
			catch(IllegalArgumentException e) {
				c.println("Error: Invalid argument");
			}
		}
	}
	
	//Handles the errors command
	private void errorsCmd (Console c, String[] args) {
		c.println(errors.toString());
	}
	
	//Handles the help command
	private void helpCmd (Console c, String[] args) {
		c.println("The following is a list of commands and thier usage:");
		c.println("shutdown - Closes the error simulator program.");
		c.println("verbose - Makes the error simulator output more detailed information.");
		c.println("quiet - Makes the error simulator output only basic information.");
		c.println("clientport [x] - Outputs the port currently being used to listen to requests "
				+ "from the client if x is not provided. If parameter x is provided, then the port "
				+ "is changed to x.");
		c.println("serverport [x] - Outputs the port currently being used to forward requests "
				+ "to the server if x is not provided. If parameter x is provided, then the port "
				+ "is changed to x.");
		c.println("serverip [x] - Outputs the IP address currently being used to communicate with "
				+ "the server if x is not provided. If parameter x is provided, then the IP address "
				+ "is changed to x.");
		c.println("drop [A B C] - Drops a packet. A = packet type, B = packet number, C = # of times to create error.");
		c.println("	Valid packet types are RRQ, WRQ, DATA, ACK, ERROR. C < 0 is infinite.");
		c.println("delay [A B C D] - Delays a packet. A = packet type, B = packet number, C = delay time (ms), D = # of times to create error.");
		c.println("	Valid packet types are RRQ, WRQ, DATA, ACK, ERROR. D < 0 is infinite.");
		c.println("duplicate [A B C D] - Duplicates a packet. A = packet type, B = packet number, C = time between packets (ms), D = # of times to create error.");
		c.println("	Valid packet types are RRQ, WRQ, DATA, ACK, ERROR. D < 0 is infinite.");
		c.println("help - Shows help information.");
	}
	

	/**
	 * main function for the error simulator
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		
		//Initialize settings to default values
		Boolean verbose = false;
		int serverPort = 69;
		int clientPort = 23;
		InetAddress serverAddress = null;
		try {
			serverAddress = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		//Setup command line parser
		Option verboseOption = new Option( "v", "verbose", false, "print extra debug info" );
		
		Option serverPortOption = Option.builder("sp").argName("server port")
                .hasArg()
                .desc("the port number of the servers listener")
                .type(Integer.TYPE)
                .build();
		
		Option serverAddressOption = Option.builder("sa").argName("server address")
                .hasArg()
                .desc("the IP address of the server")
                .type(String.class)
                .build();
		
		Option clientPortOption = Option.builder("cp").argName("client port")
                .hasArg()
                .desc("the port number to listen to client requests on")
                .type(Integer.TYPE)
                .build();
		
		Options options = new Options();

		options.addOption(verboseOption);
		options.addOption(serverPortOption);
		options.addOption(serverAddressOption);
		options.addOption(clientPortOption);
		
		CommandLineParser parser = new DefaultParser();
	    try {
	        // parse the command line arguments
	        CommandLine line = parser.parse( options, args );
	        
	        if( line.hasOption("verbose")) {
		        verbose = true;
		    }
	        
	        if( line.hasOption("sp")) {
		        serverPort = Integer.parseInt(line.getOptionValue("sp"));
		    }
	        
	        if( line.hasOption("sa")) {
		        try {
					serverAddress = InetAddress.getByName((String)line.getParsedOptionValue("sa"));
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
		    }
	        
	        if( line.hasOption("cp")) {
	        	clientPort = Integer.parseInt(line.getOptionValue("cp"));
		    } 
	    }
	    catch( ParseException exp ) {
	        System.err.println( "Command line argument parsing failed.  Reason: " + exp.getMessage() );
	        System.exit(1);
	    }
		
		System.out.println("Error Simulator Running");
		
		if(verbose) {
			System.out.println("Listening to client on port " + clientPort);
			System.out.println("Server address: " + serverAddress + " port " + serverPort + "\n");
		}
		
		// Create and start ErrorSim instance
		ErrorSim errorSim = new ErrorSim (clientPort, serverPort, serverAddress, verbose);
		errorSim.start();
		
		// Create and start console UI thread
		Map<String, Console.CommandCallback> commands = Map.ofEntries(
				Map.entry("shutdown", errorSim::shutdown),
				Map.entry("verbose", errorSim::setVerboseCmd),
				Map.entry("quiet", errorSim::setQuietCmd),
				Map.entry("clientport", errorSim::setClientPortCmd),
				Map.entry("serverport", errorSim::setServerPortCmd),
				Map.entry("serverip", errorSim::setServerIPCmd),
				Map.entry("drop", errorSim::dropCmd),
				Map.entry("delay", errorSim::delayCmd),
				Map.entry("duplicate", errorSim::duplicateCmd),
				Map.entry("errors", errorSim::errorsCmd),
				Map.entry("help", errorSim::helpCmd)
				);
		
		Console console = new Console(commands);
		
		Thread consoleThread = new Thread(console);
		consoleThread.start();
	}
}