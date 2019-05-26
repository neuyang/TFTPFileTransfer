import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.net.SocketTimeoutException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * A TFTP Client program for a client-server project
 * 
 * Follows TFTP standards, but cannot handle all errors.
 * Currently handles: incorrect packet types, delayed/duplicated packets, lost packets
 * 
 * @author Scott Malonda
 *
 */

public class Client {
	/**
	 * This class is a single-threaded implementation of a TFTP client.
	 * Command line arguments for the first data transfer are accepted
	 */
	private DatagramSocket sendReceiveSocket;
	private int serverPort;
	private boolean verbose;
	
	private InetAddress serverAddress;
	
	public void setServerAddress(InetAddress serverAddress) {
		this.serverAddress = serverAddress;
	}
	

	public Client(int serverPort, boolean verbose)
	{
		this.serverPort = serverPort;
		
		this.verbose = verbose;
		
		if(this.verbose) {
			System.out.println("Setting up send/receive socket.");
		}
		
		try {	//Setting up the socket that will send/receive packets
			sendReceiveSocket = new DatagramSocket();
		} catch(SocketException se) { //If the socket can't be created
			se.printStackTrace();
			System.exit(1);
		}
	}
	
	/**
	 * Method to read a file from the server.  The Client must already have the server
	 * address and port #.
	 * 
	 * @param filename Filepath to the client-side file that will be written to
	 */
	public void read(String filename)
	{
		
		if(verbose) {
			System.out.println("Reading from server file");
		}
		
		// Attempting to disable the socket timeout
		try {
			sendReceiveSocket.setSoTimeout(0);
		} catch(SocketException se) {
			System.err.println("Timeout could not be disabled.  Continuing proccess.\nProcess may terminate due to SocketTimeoutExceptions may be encountered.");
		}
		
		TFTPPacket.ACK ackPacket = new TFTPPacket.ACK(0);
		DatagramPacket sendPacket;
		byte[] data = new byte[TFTPPacket.MAX_SIZE];
		DatagramPacket receivePacket;
		TFTPPacket.DATA dataPacket = null;
		int len = 0;
		int blockNum = 0;
		int lastBlock = 0;
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		} 
		if(verbose) {
			System.out.println("File ready to be written! filename: "+filename);
		}
		
