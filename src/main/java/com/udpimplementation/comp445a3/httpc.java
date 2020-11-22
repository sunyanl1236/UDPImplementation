package com.udpimplementation.comp445a3;

import java.net.URL;

import com.udpimplementation.comp445a3.CustomSocketClient.CurlImplement;
import com.udpimplementation.comp445a3.CustomSocketClient.HttpRequestGenerator;
import com.udpimplementation.comp445a3.CustomSocketClient.SocketClient;

public class httpc {
	public static void main(String[] args) {
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
		
		SocketClient.socketClientConnection(reqGenerator, curl.hasVerbose, host, url.toString(), curl.hasOutputFile, outFileName);
	}

}
