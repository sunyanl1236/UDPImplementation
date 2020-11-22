package com.udpimplementation.comp445a3.CustomSocketClient;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;

public class SocketClient {
	private static Socket socket = null;
	private static InputStream in = null;
	private static OutputStream out = null;
	private static FileWriter myWriter = null;
	private static StringBuilder response = new StringBuilder();
	private static String request;
	public static void socketClientConnection(HttpRequestGenerator reqGenerator, boolean isVerbose, String host, String url, boolean hasOutCommand, String outFileName) {
		//all the variable needed
		String redirectUrl="";
//		String CurrentUrl = url;
		String statusCode="";
		String firstLineRes="";
		String redirectStr="";
		boolean redirectVerbose = false;
		String resBody="";
//		int portNum = 80;
		
		try {
			socket = new Socket(host, 8080);
			
			in = socket.getInputStream();
			out = socket.getOutputStream();
			
			//generate request
			request = reqGenerator.printReq();

			out.write(request.getBytes());
			out.flush();
			
			
			//get first response
			//StringBuilder response = new StringBuilder();
			int data = in.read();
			
			while(data != -1) {
				response.append((char) data);
				data = in.read();
			}
			
			//if contains "Content-Disposition", download the file
			if(response.toString().contains("Content-Disposition")) {
				String[] responseArr = response.toString().split("HTTP/1.0",2);
				response = new StringBuilder();
				response.append("HTTP/1.0").append(responseArr[1]);
				
				//get filename
				String[] contentDispositionArr = response.toString().split("filename = \"");
				int endIndex = contentDispositionArr[1].indexOf('"');
				String fileName = contentDispositionArr[1].substring(0, endIndex);
				System.out.println("Content-Disposition file name: " + fileName);
				
				//download the file
				FileOutputStream fos = new FileOutputStream(fileName);
			    BufferedOutputStream bos = new BufferedOutputStream(fos);
			    byte[] fileByte = responseArr[0].getBytes();
			    bos.write(fileByte, 0, fileByte.length);
			    bos.close();
				
				
			}
			
			//handle redirect if status code start with 3xx
			//check status code in response
			firstLineRes = response.toString().split("\r\n", 2)[0]; 
			statusCode = firstLineRes.split(" ")[1];
			
			if(statusCode.charAt(0) == '3') {
				//get Location url
				redirectStr = response.toString().split("Location: ", 2)[1];
				redirectUrl = redirectStr.split("\r\n",2)[0];
				
				//example: Location: /newStr
				if(redirectUrl.charAt(0) == '/') {
					redirectUrl = host+redirectUrl;
				}
				
				do {
					System.out.print("Status code is "+ statusCode + ". Redirect to url "+redirectUrl+"\n");
					if(isVerbose) {
						redirectVerbose = true;
					}
					printVerbose(isVerbose, resBody);
					
					//change url, change request line
					URL newUrl = new URL(redirectUrl);
					reqGenerator.setHostName(newUrl.getHost());
					reqGenerator.setReqRelativePath(newUrl.getPath());
					reqGenerator.setQueryParam(newUrl.getQuery());
					request = reqGenerator.printReq();
//					CurrentUrl = redirectUrl;
					
//					in.close();
//					out.close();
					
					socket = new Socket(newUrl.getHost(), 80); //change back to 80
					
					in = socket.getInputStream();
					out = socket.getOutputStream();
					
					//send request again
					out.write(request.getBytes());
					out.flush();
					
					
					response = new StringBuilder(); //clear the original stringBuilder
//					System.out.println("response after clear:"+response.toString());
					
					//get response
					data = in.read();
					
					while(data != -1) {
						response.append((char) data);
						data = in.read();
					}
					
					
					System.out.println("response:"+response);
					
					//check status code in response again
					firstLineRes = response.toString().split("\r\n", 2)[0]; 
					statusCode = firstLineRes.split(" ")[1];
					//update Location url
					redirectStr = response.toString().split("Location: ", 2)[1];
					redirectUrl = redirectStr.split("\r\n",2)[0];
					
					//example: Location: /newStr
					if(redirectUrl.charAt(0) == '/') {
						redirectUrl = host+redirectUrl;
					}
				}
//				while(statusCode.charAt(0) == '3' && !CurrentUrl.equals(redirectUrl));
				while(statusCode.charAt(0) == '3');
			}
			
			
			
			//if has -v command, print response
			if(!redirectVerbose) {
				printVerbose(isVerbose, resBody);
			}
			
			//if has -o command, write the response to corresponding file name
			if(hasOutCommand && !outFileName.isEmpty()) {
				//File outFile = new File(outFileName);
				myWriter = new FileWriter(outFileName);
			    myWriter.write(response.toString());
			}
		} 
		catch (IOException e) {
			e.printStackTrace();
			System.out.println("Failed to connect to "+url+" on port 80.");
		}
		finally {
			try {
				if(socket != null) {
					socket.close();
				}
				if(in != null) {
					in.close();
				}
				if(out != null) {
					out.close();
				}
				if(myWriter != null) {
					myWriter.close();
				}
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static void printVerbose(boolean isVerbose, String resBody ) {
		if(isVerbose) {
			//print the details of response
			System.out.println("response:");
			System.out.println(response);
			
			//print the request
			System.out.println("\n");
			System.out.println(request);
		} else {
			System.out.println("response:");
			String[] strArr = response.toString().split("\r\n\r\n",2);
			if(strArr != null) {
				resBody = strArr[1];
			}
			if(resBody.isEmpty()) {
				System.out.println("No response body.");
			} else {
				System.out.println(resBody);
			}
		}
	}
}