		// Receive data and send acks
		boolean moreToWrite = true;
		boolean duplicateData = false;
		while (moreToWrite) {
			// Receive Data Packet
			if(verbose) {
				System.out.println("Waiting for data packet");
			}
			
			data = new byte[TFTPPacket.MAX_SIZE];
			receivePacket = new DatagramPacket(data, data.length);
			
			try {
				sendReceiveSocket.receive(receivePacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			if(verbose) {
				System.out.println("Recieved packet from server");
			}
			// Check Packet for correctness
		    
			try {
				dataPacket = new TFTPPacket.DATA(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
			} catch (IllegalArgumentException e) {
				System.out.println("Not a DATA response to ack! :((((");
				e.printStackTrace();
				System.exit(0);
			}
			// Definitely data :)
			// Strip block number & port
			blockNum = dataPacket.getBlockNum();
			int replyPort = receivePacket.getPort();
			// Check if the received block is either duplicated or delayed.  If so, send an ACK 
			// packet for it but don't write any of the data to the file.
			if(blockNum < (lastBlock + 1)) {
				duplicateData = true;
			}
			// Check size? Less than 512 == done
			len = dataPacket.getData().length;
			if (len < 512) {
				moreToWrite = false;
			}
			if(verbose) {
				System.out.println("Received Packet:");
				System.out.println("Packet Type: DATA");
				System.out.println("Filename: "+filename);
				System.out.println("Block Number: "+blockNum);
				System.out.println("# of Bytes: "+len);
				if(duplicateData) {
					System.out.println("Duplicate data packet received.  Sending ACK and discarding data");
				}
			}
			// Write into file
			if(!duplicateData) {
				try {
					fos.write(dataPacket.getData(),0,dataPacket.getData().length);
				} catch (IOException e) {
					System.out.println("Failed to write data to file!");
					e.printStackTrace();
					System.exit(0);
				}
			}
			
			// Send Acknowledgement packet with block number
			ackPacket = new TFTPPacket.ACK(blockNum);
			sendPacket = new DatagramPacket(ackPacket.toBytes(), ackPacket.toBytes().length, serverAddress, replyPort);
			if(!duplicateData) {
				lastBlock = blockNum;
			}
			
			if(verbose) {
				System.out.println("ACK Packet Successfully Assembled");
				System.out.println("Last block number updated");
			}
			
			// Send ack packet to server on serverPort
			if(verbose) {
				System.out.println("Sending Packet:");
				System.out.println("Packet Type: ACK");
				// N/A System.out.println("Filename: "+this.filename);
				// N/A System.out.println("Mode: "+this.Mode);
				System.out.println("Block Number: "+blockNum);
				// N/A System.out.println("# of Bytes: "+len);
			}
			
			try {
				sendReceiveSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			duplicateData = false;
			if(verbose && moreToWrite) {
				System.out.println("Waiting for Next DATA Block:");
			}
		}
		
		System.out.println("File transfer complete!");
		
		try {
			fos.flush();
			fos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Method to write a file to the server.  The Client must already have the server
	 * address and port #.
	 * 
	 * @param filename Filepath to the client-side file that will be read from
	 */
	public void write(String filename)
	{
	    
		// Initializing the socket timeout.  If it cannot be set, the file transfer will be stopped.
		try {
			sendReceiveSocket.setSoTimeout(5000);
		} catch(SocketException se) {
			System.err.println("Socket timeout could not be set.  Cancelling file transfer.");
			return;
		}
		
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		} 
		if(verbose) {
			System.out.println("Successfully opened: "+filename);
		}
		
		TFTPPacket.DATA dataPacket;
	    DatagramPacket receivePacket;
	    DatagramPacket sendPacket = null;
	    byte[] data = new byte[TFTPPacket.MAX_SIZE];
	    int len = 69999;
		int blockNum = 0;
	    
		//Receiving the first ACK packet and stripping the new port number
	    receivePacket = new DatagramPacket(data, data.length);
	    try {
    		sendReceiveSocket.receive(receivePacket);
    	} catch(IOException e) {
    		e.printStackTrace();
			System.exit(1);
    	}
	    
	    if(verbose) {
			System.out.println("Recieved packet from server.");
		}
	    
	    // Parse ACK for correctness
	    TFTPPacket.ACK ackPacket = null;
    	try {
			ackPacket = new TFTPPacket.ACK(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
		} catch (IllegalArgumentException e) {
			System.out.println("Not an ACK Packet! :((((");
			e.printStackTrace();
			System.exit(0);
		}
    	
    	if (ackPacket.getBlockNum() == 0 ) {
			// Correct acks
			if(verbose) {
				System.out.println("Recieved ACK for block #0.  Starting data transfer...");
			}
		} else {
			// Incorrect ack
			System.out.println("Wrong ACK response. Incorrect block number");
    		throw new IllegalArgumentException();
		}
    	
		int replyPort = receivePacket.getPort();
		boolean moreToRead = true;
		boolean duplicateAck = false;
		while (moreToRead) {
			// Read data from file into data packet and send to server if the last ACK was correct
		    if(!duplicateAck) {
		    	blockNum++;
		    	blockNum = blockNum & 0xFFFF;
		    	try {
		    		if ((len=fis.read(data,0,512)) < 512) {
		    			moreToRead = false;
		    			if (len == -1) {
		    				// End of file reached exactly. Send 0 bytes of data.
		    				len = 0;
		    			}
		    			fis.close();
		    		}
		    		// Shrink wrap size based on the # of bytes read from the file
		    		data = Arrays.copyOf(data, len);
		    	} catch (IOException e) {
		    		e.printStackTrace();
		    		System.exit(0);
		    	}
		    	
		    	// Assemble data packet
		    	dataPacket = new TFTPPacket.DATA(blockNum, data);
		    	sendPacket = new DatagramPacket(dataPacket.toBytes(), dataPacket.toBytes().length, serverAddress, replyPort);
		    	
		    	if(verbose) {
		    		System.out.println("Sending Packet:");
		    		System.out.println("Packet Type: DATA");
		    		System.out.println("Filename: "+filename);
		    		// Mode not Applicable
		    		System.out.println("Block Number: "+blockNum);
		    		System.out.println("# of Bytes: "+len);
		    	}
		    	
		    	try {
		    		sendReceiveSocket.send(sendPacket);
		    	} catch (IOException e) {
		    		e.printStackTrace();
		    		System.exit(1);
		    	}
		    	
		    	duplicateAck = false;
		    }
		    
		    // Wait for ACK
		    if(verbose) {
		    	System.out.println("Waiting for ACK packet...");
		    }
		    
		    // New Receive total bytes
		    data = new byte[TFTPPacket.MAX_SIZE];
		    receivePacket = new DatagramPacket(data, data.length);
		    boolean acknowledged = false;
		    int resendCount = 0;
		    // Loop to handle socket timeouts.  If the socket times out, the last data packet is sent again.
		    // Loops 5 times until the program gives up and stops the transfer.
		    while(!acknowledged) {
		    	try {
		    		sendReceiveSocket.receive(receivePacket);
		    		acknowledged = true;
		    	} catch(IOException e1) {
		    		if(e1 instanceof SocketTimeoutException) {
		    			System.err.println("Socket timeout while waiting for ACK packet.");
		    			if(resendCount > 4) {
				    		if(verbose) System.err.println("Data has been re-sent 5 times.  Aborting file transfer.");
				    		return;
				    	}
		    			if(verbose) System.out.println("Re-sending last DATA packet.");
		    			try {
				    		sendReceiveSocket.send(sendPacket);
				    	} catch (IOException e2) {
				    		e2.printStackTrace();
				    		System.exit(1);
				    	}
		    			resendCount++;
		    		} else {
		    		e1.printStackTrace();
		    		System.exit(1);
		    		}
		    	}
		    }
		    
		    // Parse ACK for correctness
		    ackPacket = null;
	    	try {
				ackPacket = new TFTPPacket.ACK(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
				replyPort = receivePacket.getPort();
			} catch (IllegalArgumentException e) {
				System.err.println("Wrong Packet Recieved. Reason: Not an ackPacket");
				e.printStackTrace();
				System.exit(0);
			}
	    	
	    	if(verbose) {
				System.out.println("Received Packet:");
				System.out.println("Packet Type: ACK");
				// N/A System.out.println("Filename: "+this.filename);
				// N/A System.out.println("Mode: "+this.Mode);
				System.out.println("Block Number: "+blockNum);
				// N/A System.out.println("# of Bytes: "+len);
			}
	    	
	    	if (ackPacket.getBlockNum() == blockNum ) {
				// Correct acks
	    		duplicateAck = false;
			} else {
				// Incorrect ack
				System.err.println("Wrong ACK response. Reason: Incorrect block number.  Ignoring ACK and waiting for another packet.");
				duplicateAck = true;
			}
			
		}
		// All data is sent and last ACK received,
		// Close socket, quit
		System.out.println("File transfer complete!");
	}
	
	
	public void buildRequest(String source, String dest)
	{
		/**
		 * Checks the specified filepaths (source and dest) to see which one is on the server.
		 * If source is the server file, creates a read request and calls read().  If dest is
		 * on the server, creates a write request and calls write().
		 * The IP of the server is take
		 */
		DatagramPacket sendPacket = null;
		
		/*
		 * Checking which file (source or dest) is on the server to determine the type of
		 * request.
		 * Also recording the IP address of the server from the path to the server file and
		 * building the packet bytes
		 */
		if(source.contains(":")) {		//Create and send a read request
			String split[] = source.split(":");
			String addressString = split[0];
			String filepath = split[1];
			
			try {
				this.serverAddress = InetAddress.getByName(addressString);
			} catch(UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			// Read Request
			TFTPPacket.RRQ readPacket = new TFTPPacket.RRQ(filepath, TFTPPacket.TFTPMode.NETASCII);
			sendPacket = new DatagramPacket(readPacket.toBytes(), readPacket.size(), serverAddress, serverPort);
			
			if(verbose) {
				System.out.println("Sending Packet");
				System.out.println("Packet Type: RRQ");
				System.out.println("Filename: "+filepath);
				System.out.println("Mode: "+readPacket.getMode().toString());
				// Block Number not Applicable
				System.out.println("# of Bytes: "+(sendPacket.getData().length-4));
			}
			try {
				sendReceiveSocket.send(sendPacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			if(verbose ) {
				System.out.println("Request sent.  Waiting for response from server...");
			} else System.out.println("Request sent.");
			
			read(dest);
			
			
		} else if(dest.contains(":")) {		//Create and send a write request
			String split[] = dest.split(":");
			String addressString = split[0];
			String filepath = split[1];
			
			try {
				this.serverAddress = InetAddress.getByName(addressString);
			} catch(UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}

			// Write request
			TFTPPacket.WRQ writePacket = new TFTPPacket.WRQ(filepath, TFTPPacket.TFTPMode.parseFromString("netascii"));
			sendPacket = new DatagramPacket(writePacket.toBytes(), writePacket.size(), serverAddress, serverPort);
			
			if(verbose) {
				System.out.println("Sending Packet");
				System.out.println("Packet Type: RRQ");
				System.out.println("Filename: "+filepath);
				System.out.println("Mode: "+writePacket.getMode().toString());
				// Block Number not Applicable
				System.out.println("# of Bytes: "+(sendPacket.getData().length-4));
			}
			try {
				sendReceiveSocket.send(sendPacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			if(verbose ) {
				System.out.println("Request sent.  Waiting for response from server...");
			} else System.out.println("Request sent.");
	    	
			write(source);
		}
		
		else {	//If neither file is on the server, print an error message and quit.
			System.out.println("Error: neither file is on the server.  Please try another command.");
		}
	}
	
	private void shutdown (Console c, String[] args) {
		c.println("Closing socket and scanner, and shutting down server.");
		
		sendReceiveSocket.close();
		try {
			c.close();
		} catch (IOException e) {
			c.printerr("Error closing console thread.");
			System.exit(1);
		}
		
		System.exit(0);
	}
	
	private void setVerboseCmd (Console c, String[] args) {
		c.println("Running in verbose mode.");
		this.verbose = true;
	}
	
	private void setQuietCmd (Console c, String[] args) {
		c.println("Running in quiet mode.");
		this.verbose = false;
	}
	
	private void putCmd (Console c, String[] args) {
		if (args.length < 2) {
			// Not enough arguments
			c.println("Too few arguments.");
			return;
		} else if (args.length > 3) {
			// Too many arguments
			c.println("Too many arguments.");
			return;
		}
		
		if (this.serverAddress == null) {
			c.println("No server specified. Use the connect command to choose a server.");
			return;
		}
		
		String remoteFile = "";
		if (args.length == 2) {
			// Remote name is the same as the local name
			String[] parts = args[1].split("/");
			remoteFile = parts[parts.length - 1];
		} else {
			// Remote name is specified explicitly
			remoteFile = args[2];
		}
		
		// Do write request
		TFTPPacket.WRQ writePacket = new TFTPPacket.WRQ(remoteFile, TFTPPacket.TFTPMode.NETASCII);
		DatagramPacket request = new DatagramPacket(writePacket.toBytes(), writePacket.size(), serverAddress, serverPort);
		
		if (verbose) {
			c.println("Sending Packet");
			c.println("Packet Type: RRQ");
			c.println("Filename: " + remoteFile);
			c.println("Mode: " + writePacket.getMode().toString());
			c.println("# of Bytes: " + (request.getData().length - 4));
		}
		
		try {
			sendReceiveSocket.send(request);
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		if (verbose ) {
			c.println("Request sent.  Waiting for response from server...");
		} else {
			c.println("Request sent.");
		}
    	
		write(args[1]);
	}
	
	private void getCmd (Console c, String[] args) {
		if (args.length < 2) {
			// Not enough arguments
			c.println("Too few arguments.");
			return;
		} else if (args.length > 3) {
			// Too many arguments
			c.println("Too many arguments.");
			return;
		}
		
		if (this.serverAddress == null) {
			c.println("No server specified. Use the connect command to choose a server.");
			return;
		}
		
		String localFile = "";
		if (args.length == 2) {
			// Local name is the same as the remote name
			String[] parts = args[1].split("/");
			localFile = parts[parts.length - 1];
		} else {
			// Local name is specified explicitly
			localFile = args[2];
		}
		
		// Do read Request
		TFTPPacket.RRQ readPacket = new TFTPPacket.RRQ(args[1], TFTPPacket.TFTPMode.NETASCII);
		DatagramPacket request = new DatagramPacket(readPacket.toBytes(), readPacket.size(), serverAddress, serverPort);
		
		if (verbose) {
			c.println("Sending Packet");
			c.println("Packet Type: RRQ");
			c.println("Filename: " + args[1]);
			c.println("Mode: " + readPacket.getMode().toString());
			// Block Number not Applicable
			c.println("# of Bytes: " + (request.getData().length - 4));
		}
		
		try {
			sendReceiveSocket.send(request);
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		if (verbose ) {
			c.println("Request sent.  Waiting for response from server...");
		} else {
			c.println("Request sent.");
		}
		
		read(localFile);
	}
	
	private void connectCmd (Console c, String[] args) {
		if (args.length < 2) {
			// Not enough arguments
			c.println("Too few arguments.");
			return;
		} else if (args.length > 3) {
			// Too many arguments
			c.println("Too many arguments.");
			return;
		}
		
		try {
			this.setServerAddress(InetAddress.getByName(args[1]));
		} catch (UnknownHostException e) {
			c.println("Invalid server: \"" + args[1] + "\"");
		}
		
		if (args.length == 3) {
			// Parse port
			try {
				this.serverPort = Integer.parseInt(args[2]);
			} catch (NumberFormatException e) {
				c.println("Invalid port: \"" + args[2] + "\"");
			}
		} else {
			this.serverPort = 69;
		}
	}
	
	private void helpCmd (Console c, String[] args) {
		c.println("Avaliable Client Commands:");
		c.println("connect [server] <ip>\n\tSelect a server, if port is not specified port 69 will be used.");
		c.println("put [local file] <remote file>\n\tSend a file to the server.");
		c.println("get [remote file] <local file>\n\tGet a file from the server.");
		c.println("shutdown\n\tShutdown client.");
		c.println("verbose\n\tEnable debugging output.");
		c.println("quiet\n\tDisable debugging output.");
	}

	public static void main(String[] args) {
		System.out.println("Setting up Client...");
		
		int serverPort = 69;
		boolean verbose = false;
		
		//Setting up the parsing options
		Option verboseOption = new Option( "v", "verbose", false, "print extra debug info" );
		
		Option serverPortOption = Option.builder("p").argName("server port")
                .hasArg()
                .desc("the port number of the servers listener")
                .type(Integer.TYPE)
                .build();
		
		Options options = new Options();
		options.addOption(verboseOption);
		options.addOption(serverPortOption);
		
		CommandLine line = null;
		
		CommandLineParser parser = new DefaultParser();
	    try {
	        // parse the command line arguments
	        line = parser.parse( options, args );
	        
	        if( line.hasOption("verbose")) {
		        verbose = true;
		    }
	        
	        if( line.hasOption("p")) {
		        serverPort = Integer.parseInt(line.getOptionValue("p"));
		    }
	    } catch( ParseException exp ) {
	    	System.err.println( "Command line argument parsing failed.  Reason: " + exp.getMessage() );
		    System.exit(1);
	    }
	    
	    Client client = new Client(serverPort, verbose);
	    
	    
	    // Get the positional arguments and perform a transaction if one is specified
		String[] positionalArgs = line.getArgs();
		if (positionalArgs.length == 1) {
			// Assume that the argument is the server address
			try {
				client.setServerAddress(InetAddress.getByName(positionalArgs[0]));
			} catch (UnknownHostException e) {
				System.out.println("Invalid server: \"" + positionalArgs[0] + "\"");
			}
		} else if (positionalArgs.length == 2) {
			// Source and destination files specified
			client.buildRequest(positionalArgs[0], positionalArgs[1]);
			System.exit(0);
		} else if (positionalArgs.length > 2) {
			// Too many arguments
			System.out.println("Too many files specified, entering interactive mode.");
		}

		// Create and start console UI thread
		Map<String, Console.CommandCallback> commands = Map.ofEntries(
				Map.entry("shutdown", client::shutdown),
				Map.entry("verbose", client::setVerboseCmd),
				Map.entry("quiet", client::setQuietCmd),
				Map.entry("put", client::putCmd),
				Map.entry("get", client::getCmd),
				Map.entry("connect", client::connectCmd),
				Map.entry("help", client::helpCmd)
				);

		Console console = new Console(commands);

		Thread consoleThread = new Thread(console);
		consoleThread.start();
	}
}
