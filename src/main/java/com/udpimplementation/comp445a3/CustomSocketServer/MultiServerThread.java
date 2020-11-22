package com.udpimplementation.comp445a3.CustomSocketServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.locks.Lock;

public class MultiServerThread extends Thread{
	private Socket clientSocket = null;
	private PrintWriter out = null;
	private BufferedReader in = null;
	private HttpResponseGenerator resGenetator = null;
	
	Lock rlock = null;
	Lock wlock = null;
	
	private String requestMethod;
	private String queryDir;
	private String rootDir;
	private String reqBody = "";
	private String response;
	private boolean hasOverwrite = false;
	private boolean hasContentLength = false;
	private boolean hasDebugMsg;
	private int contentLen = 0;
	private boolean needDownload = false;
	
	
	public MultiServerThread(Socket cSocket, String rootDir, boolean hasDebugMsg, Lock rlock, Lock wlock) {
		this.clientSocket = cSocket;
		this.resGenetator = HttpResponseGenerator.getResponseObj();
		this.resGenetator.setDebugMsg(hasDebugMsg);
		this.rootDir = rootDir;
		this.hasDebugMsg = hasDebugMsg;
		this.rlock = rlock;
		this.wlock = wlock;
	}
	
	public void run() {
		try {
			OutputStream os = clientSocket.getOutputStream();
			out = new PrintWriter(os, true);
			in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			
			StringBuilder request = new StringBuilder();
			String line="";
			
			//read request line and header
			do {
				line = in.readLine();
				if(line.contains("GET") || line.contains("POST")) {
					//split the request line by " "
					String[] reqLineArr = line.split(" ");
					
					//test
					if(this.hasDebugMsg) {
						System.out.println("print reqLineArr\n");
						for(String ss : reqLineArr) {
							System.out.println(ss);
						}
					}
					
					this.requestMethod = reqLineArr[0];
					this.queryDir = reqLineArr[1];
					
					if(this.hasDebugMsg) {
						System.out.println("requestMethod: "+this.requestMethod);
						System.out.println("queryDir: "+this.queryDir);
					}
				}
				if(line.contains("Content-Length")) {
					this.hasContentLength = true;
					String[] contentLenArr = line.split(": ");
					this.contentLen = Integer.parseInt(contentLenArr[1]);
					//System.out.println("hasContentLength: "+ this.hasContentLength + "contentLen" + this.contentLen);
				}
				if(line.contains("Has-Overwrite")) {
					String[] hasOverwriterArr = line.split(": ");
					
					//test
					if(this.hasDebugMsg) {
						System.out.println("print hasOverwriterArr: \n");
						for(String sss: hasOverwriterArr) {
							System.out.println(sss);
						}
					}
					
					this.hasOverwrite = Boolean.parseBoolean(hasOverwriterArr[1]);
					if(this.hasDebugMsg) {
						System.out.println("hasOverwrite: "+ this.hasOverwrite);
					}
				}
				if(line.equals("")) {
					break;
				}
				request.append(line).append("\r\n");
			}
			while(true);
			
			//if has content length, read request body
			if(this.hasContentLength) {
				System.out.println("Has request body.");
				char c;
				for(int j=0; j< this.contentLen; j++) {
					c = (char)in.read();
					this.reqBody += c;
				}
				request.append("\r\n").append(this.reqBody);
			}
			
			//check if the file need to be download
			if(this.queryDir.equals("/dir1/dir11/download.txt") || this.queryDir.equals("/dir2/downloadpage.html")) {
				this.needDownload = true;
			}
			
			//print request
			if(this.hasDebugMsg) {
				System.out.println("\n\nPrint the request:");
				System.out.println(request.toString());
			}
			
			//get response
			byte[] result = this.resGenetator.processRequest(this.requestMethod, this.queryDir, this.rootDir, this.hasOverwrite, this.reqBody, this.rlock, this.wlock, this.needDownload);
			this.response = this.resGenetator.printResponse();
			
			//print response //test
			if(this.hasDebugMsg) {
				System.out.println("\n\nPrint the response:");
				System.out.println(this.response);
			}
			
			//send the response
			out.print(this.response);
			
			if(result != null) {
				os.write(result);
				os.flush();
			}
			out.flush();
			
			if(clientSocket != null) {
				clientSocket.close();
			}
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}
}
