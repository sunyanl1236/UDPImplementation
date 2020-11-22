package com.udpimplementation.comp445a3.CustomSocketClient;
import java.util.HashMap;
import java.util.Map;

/* Using singleton pattern to create one and only one request for each command line
 * */
public class HttpRequestGenerator {
	private static HttpRequestGenerator req = new HttpRequestGenerator();
	private String method;
	private String relativePath;
	private HashMap<String, String> reqHeader = new HashMap<>();
	private String reqBody="";
	private String reqQueryParam;
	private String hostName;
	private boolean hasOverwrite;
	
	//constructor
	private HttpRequestGenerator() {}
	
	public static HttpRequestGenerator getRequestObj() {
		return req;
	}
	
	public void setReqMethod(String reqMethod) {
		this.method = reqMethod.toUpperCase();
	}
	
	public void setReqRelativePath(String relPath) {
		this.relativePath = relPath.isEmpty()? "/" : relPath;
		//System.out.println("relativePath: "+ this.relativePath);
	}
	
	public void setReqHeader(HashMap<String, String> reqHeaderKeyVal) {
		//String[] splitResult = reqHeaderKeyValuePair.split(":");
		this.reqHeader = reqHeaderKeyVal;
	}
	
	public void setReqBody(String body) {
		this.reqBody = body;
	}
	
	public void setQueryParam(String query) {
		this.reqQueryParam = query;
	}
	
	public void setHostName(String host) {
		this.hostName = host;
	}
	
	public void setHasOverwrite(boolean hasOverwrite) {
		this.hasOverwrite = hasOverwrite;
	}
	
	public String printReq() {
		StringBuilder sb = new StringBuilder();
		//request line
		if(reqQueryParam != null && !reqQueryParam.isEmpty()) {
			//System.out.println("has both relativePath and query.");
			sb.append(this.method).append(" ").append(this.relativePath).append("?").append(this.reqQueryParam).append(" HTTP/1.0\r\n");
		}
		else {
			//System.out.println("Only has relativePath, no query.");
			sb.append(this.method).append(" ").append(this.relativePath).append(" HTTP/1.0\r\n");
		}
		
		//request header
		//traverse the HashMap and append the key-val pair to sb
		for(Map.Entry<String, String> entry : reqHeader.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
		}
		//content-length
		//System.out.println("this.method: "+this.method);
		//System.out.println("this.reqBody.isEmpty(): "+this.reqBody.isEmpty());
		if(this.method.equals("POST") && !this.reqBody.isEmpty() && !reqHeader.containsKey("Content-Length")) {
			sb.append("Content-Length: ").append(this.reqBody.length()).append("\r\n");
		}
		sb.append("User-Agent: Concordia-HTTP/1.0\r\n").append("Host: ").append(this.hostName).append("\r\n");
		
		//custom header info
		if(hasOverwrite) {
			sb.append("Has-Overwrite: ").append(Boolean.toString(this.hasOverwrite)).append("\r\n");
		}
		sb.append("\r\n");
		
		/* check GET and POST request body in main
		 * the GET method should not have entity body
		 * */
		if(this.method.equals("POST") && !this.reqBody.isEmpty()) {
			sb.append(this.reqBody);
		}
		return sb.toString();
	}
	
}
