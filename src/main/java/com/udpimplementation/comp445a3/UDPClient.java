package com.udpimplementation.comp445a3;



import joptsimple.OptionParser;
import joptsimple.OptionSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.udpimplementation.comp445a3.CustomSocketClient.HttpRequestGenerator;

import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import static java.nio.channels.SelectionKey.OP_READ;

public class UDPClient {
	
	private static final int MAX_WINDOW_SIZE = 5;
    private static final Logger logger = LoggerFactory.getLogger(UDPClient.class);
    private static FileWriter myWriter = null;
    private static SocketAddress routerAddress = null;
    private static InetSocketAddress serverAddress = null;
    private static int windowSize = 0;
    private static long seqNum = 0;
    private static boolean doneHandshake = false;
    private static DatagramChannel channel = null;
    private static Timer timer = new Timer(true);
    private static boolean isVerbose;
    private static boolean hasOutCommand;
    private static String outFileName;
    private static String request;
    
    private static HashMap<Long, PacketInformation> packetsInfo = new HashMap<>(); 
    private static Queue<Packet> generatedPackets = new LinkedList<Packet>(); // generated packets inside client
    private static LinkedList<Long> windowSeq = new LinkedList<Long>(); //packets seq# inside a single window size
    private static int packetsNum = 0;
    

    /* perform TCP three-way handshaking
     * returns true if three-way handshaking successes, otherwise false
     * */
    private static boolean threeWayHandshake() throws IOException {
		//send SYN to initialize the connection	
		Packet synPacket = new Packet.Builder()
				.setType(Packet.SYN)
				.setSequenceNumber(0l)
				.setPortNumber(serverAddress.getPort())
				.setPeerAddress(serverAddress.getAddress())
				.setPayload("".getBytes())
				.create();
		

		channel.send(synPacket.toBuffer(), routerAddress);
		//start timer
		timer.schedule(new PacketTimer(synPacket), 10000);
		
		logger.info("Sending SYN to initialize connection.\n\n");
    	
		//wait for SYN response
        if(timeout(channel)) {
        	ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        	channel.receive(buf); //Receives a datagram via this channel,source address is returned.
        	buf.flip(); //change mode to reading
        	Packet resp = Packet.fromBuffer(buf);
        	
        	//update server address
        	serverAddress = new InetSocketAddress(resp.getPeerAddress(), resp.getPeerPort());
        	//test
        	System.out.println("***********new server is: "+ serverAddress.toString());
        	
        	if(resp.getType() == Packet.SYN_ACK) {
        		logger.info("Received SYN-ACK from the server.");
        		
        		//send ACK and first payload to server
        		StringBuilder msg = new StringBuilder();
        		msg.append("Success").append("\npacketsNum: ").append(packetsNum)
        			.append("\nwindowSize: ").append(windowSize);
        		
        		//System.out.println("seq#: "+ seqNum);
        		
        		Packet ackConnectionPacket = new Packet.Builder()
        				.setType(Packet.ACK)
        				.setSequenceNumber(1l)
        				.setPortNumber(serverAddress.getPort())
        				.setPeerAddress(serverAddress.getAddress())
        				.setPayload(msg.toString().getBytes())
        				.create();
        		
        		channel.send(ackConnectionPacket.toBuffer(), routerAddress);
        		logger.info("Sending ACK and first payload to confirm connection.\n\n");
        		return true;
        	}
        }
        
        return false;
    }
    
