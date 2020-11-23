package com.udpimplementation.comp445a3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;

import com.udpimplementation.comp445a3.CustomSocketClient.CurlImplement;
import com.udpimplementation.comp445a3.CustomSocketClient.HttpRequestGenerator;

public class httpc {
	public static void runHttpc(String[] args, SocketAddress routerAddr, InetSocketAddress serverAddr) {
		// TODO Auto-generated method stub
		//System.out.println(args);
		CurlImplement curl = new CurlImplement(args);
		HttpRequestGenerator reqGenerator = HttpRequestGenerator.getRequestObj();
		if(curl.hasData) {
			reqGenerator.setReqBody(curl.getArgsBody());
		}
		if(curl.hasFile) {
			reqGenerator.setReqBody(curl.getArgsFile());
		}
		if(curl.hasHeaders) {
			reqGenerator.setReqHeader(curl.getHeaders());
		}
		reqGenerator.setReqMethod(curl.getMethod());
//		reqGenerator.setHasOverwrite(curl.getHasOverwrite());
		
		String outFileName="";
		if(curl.hasOutputFile) {
			outFileName = curl.getOutFileName();
		}
		
		
		URL url = curl.getUrl();
		String host = url.getHost();
		reqGenerator.setHostName(host);
		String relPath = url.getPath();
		reqGenerator.setReqRelativePath(relPath);
		String queryParam = url.getQuery();
		reqGenerator.setQueryParam(queryParam);
		
		//String req = reqGenerator.printReq();
		
//		SocketClient.socketClientConnection(reqGenerator, curl.hasVerbose, host, url.toString(), curl.hasOutputFile, outFileName);
		try {
			UDPClient.runClient(reqGenerator, curl.hasVerbose, url.toString(), curl.hasOutputFile, outFileName);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
