package com.udpimplementation.comp445a3;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.udpimplementation.comp445a3.CustomSocketServer.MultiServerThread;
import com.udpimplementation.comp445a3.CustomSocketServer.ServerCLIImplement;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.Scanner;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

public class UDPServer {

    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);
    private static long seqNum = 0;
    private static int portNum;
    private static int countThread = 0;

    private void listenAndServe(int port, String rootDir, boolean hasDebugMsg) throws IOException {
    	portNum = port;
    	
        try (DatagramChannel channel = DatagramChannel.open()) {
        	channel.configureBlocking(true); //blocking mode, need to change later!!!!!!
        	DatagramSocket datagramSocket = channel.socket(); //get a DatagramSocket
        	datagramSocket.bind(new InetSocketAddress(port));
            logger.info("EchoServer is listening at {}\n\n", channel.getLocalAddress());
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            for (; ; ) {
                buf.clear();
                SocketAddress router = channel.receive(buf);

                // Parse a packet from the received raw data.
                buf.flip(); //read
                Packet packet = Packet.fromBuffer(buf);
                int PktType = packet.getType();
                
                String payload = new String(packet.getPayload(), UTF_8);
                logger.info("Packet: {}", packet);
                logger.info("Packet type: {}", PktType);
                logger.info("Payload: {}", payload);
                logger.info("Router: {}", router);
               
                
                buf.flip(); //write
                
                
                if(PktType == Packet.SYN) {
                	logger.info("Creating new thread " + (countThread++) + "\n\n");
                	MultiServerThread udpSocketThread = new MultiServerThread(channel, packet, ++portNum, logger, rootDir, hasDebugMsg);
                	udpSocketThread.start();
                }
                else {
                	System.out.println("Not SYN packet");
                }
                
                
                //original 
//                if(PktType == Packet.SYN) {
//                	//for acknowledging three-way handshake
//                	Packet synackPacket = packet.toBuilder()
//                            .setType(Packet.SYN_ACK)
//                            .setSequenceNumber(seqNum)
//                            .setPayload("".getBytes())
//                            .create();
//                	
//                	channel.send(synackPacket.toBuffer(), router);
//                	logger.info("Send SYN-ACK to client.\n\n");
//                }
//                //receive ack for building connection
//                else if(PktType == Packet.ACK && payload.equals("Success")) {
//                	logger.info("Build connection successfully!");
//                	doneHandshake = true;
//                	Packet resp = packet.toBuilder()
//                			.setPayload(payload.getBytes())
//                			.create();
//                	channel.send(resp.toBuffer(), router);
//                	logger.info("Echo back the payload.\n\n");
//                }
//                else {
//                	// Send the response to the router not the client.
//                	// The peer address of the packet is the address of the client already.
//                	// We can use toBuilder to copy properties of the current packet.
//                	// This demonstrate how to create a new packet from an existing packet.
//                	Packet resp = packet.toBuilder()
//                			.setPayload(payload.getBytes())
//                			.create();
//                	channel.send(resp.toBuffer(), router);
//                	logger.info("Sent response to client.\n\n");
//                }


            }
        }
    }

    public static void main(String[] args) throws IOException {
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
        
        UDPServer server = new UDPServer();
        server.listenAndServe(port, parseCommand.getRootDirPath(), parseCommand.getHasDebugMsg());
        
    }
}