    private static void closeConnection() throws IOException {
    	Packet FINpkt = new Packet.Builder()
				.setType(Packet.FIN)
				.setSequenceNumber(++seqNum)
				.setPortNumber(serverAddress.getPort())
				.setPeerAddress(serverAddress.getAddress())
				.setPayload("".getBytes())
				.create();
    	
    	do {
    		channel.send(FINpkt.toBuffer(), routerAddress);
        	logger.info("Client sent FIN to close connection.");
    	}
        while(!timeout(channel)); //wait for FIN response
    	
    	ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
    	
        channel.receive(buf); //Receives a datagram via this channel,source address is returned.
        buf.flip(); //change mode to reading
        Packet resp = Packet.fromBuffer(buf);
        int resType = resp.getType();
        String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
        
        if(resType == Packet.FIN_ACK && payload.equals("Finish")) {
        	logger.info("Received FIN_ACK from server to acknowledge client's FIN.");
        	
        	Packet ACKpkt = new Packet.Builder()
        			.setType(Packet.ACK)
        			.setSequenceNumber(++seqNum)
        			.setPortNumber(serverAddress.getPort())
        			.setPeerAddress(serverAddress.getAddress())
        			.setPayload("Finish connection".getBytes())
        			.create();
        	channel.send(ACKpkt.toBuffer(), routerAddress);
        	timer.schedule(new PacketTimer(ACKpkt), 10000);
        	logger.info("Close connection on the client side.");
        	channel.close();
        }
    }
    
    public static void runClient() throws IOException {
    	channel = DatagramChannel.open();
    	channel.configureBlocking(false);
    	InetSocketAddress clientAddress = new InetSocketAddress(InetAddress.getLocalHost(), 41830);
    	channel.bind(clientAddress); //bind client host and port


    	Selector selector = Selector.open();
    	channel.register(selector, SelectionKey.OP_READ);

    	//readBuff for response
    	ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN); 

    	int countRun = 0;


