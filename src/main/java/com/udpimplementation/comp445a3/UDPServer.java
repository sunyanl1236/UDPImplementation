package com.udpimplementation.comp445a3;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.udpimplementation.comp445a3.CustomSocketServer.HttpResponseGenerator;
import com.udpimplementation.comp445a3.CustomSocketServer.ServerCLIImplement;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

public class UDPServer {
    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);
    private static long seqNum = 0;
    private SocketAddress router = null; //router address
    private boolean doneHandshake = false;
    private BufferedReader in = null;
//    private static int portNum;
//    private static int countThread = 0;
//    private static HashMap<String, MultiServerThread> clientAddrSet = new HashMap<>();
    
    
	final static ReadWriteLock rwlock = new ReentrantReadWriteLock();
	final static Lock rlock = rwlock.readLock();
	final static Lock wlock = rwlock.writeLock();
	private String requestMethod;
	private String queryDir;
	private static String rootDir;
	private String reqBody = "";
	private String response;
	private boolean hasOverwrite = false;
	private boolean hasContentLength = false;
	private int contentLen = 0;
	private boolean needDownload = false;
	public int portNum;
	private static HttpResponseGenerator resGenetator = HttpResponseGenerator.getResponseObj();
	private static LinkedList<Long> windowSeq = new LinkedList<Long>(); //packets seq# inside a single window size
	private static int windowSize = 0;
	private static int packetsNum = 0;
	private static Queue<Long> toBeAckedPackets = new LinkedList<Long>(); // store all seq# of pkt to be acked
	private static HashMap<Long, Packet> bufferedPackets = new HashMap<>(); //store all acked packets

    private void listenAndServe(int port) throws IOException, InterruptedException, AsynchronousCloseException {
//    	portNum = port;
    	
        try (DatagramChannel channel = DatagramChannel.open()) {
        	channel.configureBlocking(true); //blocking mode, need to change later!!!!!!
//        	DatagramSocket datagramSocket = channel.socket(); //get a DatagramSocket
        	channel.bind(new InetSocketAddress(InetAddress.getLocalHost(), port));
//        	portNum++;
        	
            logger.info("EchoServer is listening at {}\n\n", channel.getLocalAddress());
            
//            Selector selector = Selector.open();
//            channel.register(selector, SelectionKey.OP_READ);
            
            ByteBuffer readBuf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            for (; ; ) {
            	readBuf.clear();
                SocketAddress router = channel.receive(readBuf);

                // Parse a packet from the received raw data.
                readBuf.flip(); //read
                Packet packet = Packet.fromBuffer(readBuf);
                long seq = packet.getSequenceNumber();
                String payload = new String(packet.getPayload(), UTF_8);
                int PktType = packet.getType();
                logger.info("Packet: {}", packet);
                logger.info("Payload: {}", payload);
                logger.info("Packet type: {}", PktType);
                logger.info("Router: {}", router);
                
                readBuf.flip(); //write
                byte[] packetByteArr = null;
                
                if(PktType == Packet.SYN) {
					//for acknowledging three-way handshake
					Packet synackPacket = packet.toBuilder()
							.setType(Packet.SYN_ACK)
							.setSequenceNumber(seqNum)
							.setPayload("".getBytes())
							.create();
	
					//convert Packet to byte array
//					packetByteArr = synackPacket.toBytes();
					channel.send(synackPacket.toBuffer(), router);
					System.out.println("Peer address in generated packet " + synackPacket.getPeerAddress().toString()+": "+synackPacket.getPeerPort());
	
					logger.info("Send SYN-ACK to client.\n\n");
				}
				//receive ack for building connection
				else if(PktType == Packet.ACK && payload.contains("Success")) {
					logger.info("Build connection successfully!");
					doneHandshake = true;

					//initialize packetsNum, windowSize
					String[] tempArr = payload.split("\n");
					packetsNum = Integer.parseInt(tempArr[1].substring(tempArr[1].indexOf(':')+2));
					windowSize = Integer.parseInt(tempArr[2].substring(tempArr[1].indexOf(':')+2));
					
					//initialize toBeAckedPackets(start from 1)
					for(int i=0; i<packetsNum; i++) {
						toBeAckedPackets.add((long) (i+1));
					}
					
					// initialize windowSeq
					for(int i=0; i<windowSize; i++) {
						Long seqN = toBeAckedPackets.remove();
						windowSeq.addLast(seqN);
					}
					System.out.println("*************packetsNum: "+packetsNum+" windowSize: "+windowSize);
				}
				else if(PktType == Packet.FIN && doneHandshake) {
					logger.info("Received FIN from client.");
					Packet respACK = packet.toBuilder()
							.setType(Packet.FIN_ACK)
							.setPayload("Finish".getBytes())
							.create();

					channel.send(respACK.toBuffer(), router);
					logger.info("Sent FIN_ACK to acknowledge FIN from client.");
				}
				else if(PktType == Packet.ACK && doneHandshake && payload.contains("Finish connection")) { //receive ACK for FIN-ACK
					logger.info("Connection closed on the server side.");
					channel.close();
				}
				else if(PktType == Packet.DATA && doneHandshake) {
					//check seq# see if it's inside window
					String msg = "Acknowledged packet seq# "+ seq;
					if(windowSeq.contains(seq) && !bufferedPackets.containsKey(seq)) {
						bufferedPackets.put(seq, packet);
						
						//send ACK
						Packet ackPkt = packet.toBuilder()
								.setType(Packet.ACK)
								.setPayload(msg.getBytes())
								.create();
						channel.send(ackPkt.toBuffer(), router);
						logger.info("Sent ACK to packet seq={} and put it into buffer on the server side.\n\n", seq);
					}
					else{
						if(!windowSeq.isEmpty()) {
							long lowerBound = windowSeq.getFirst().longValue()-windowSize;
							long upperBound = windowSeq.getFirst().longValue()-1;
							
							//check if seq# in [rcvbase-N, rcvbase-1]
							if(seq<= upperBound && seq>= lowerBound) {
								//send ACK, but don't put it into buffer
								Packet ackPkt = packet.toBuilder()
										.setType(Packet.ACK)
										.setPayload(msg.getBytes())
										.create();
								channel.send(ackPkt.toBuffer(), router);
								logger.info("Sent ACK to packet seq={} in previous windowSeq.\n\n", seq);
							}
						}
					}
					
					//adjust window
					adjustWindow(seq);
					
					//if receive all packets, generate response
					if(doneHandshake && windowSeq.isEmpty() && isPacketCompleted()){
						//integrate all the packets payload and generate the request
						String rawReq="";
						for(int i=0; i<packetsNum; i++) {
							Long seqKey = (long)i+1;
							Packet p = bufferedPackets.get(seqKey);
							rawReq += new String(p.getPayload(),StandardCharsets.UTF_8);
						}
						
						//print req
						System.out.println("\n\nPrint the request:");
//						System.out.println("********rawReq: " + rawReq);
						String request = parseRequest(rawReq);
						System.out.println(request);

						//get response
						byte[] result = resGenetator.processRequest(this.requestMethod, this.queryDir, rootDir, this.hasOverwrite, this.reqBody, rlock, wlock, this.needDownload);
						this.response = resGenetator.printResponse();

						System.out.println("\n\nPrint the response:");
						System.out.println(this.response);

						String newPayload = this.response;
						System.out.println("send newPayload"+newPayload+"    size: "+ newPayload.length());
						if(result != null) {
							newPayload += new String(result, StandardCharsets.UTF_8); //download content
						}

						Packet resp = packet.toBuilder()
								.setPayload(newPayload.getBytes())
								.create();

						channel.send(resp.toBuffer(), router);
						logger.info("Sent response to client.");
					}
					
				}
				else { //echo back the payload
					if(doneHandshake) {
						// Send the response to the router not the client.
						// The peer address of the packet is the address of the client already.
						// We can use toBuilder to copy properties of the current packet.
						// This demonstrate how to create a new packet from an existing packet.
						Packet resp = packet.toBuilder()
								.setPayload(payload.getBytes())
								.create();
	
						//convert Packet to byte array
//						packetByteArr = resp.toBytes();
						channel.send(resp.toBuffer(), router);
						logger.info("Sent echo response to client.\n\n");
						System.exit(0); //*******************need change
					}
					else {
						System.out.println("\n\nHaven't done handshake yet");
					}
				}

            }
            
        }
    }
    
    private void adjustWindow(long seq) {
    	//adjust window (change to method)
		if(seq == windowSeq.getFirst().longValue() && bufferedPackets.containsKey(seq)) {
			windowSeq.removeFirst();
			if(!toBeAckedPackets.isEmpty()) {
				Long val = toBeAckedPackets.remove();
				windowSeq.addLast(val);
			}
		}
		else {
			//check all seq in windowSeq, see if they exist in ackedPackets
			int adjustNum = 0;
			for(Long v : windowSeq) {
				if(bufferedPackets.containsKey(v)) {
					adjustNum++;
				}
				else {
					break;
				}
			}
			
			for(int i=0; i<adjustNum; i++) {
				windowSeq.removeFirst();
				if(!toBeAckedPackets.isEmpty()) {
					Long val = toBeAckedPackets.remove();
					windowSeq.addLast(val);
				}
			}
		}
    }
    
    private boolean isPacketCompleted() {
    	for(int i=0; i<packetsNum; i++) {
    		Long seq = (long) (i+1);
    		if(bufferedPackets.containsKey(seq)) {
    			continue;
    		}
    		else {
    			return false;
    		}
    	}
    	
    	return true;
    }

    private String parseRequest(String rawReq) throws IOException {
		String[] reqArr = rawReq.split("\\R");

		//test
		System.out.println("print reqArr\n");
		for(String ss : reqArr) {
			System.out.println(ss+",,");
		}

		String reqLine = reqArr[0];
		if(reqLine.contains("GET") || reqLine.contains("POST")) {
			//split the request line by " "
			String[] reqLineArr = reqLine.split(" ");

			//test
			System.out.println("print reqLineArr\n");
			for(String ss : reqLineArr) {
				System.out.println(ss);
			}

			this.requestMethod = reqLineArr[0];
			this.queryDir = reqLineArr[1];

			//check if the file need to be download
			if(this.queryDir.equals("/dir1/dir11/download.txt") || this.queryDir.equals("/dir2/downloadpage.html")) {
				this.needDownload = true;
			}

			//test
			System.out.println("requestMethod: "+this.requestMethod);
			System.out.println("queryDir: "+this.queryDir);
		}

		if(rawReq.contains("Content-Length")) {
			this.hasContentLength = true;
			String contentLenStr1 = rawReq.split("Content-Length: ")[1];
			String contentLenStr2 = contentLenStr1.split("\\R")[0];
			
			//test
//			System.out.println("******contentLenStr1: "+contentLenStr1);
//			System.out.println("******contentLenStr2: "+contentLenStr2);
			
			this.contentLen = Integer.parseInt(contentLenStr2);
			System.out.println("hasContentLength: "+ this.hasContentLength + "   contentLen: " + this.contentLen);

			//if has content length, read request body
			System.out.println("Has request body.");
			this.reqBody = rawReq.split("\\R\\R")[1];
//			System.out.println("**********reqBody: "+ this.reqBody);
		}
		if(rawReq.contains("Has-Overwrite")) {
			String hasOverwriterStr1 = rawReq.split("Has-Overwrite: ")[1];
			String hasOverwriterStr2 = hasOverwriterStr1.split("\\R")[0];

			this.hasOverwrite = Boolean.parseBoolean(hasOverwriterStr2);
			System.out.println("hasOverwrite: "+ this.hasOverwrite);
		}

		//generate whole request
		String reqLineStr = rawReq.split("\\R\\R")[0];
		String wholeReq = reqLineStr + "\\R\\R" + this.reqBody;
		return wholeReq;
	}//end method
    
    public static void main(String[] args) {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("port", "p"), "Listening port")
                .withOptionalArg()
                .defaultsTo("8007");

        OptionSet opts = parser.parse(args);
        int port = Integer.parseInt((String) opts.valueOf("port"));
        
        //read httpf input
        Scanner sc = new Scanner(System.in);
        logger.info("Please enter httpfs command: ");
        String keyIn = sc.nextLine();
        String[] tempArgs = keyIn.split(" ");
        
        //test & drop the first "httpfs"
	      String[] keyInArgs = new String[tempArgs.length -1];
	      for(int i=1; i<tempArgs.length; i++){
	      	keyInArgs[i-1] = tempArgs[i];
	      }
        ServerCLIImplement parseCommand = new ServerCLIImplement(keyInArgs);
        resGenetator.setDebugMsg(parseCommand.getHasDebugMsg());
        rootDir = parseCommand.getRootDirPath();
        
        UDPServer server = new UDPServer();
        try {
			server.listenAndServe(port);
		} 
        catch(AsynchronousCloseException e) {
        	System.exit(0);
        }
        catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
    }
}
