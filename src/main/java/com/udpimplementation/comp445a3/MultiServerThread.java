package com.udpimplementation.comp445a3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;

import com.udpimplementation.comp445a3.Packet;
import com.udpimplementation.comp445a3.CustomSocketServer.HttpResponseGenerator;

public class MultiServerThread extends Thread{
	public DatagramChannel channel = null;
	private SocketAddress clientAddress = null; //client address
	private SocketAddress routerAddress = null; //router address
	private BufferedReader in = null;
	private HttpResponseGenerator resGenetator = null;
	private ByteBuffer readBuf = ByteBuffer.allocate(Packet.MAX_LEN).order(ByteOrder.BIG_ENDIAN);

	final static ReadWriteLock rwlock = new ReentrantReadWriteLock();
	final static Lock rlock = rwlock.readLock();
	final static Lock wlock = rwlock.writeLock();
	private static Logger logger = null;
	private static long seqNum = 0;
	private static Selector selector = null;

	private boolean doneHandshake = false;
	private String requestMethod;
	private String queryDir;
	private String rootDir;
	private String reqBody = "";
	private String response;
	private String msgRead = "";
	private boolean hasOverwrite = false;
	private boolean hasContentLength = false;
	private boolean hasDebugMsg;
	private int contentLen = 0;
	private boolean needDownload = false;
	public int portNum;
//	private int count; //thread number
	private int countRun=0; //count the running times
	private Packet packet = null;


	public MultiServerThread(DatagramChannel chn, Selector s, SocketAddress clientAddress, SocketAddress routerAddress, 
			ByteBuffer readBuf, int serverPort, Logger l, String rootDir, boolean hasDebugMsg) {
		this.channel = chn;
		this.clientAddress = clientAddress;
		this.routerAddress = routerAddress;
//		this.packet = packet;
		this.readBuf= readBuf; //initialize readBuf
		selector = s;
		this.portNum = serverPort;
		this.resGenetator = HttpResponseGenerator.getResponseObj();
		this.resGenetator.setDebugMsg(hasDebugMsg);
		this.rootDir = rootDir;
		this.hasDebugMsg = hasDebugMsg;
		logger = l;
	}


	public void run() {
		try {
			ByteBuffer writeBuf = ByteBuffer
					.allocate(Packet.MAX_LEN)
					.order(ByteOrder.BIG_ENDIAN);

			while(true) {
				if(countRun!=0) {
            		//check if receive from the same client 
					readBuf.rewind(); //reset position to 0
            		SocketAddress routerAddr = channel.receive(readBuf);
//            		readBuf.flip(); //read
					System.out.println("&&&&&&&&&&&&&&&&&&& Test &&&&&&&&&&&&&&&"+" readBuf limit: "+ readBuf.limit());
					packet = Packet.fromBuffer(readBuf);
					
					//read client info
		            InetSocketAddress receivedClientAddr = new InetSocketAddress(packet.getPeerAddress(), packet.getPeerPort());
		            String receivedClientAddrStr = receivedClientAddr.toString();
					
            		if ( receivedClientAddrStr.equals(this.clientAddress.toString())) {
            			System.out.println("*************received client Address: "+ receivedClientAddrStr);
            		}
            		else {
            			continue;
            		}
            	}
				
				packet = Packet.fromBuffer(readBuf);
				String payload = new String(packet.getPayload(), StandardCharsets.UTF_8);
				int PktType = packet.getType();
//	            InetSocketAddress receivedClientAddr = new InetSocketAddress(packet.getPeerAddress(), packet.getPeerPort());
//	            String receivedClientAddrStr = receivedClientAddr.toString();
	            byte[] packetByteArr = null;
	        	
				logger.info("Packet: {}", packet);
				logger.info("Packet type: {}", PktType);
				logger.info("Payload: {}", payload);
//				logger.info("Router: {}", router);
				readBuf.clear(); 
	
				if(PktType == Packet.SYN) {
					//for acknowledging three-way handshake
					Packet synackPacket = packet.toBuilder()
							.setType(Packet.SYN_ACK)
							.setSequenceNumber(seqNum)
							.setPayload("".getBytes())
							.create();
	
					//convert Packet to byte array
					packetByteArr = synackPacket.toBytes();
					System.out.println("Peer address in generated packet " + synackPacket.getPeerAddress().toString()+": "+synackPacket.getPeerPort());
	
					logger.info("Send SYN-ACK to client.\n\n");
				}
				//receive ack for building connection
				else if(PktType == Packet.ACK && payload.equals("Success")) {
					logger.info("Build connection successfully!");
					doneHandshake = true;
					Packet resp = packet.toBuilder()
							.setPayload(payload.getBytes())
							.create();
	
					//convert Packet to byte array
					packetByteArr = resp.toBytes();
	
					logger.info("Echo back the payload.\n\n");
				}
				else if(payload.contains("HTTP")){ //if payload contains an HTTP request
					if(doneHandshake) {
						System.out.println("\n\nPrint the request:");
						String request = parseRequest(payload);
						System.out.println(request);
	
						//get response
						byte[] result = this.resGenetator.processRequest(this.requestMethod, this.queryDir, this.rootDir, this.hasOverwrite, this.reqBody, rlock, wlock, this.needDownload);
						this.response = this.resGenetator.printResponse();
	
						System.out.println("\n\nPrint the response:");
						System.out.println(this.response);
	
						String newPayload = this.response;
						if(result != null) {
							newPayload += new String(result, StandardCharsets.UTF_8);
						}
	
						Packet resp = packet.toBuilder()
								.setPayload(newPayload.getBytes())
								.create();
	
						//convert Packet to byte array
						packetByteArr = resp.toBytes();
	
						logger.info("Sent response to client.\n\n");
					}
					else {
						System.out.println("\n\nHaven't done handshake yet");
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
						packetByteArr = resp.toBytes();
	
						logger.info("Sent echo response to client.\n\n");
					}
					else {
						System.out.println("\n\nHaven't done handshake yet");
					}
				}
				
				if(packetByteArr !=null) {
					writeBuf.put(packetByteArr);
					writeBuf.flip();
					channel.send(writeBuf, routerAddress);
					writeBuf.clear();
					countRun++;
				}
	
			}
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	} //end method

	private String parseRequest(String rawReq) throws IOException {
		String[] reqArr = rawReq.split("\\r\\n");

		//test
		System.out.println("print reqArr\n");
		for(String ss : reqArr) {
			System.out.println(ss);
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
			String contentLenStr2 = contentLenStr1.split("\\r\\n")[0];
			this.contentLen = Integer.parseInt(contentLenStr2);
			System.out.println("hasContentLength: "+ this.hasContentLength + "contentLen" + this.contentLen);

			//if has content length, read request body
			System.out.println("Has request body.");
			char c;
			for(int j=0; j< this.contentLen; j++) {
				c = (char)in.read();
				this.reqBody += c;
			}	
		}
		if(rawReq.contains("Has-Overwrite")) {
			String hasOverwriterStr1 = rawReq.split("Has-Overwrite: ")[1];
			String hasOverwriterStr2 = hasOverwriterStr1.split("\\r\\n")[0];

			this.hasOverwrite = Boolean.parseBoolean(hasOverwriterStr2);
			System.out.println("hasOverwrite: "+ this.hasOverwrite);
		}

		//generate whole request
		String reqLineStr = rawReq.split("\\r\\n\\r\\n")[0];
		String wholeReq = reqLineStr + "\\r\\n\\r\\n" + this.reqBody;
		return wholeReq;
	}//end method

} //end class