    	while(true) {
    		if(!doneHandshake) {
    			logger.info("Start three-way handshaking process...");
    			//if three-way handshaking failed, keep initializing connection
    			doneHandshake = threeWayHandshake();
    			while(!doneHandshake) {
    				doneHandshake = threeWayHandshake();
    			}
    			countRun++;
    		} 
    		else{
    			//send packets
    			if (windowSeq.size() == 0 && !generatedPackets.isEmpty()) { // 1st time to send pkt
    				while(windowSeq.size() < windowSize) { //initialize windowSeq queue
    					Packet pktToSend = generatedPackets.remove(); //dequeue one packet
    					PacketInformation pktToSendInfo = packetsInfo.get(pktToSend.getSequenceNumber());
    					System.out.println("get PacketInformation is null?"+ pktToSendInfo==null);
    					pktToSendInfo.setStatus("SENT");
    					windowSeq.addLast(pktToSend.getSequenceNumber()); //add seq# to window
    					channel.send(pktToSend.toBuffer(), routerAddress);
    					logger.info("Sending packet {} to router at {}\n\n", pktToSend.getSequenceNumber(), routerAddress);

    					//start timer
    					timer.schedule(new PacketTimer(pktToSend), 10000);
    				}
    			}
    			else { //already has windowSeq and recerive ack
    				//adjust window
    				adjustWindowSeq();
    			}


    			// Try to receive a packet within timeout.
    			/* "Blocking" simply means a function will wait until a certain event happens.
    			 * "Non blocking" means the function will never wait, it will just return straight away.
    			 * */
    			logger.info("Waiting for the response");


    			//static timer
    			selector.select(10000); //return ready to read channel

    			Set<SelectionKey> keys = selector.selectedKeys();
    			if(keys.isEmpty()){
    				continue;
    			}
    			
    			buf.clear();
    			SocketAddress router = channel.receive(buf);
    			buf.flip(); //change mode to reading
    			Packet resp = Packet.fromBuffer(buf);
    			InetAddress receivedServerAddr = resp.getPeerAddress();
    			int receivedServerPort = resp.getPeerPort();
    			long ackedSeq;


    			if(receivedServerAddr.toString().equals(serverAddress.getAddress().toString()) && receivedServerPort == serverAddress.getPort()) {
    				logger.info("Received response from server: {} {}",receivedServerAddr.toString(),receivedServerPort);
    				logger.info("Router: {}", router);
    				logger.info("Response Packet: {}", resp);
    				String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
    				logger.info("Payload: {}\n\n",  payload);
    				
    				//parse acknowledged packet seq# from payload
    				if(payload.contains("Acknowledged packet")) {
    					String[] parsedPayloadArr = payload.split(" ");
    					ackedSeq = Long.parseLong(parsedPayloadArr[parsedPayloadArr.length-1]);
//    					System.out.println("*****parsed seq#: "+ackedSeq);
    					
    					PacketInformation info = packetsInfo.get(ackedSeq);
    					buf.flip();
    					
    					//if seq# is inside windowSeq
    					if(ackedSeq<= windowSeq.getLast().longValue() && ackedSeq >= windowSeq.getFirst().longValue()) {
    						//change status to ACKED
        					info.setStatus("ACKED");
    					}
    				}
    				else { //payload is response
    					String response = payload;
//    					System.out.println("payload is response: "+ response); //test
    					String resBody = "";
						String[] strArr = payload.split("\\r\\n\\r\\n",2);
						
						//test strArr
//						System.out.println("********split response payload: ");
//						for(String s: strArr) {
//							System.out.println(s);
//						}
//						System.out.println("*************");
						
						if(strArr != null) {
							resBody = strArr[1];
						}
						if(resBody.isEmpty()) {
							System.out.println("No response body.");
						} else {
							System.out.println("response body: " + resBody);
						}
						
						
						//if the request has v option
						if(isVerbose) {
							//print the details of response
							System.out.println("response:");
							System.out.println(response);
							
							//print the request
							System.out.println("\nrequest:");
							System.out.println(request);
						}
						
						//if request has -o command, write the response to corresponding file name
						if(hasOutCommand && !outFileName.isEmpty()) {
							//File outFile = new File(outFileName);
							myWriter = new FileWriter(outFileName);
							myWriter.write(response.toString());
						}
    				}
    			}//check server
    			
    			keys.clear();
    			
    			//************send FIN if windowSeq and generatedPackets are empty
    			if(windowSeq.isEmpty() && generatedPackets.isEmpty()) {
    				//close connection
    				closeConnection();
    				break;
    			}
    			
			} //end else if doneHandshake
		}// while true
    } //end method
  
    public static void generatePackets(HttpRequestGenerator reqGenerator, boolean isV, String url, boolean hasO, String fileName) {
    	String req = reqGenerator.printReq();
    	System.out.println("*********request entered: \n"+ request);
    	
    	isVerbose = isV;
    	hasOutCommand = hasO;
    	outFileName = fileName;
    	request = req;
    	
    	//separate request into multiple packets
    	String[] requestChunk = request.split("\\n");
    	
    	for(int i=0; i<requestChunk.length; i++) {
    		seqNum++; //may change
    		Packet p = new Packet.Builder()
    				.setType(Packet.DATA)
    				.setSequenceNumber(seqNum)
    				.setPortNumber(serverAddress.getPort())
    				.setPeerAddress(serverAddress.getAddress())
    				.setPayload(requestChunk[i].getBytes())
    				.create();
    		
    		packetsInfo.put(seqNum, new PacketInformation(p, "UNSENT"));
    		generatedPackets.add(p); //enqueue
    		
    		packetsNum++;
    		
            logger.info("Packet generated: {} payload: {}", p, requestChunk[i]);
    		logger.info("Put packet {} into HashMap.\n\n", seqNum);
    	}
    }
    
    public static void adjustWindowSeq() throws IOException {
    	while(!windowSeq.isEmpty()) {
	    	Long seq = windowSeq.getFirst(); //dequeue one seq#
	    	PacketInformation info = packetsInfo.get(seq);
	    	if(info.getStatus().equals("ACKED")) {
	    		windowSeq.removeFirst(); //remove acked packets at the top
	    		if(!generatedPackets.isEmpty()) {
	    			Packet pktToSend = generatedPackets.remove(); //dequeue one packet
	    			windowSeq.addLast(pktToSend.getSequenceNumber()); //add new seq# to window
	    			
	    			PacketInformation toBeSentInfo = packetsInfo.get(pktToSend.getSequenceNumber());
	    			
	    			//send packets added
	    			toBeSentInfo.setStatus("SENT");
					channel.send(pktToSend.toBuffer(), routerAddress);
					logger.info("Sending packet {} to router at {}\n\n", pktToSend.getSequenceNumber(), routerAddress);
					
					//start timer
					timer.schedule(new PacketTimer(pktToSend), 10000);
	    		}
	    	} 
	    	else {
	    		break;
	    	}
    	}
    	
    	//test
		System.out.println("*************windowSeq now: ");
		for(Long seq: windowSeq) {
			System.out.println(seq);
		}
		System.out.println();
    	
    }
    
    
    private static boolean timeout(DatagramChannel channel) throws IOException {
    	// Try to receive a packet within timeout.
        /* "Blocking" simply means a function will wait until a certain event happens.
		 * "Non blocking" means the function will never wait, it will just return straight away.
         * */
        channel.configureBlocking(false); //non-blocking mode
        Selector selector = Selector.open();
        channel.register(selector, OP_READ);
        logger.info("Waiting for the response");
        
        //static timer
        selector.select(10000); //return ready to read channel

        Set<SelectionKey> keys = selector.selectedKeys();
        if(keys.isEmpty()){
            logger.error("No response after timeout");
            return false;
        }
        
        keys.clear();
        return true;
    }
    
	public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.accepts("router-host", "Router hostname")
                .withOptionalArg()
                .defaultsTo("127.0.1.1");

        parser.accepts("router-port", "Router port number")
                .withOptionalArg()
                .defaultsTo("3000");

        parser.accepts("server-host", "EchoServer hostname")
                .withOptionalArg()
                .defaultsTo("127.0.1.1");

        parser.accepts("server-port", "EchoServer listening port")
                .withOptionalArg()
                .defaultsTo("8007");

        OptionSet opts = parser.parse(args);

        // Router address
        String routerHost = (String) opts.valueOf("router-host");
        int routerPort = Integer.parseInt((String) opts.valueOf("router-port"));

        // Server address
        String serverHost = (String) opts.valueOf("server-host");
        int serverPort = Integer.parseInt((String) opts.valueOf("server-port"));

        routerAddress = new InetSocketAddress(routerHost, routerPort);
        serverAddress = new InetSocketAddress(serverHost, serverPort);

        
        logger.info("Please enter httpc command:");
        Scanner sc = new Scanner(System.in);
        
        Pattern pattern = Pattern.compile(
                "\"[^\"]*\"" +
                "|'[^']*'" +
                "|[A-Za-z']+"
            );
        String token;
        ArrayList<String> parsedArg = new ArrayList<>();
        while ((token = sc.findInLine(pattern)) != null) {
        	if(token.equals("httpc")) {
        		continue;
        	}
        	else if(token.contains("\"") || token.contains("\'")) {
        		token = token.substring(1, token.length()-1);
        		parsedArg.add(token);
//        		System.out.println(token);
        	}
        	else if(token.length() ==1) {
        		token = "-"+token;
        		parsedArg.add(token);
//        		System.out.println(token);
        	}
        	else {
        		parsedArg.add(token);
//        		System.out.println(token);
        	}
        }
        String[] keyInArgs = (String[]) parsedArg.toArray(new String[parsedArg.size()]);
        
    	httpc.runHttpc(keyInArgs, routerAddress, serverAddress);
        
        
        //calculate window size
        windowSize = Math.min((int)Math.floor(packetsNum/2.0), MAX_WINDOW_SIZE);
        logger.info("{} packets generated. Window size is {}.", packetsNum, windowSize);
        
        runClient();
        
        sc.close();
    }
    
    
    
    
    public static class PacketTimer extends TimerTask{
    	private Packet p;
    	
    	PacketTimer(Packet p){
    		this.p = p;
    	}
    	
    	@Override
    	public void run() {
    		//check if the packet is not acked
    		try {
    			Long pSeq = p.getSequenceNumber();
    			System.out.println("pSeq: "+pSeq.longValue());
    			if(pSeq != 0) {
    				PacketInformation info = packetsInfo.get(pSeq);
    				System.out.println("info is null? " + (info == null));
    				
    				if(!info.getStatus().equals("ACKED")) {
    					info.setStatus("RESENT");
    					channel.send(p.toBuffer(), routerAddress);
    				}
    			}else {
    				channel.send(p.toBuffer(), routerAddress);
    			}
    		} 
    		catch (IOException e) {
    			e.printStackTrace();
    		}
    	}
    }
    
}

