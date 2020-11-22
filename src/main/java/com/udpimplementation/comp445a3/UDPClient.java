package com.udpimplementation.comp445a3;



import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

public class UDPClient {

    private static final Logger logger = LoggerFactory.getLogger(UDPClient.class);
    private static long seqNum = 0;

    /* perform TCP three-way handshaking
     * returns true if three-way handshaking successes, otherwise false
     * */
    private static boolean threeWayHandshake(DatagramChannel channel, SocketAddress routerAddr, InetSocketAddress serverAddr) throws IOException {
    	//send SYN to initialize the connection	
    	Packet synPacket = new Packet.Builder()
                .setType(Packet.SYN)
                .setSequenceNumber(seqNum)
                .setPortNumber(serverAddr.getPort())
                .setPeerAddress(serverAddr.getAddress())
                .setPayload("".getBytes())
                .create();
        channel.send(synPacket.toBuffer(), routerAddr);
        
        logger.info("Sending SYN to initialize connection.\n\n");
        
        //wait for response
        timeout(channel);
        
        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        channel.receive(buf); //Receives a datagram via this channel,source address is returned.
        buf.flip(); //change mode to reading
        Packet resp = Packet.fromBuffer(buf);
        
        if(resp.getType() == Packet.SYN_ACK) {
        	logger.info("Received SYN-ACK from the server.");
        	
        	//send ACK and first payload to server
        	String msg = "Success";
        	seqNum++;
        	System.out.println("seq#: "+ seqNum);
        	
        	Packet ackConnectionPacket = new Packet.Builder()
                    .setType(Packet.ACK)
                    .setSequenceNumber(seqNum)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(msg.getBytes())
                    .create();
            channel.send(ackConnectionPacket.toBuffer(), routerAddr);
            
            logger.info("Sending ACK and first payload to confirm connection.\n\n");
            
            return true;
        }
        return false;
    }
    
    
    
    private static void runClient(SocketAddress routerAddr, InetSocketAddress serverAddr) throws IOException {
        try(DatagramChannel channel = DatagramChannel.open()){
        	logger.info("Start three-way handshaking process...");
        	//if three-way handshaking failed, keep initializing connection
        	boolean doneHandshake = threeWayHandshake(channel, routerAddr, serverAddr);
        	while(!doneHandshake) {
        		doneHandshake = threeWayHandshake(channel, routerAddr, serverAddr);
        	}
        	
            String msg = "Hello World";
            seqNum++;
            Packet p = new Packet.Builder()
                    .setType(Packet.DATA)
                    .setSequenceNumber(seqNum)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(msg.getBytes())
                    .create();
            channel.send(p.toBuffer(), routerAddr);

            logger.info("Sending \"{}\" to router at {}\n\n", msg, routerAddr);

            // Try to receive a packet within timeout.
            /* "Blocking" simply means a function will wait until a certain event happens.
			 * "Non blocking" means the function will never wait, it will just return straight away.
             * */
//            channel.configureBlocking(false); //non-blocking mode
//            Selector selector = Selector.open();
//            channel.register(selector, OP_READ);
//            logger.info("Waiting for the response");
//            selector.select(5000); //return ready to read channel
//
//            Set<SelectionKey> keys = selector.selectedKeys();
//            if(keys.isEmpty()){
//                logger.error("No response after timeout");
//                return;
//            }
            timeout(channel);

            // We just want a single response.
            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
            SocketAddress router = channel.receive(buf); //Receives a datagram via this channel.
            buf.flip();
            Packet resp = Packet.fromBuffer(buf);
            logger.info("Received response from server.");
            logger.info("Packet: {}", resp);
            logger.info("Router: {}", router);
            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
            logger.info("Payload: {}\n\n",  payload);

//            keys.clear();
        }
    }
    
    private static void timeout(DatagramChannel channel) throws IOException {
    	// Try to receive a packet within timeout.
        /* "Blocking" simply means a function will wait until a certain event happens.
		 * "Non blocking" means the function will never wait, it will just return straight away.
         * */
        channel.configureBlocking(false); //non-blocking mode
        Selector selector = Selector.open();
        channel.register(selector, OP_READ);
        logger.info("Waiting for the response");
        /*********need to modify the number for timeout!!!**************/
        selector.select(); //return ready to read channel

        Set<SelectionKey> keys = selector.selectedKeys();
        if(keys.isEmpty()){
            logger.error("No response after timeout");
        }
        
        keys.clear();
    }

    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.accepts("router-host", "Router hostname")
                .withOptionalArg()
                .defaultsTo("localhost");

        parser.accepts("router-port", "Router port number")
                .withOptionalArg()
                .defaultsTo("3000");

        parser.accepts("server-host", "EchoServer hostname")
                .withOptionalArg()
                .defaultsTo("localhost");

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

        SocketAddress routerAddress = new InetSocketAddress(routerHost, routerPort);
        InetSocketAddress serverAddress = new InetSocketAddress(serverHost, serverPort);

        runClient(routerAddress, serverAddress);
    }
}

