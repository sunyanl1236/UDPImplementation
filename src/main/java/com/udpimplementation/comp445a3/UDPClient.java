package com.udpimplementation.comp445a3;



import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.udpimplementation.comp445a3.CustomSocketClient.HttpRequestGenerator;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

public class UDPClient {

    private static final Logger logger = LoggerFactory.getLogger(UDPClient.class);
    private static long seqNum = 0;
    private static boolean doneHandshake = false;
    private static FileWriter myWriter = null;
    private static SocketAddress routerAddress = null;
    private static InetSocketAddress serverAddress = null;

    /* perform TCP three-way handshaking
     * returns true if three-way handshaking successes, otherwise false
     * */
    private static boolean threeWayHandshake(DatagramChannel channel) throws IOException {
    	//send SYN to initialize the connection	
    	Packet synPacket = new Packet.Builder()
                .setType(Packet.SYN)
                .setSequenceNumber(seqNum)
                .setPortNumber(serverAddress.getPort())
                .setPeerAddress(serverAddress.getAddress())
                .setPayload("".getBytes())
                .create();
        channel.send(synPacket.toBuffer(), routerAddress);
        
        logger.info("Sending SYN to initialize connection.\n\n");
        
        //wait for response
        timeout(channel);
        
        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        channel.receive(buf); //Receives a datagram via this channel,source address is returned.
        buf.flip(); //change mode to reading
        Packet resp = Packet.fromBuffer(buf);
        
        //update server address
        serverAddress = new InetSocketAddress(resp.getPeerAddress(), resp.getPeerPort());
        //test
        System.out.println("new server port is: "+ resp.getPeerPort());
        
        if(resp.getType() == Packet.SYN_ACK) {
        	logger.info("Received SYN-ACK from the server.");
        	
        	//send ACK and first payload to server
        	String msg = "Success";
        	seqNum++;
        	System.out.println("seq#: "+ seqNum);
        	
        	Packet ackConnectionPacket = new Packet.Builder()
                    .setType(Packet.ACK)
                    .setSequenceNumber(seqNum)
                    .setPortNumber(serverAddress.getPort())
                    .setPeerAddress(serverAddress.getAddress())
                    .setPayload(msg.getBytes())
                    .create();
            channel.send(ackConnectionPacket.toBuffer(), routerAddress);
            
            logger.info("Sending ACK and first payload to confirm connection.\n\n");
            
            return true;
        }
        return false;
    }
    
    
    
    public static void runClient(HttpRequestGenerator reqGenerator, boolean isVerbose, String url, boolean hasOutCommand, String outFileName) throws IOException {
        try(DatagramChannel channel = DatagramChannel.open()){
        	logger.info("Start three-way handshaking process...");
        	//if three-way handshaking failed, keep initializing connection
        	doneHandshake = threeWayHandshake(channel);
        	while(!doneHandshake) {
        		doneHandshake = threeWayHandshake(channel);
        	}
        	
        	//generate request
            String request = reqGenerator.printReq();
            seqNum++;
            Packet p = new Packet.Builder()
                    .setType(Packet.DATA)
                    .setSequenceNumber(seqNum)
                    .setPortNumber(serverAddress.getPort())
                    .setPeerAddress(serverAddress.getAddress())
                    .setPayload(request.getBytes())
                    .create();
            channel.send(p.toBuffer(), routerAddress);

            logger.info("Sending request to router at {}\n\n", routerAddress);

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

            //get response
            String response = "";
            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
            SocketAddress router = channel.receive(buf); //Receives a datagram via this channel.
            buf.flip(); //read mode
            Packet resp = Packet.fromBuffer(buf);
            logger.info("Received response from server.");
            logger.info("Packet: {}", resp);
            logger.info("Router: {}", router);
            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
            response = payload;
            logger.info("Payload: {}\n\n",  payload);
            
            //if the payload is http response, parse response body
            if(response.contains("HTTP")) {
            	String resBody = "";
            	String[] strArr = response.split("\r\n\r\n",2);
            	if(strArr != null) {
            		resBody = strArr[1];
            	}
            	if(resBody.isEmpty()) {
            		System.out.println("No response body.");
            	} else {
            		System.out.println("response body: " + resBody);
            	}
            }

//          keys.clear();
            
            
          //if contains "Content-Disposition", download the file
//			if(response.toString().contains("Content-Disposition")) {
//				String[] responseArr = response.toString().split("HTTP/1.0",2);
//				response = new StringBuilder();
//				response.append("HTTP/1.0").append(responseArr[1]);
//				
//				//get filename
//				String[] contentDispositionArr = response.toString().split("filename = \"");
//				int endIndex = contentDispositionArr[1].indexOf('"');
//				String fileName = contentDispositionArr[1].substring(0, endIndex);
//				System.out.println("Content-Disposition file name: " + fileName);
//				
//				//download the file
//				FileOutputStream fos = new FileOutputStream(fileName);
//			    BufferedOutputStream bos = new BufferedOutputStream(fos);
//			    byte[] fileByte = responseArr[0].getBytes();
//			    bos.write(fileByte, 0, fileByte.length);
//			    bos.close();
//			}
			
            if(isVerbose) {
    			//print the details of response
    			System.out.println("response:");
    			System.out.println(response);
    			
    			//print the request
    			System.out.println("\nrequest:");
    			System.out.println(request);
    		}
            
          //if has -o command, write the response to corresponding file name
			if(hasOutCommand && !outFileName.isEmpty()) {
				//File outFile = new File(outFileName);
				myWriter = new FileWriter(outFileName);
			    myWriter.write(response.toString());
			}
        }// end try
    } //end method
  
    
    
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

        routerAddress = new InetSocketAddress(routerHost, routerPort);
        serverAddress = new InetSocketAddress(serverHost, serverPort);

        Scanner sc = new Scanner(System.in);
        logger.info("Please enter httpc command: ");
        String keyIn = sc.nextLine();
        String[] tempArgs = keyIn.split(" ");
        
        //test & drop the first "httpc"
        String[] keyInArgs = new String[tempArgs.length -1];
        for(int i=1; i<tempArgs.length; i++){
        	keyInArgs[i-1] = tempArgs[i];
        }
        
        httpc.runHttpc(keyInArgs, routerAddress, serverAddress);
        
//      runClient(routerAddress, serverAddress);
    }
    
    
}

