package com.udpimplementation.comp445a3.CustomSocketServer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

public class HttpResponseGenerator {
	/* Using singleton pattern to create one and only one request for each command line
	 * */
		private static HttpResponseGenerator res = new HttpResponseGenerator();
		private int statusCode;
		private HashMap<String, String> resHeader = new HashMap<>();
		private HashMap<Integer, String> statusCodePhrase = new HashMap<>();
		private String resBody="";
		private boolean hasDebugMsg;
		
		//constructor
		private HttpResponseGenerator() {
			statusCodePhrase.put(200, "200 OK");
			statusCodePhrase.put(204, "204 No Content");
			statusCodePhrase.put(404, "404 Not Found");
			statusCodePhrase.put(201, "201 Created");
			statusCodePhrase.put(400, "400 Bad Request");
			statusCodePhrase.put(403, "403 Forbidden ");
		}
		
		public static HttpResponseGenerator getResponseObj() {
			return res;
		}
		
		public void setReqHeader(HashMap<String, String> resHeaderKeyVal) {
			this.resHeader = resHeaderKeyVal;
		}
		
		public void setResBody(String body) {
			this.resBody = body;
		}
		
		public void setStatusCode(int codeNum) {
			this.statusCode = codeNum;
		}
		
		public void setDebugMsg(boolean debugMsg) {
			this.hasDebugMsg = debugMsg;
		}
		
		public int getStatusCode() {
			return this.statusCode;
		}
		
		//return the status code
		public byte[] processRequest(String requestMethod,String queryDir, String rootDir, boolean hasOverwrite, String reqBody, Lock rlock, Lock wlock, boolean needDownload) {
			if(needDownload) {
				rlock.lock();
				System.out.println("Downloading...");
				byte[] downloadResult = processDownload(queryDir, rootDir);
				rlock.unlock();
				System.out.println("Finish download!");
				return downloadResult;
			}
			
			if(requestMethod.toUpperCase().equals("GET")) {
				rlock.lock();
				processGetReq(queryDir, rootDir);
				rlock.unlock();
			}
			else if(requestMethod.toUpperCase().equals("POST")) {
				wlock.lock();
				processPostReq(queryDir, rootDir, hasOverwrite, reqBody);
				wlock.unlock();
			}
			else {
				this.statusCode = 400;
			}
			return null;
		}
		
		//return the status code
		public void processGetReq(String queryDir, String rootDir) {
			Path path = Paths.get(rootDir+queryDir);
			File f = path.toFile();
			if(this.hasDebugMsg) {
				System.out.println("The query path is "+queryDir);
				System.out.println("Full path is "+rootDir+queryDir);
				System.out.println("Is file?: "+f.isFile());
				System.out.println("Is directory?: "+Files.isDirectory(path));
			}
			
			//check secure access, if go outside of the root directory, return directly
			if(this.hasDebugMsg) {
				System.out.println("Check secure access in processGetReq(): "+ checkSecureAccess(queryDir));
			}
			if(!checkSecureAccess(queryDir)) {
				System.out.println("Cannot read/write any file outside of the root directory!");
				return;
			}
			
			try {
				if(Files.isDirectory(path)) { //check if the path is a dir
//				System.out.println("It's a directory.");
					if(hasOthersRead(path)) { //if readable
						if(this.hasDebugMsg) {
							System.out.println("hasOthersRead: "+ hasOthersRead(path));//test
						}
						//print all the dir and files in this path
						DirectoryStream<Path> dirStream = Files.newDirectoryStream(path);
						if(this.hasDebugMsg) {
							System.out.println("The request path is a directory, print all files and directories in the path: \n");
						}
						for (Path entry : dirStream) {
							resBody += (entry.getFileName().toString()+"\n");  
						}
						
						if(!this.resBody.isEmpty()) {
							this.statusCode = 200;
							
							//add Content-Length header
							resHeader.put("Content-Length", Integer.toString(this.resBody.length()));
							
							//add Content-Type header
							resHeader.put("Content-Type", "text/plain");
							resHeader.remove("Content-Disposition");
						}
						else {
							this.statusCode = 204;
							if(this.hasDebugMsg) {
								System.out.println("empty file");
							}
						}
					}
					else { //if not readable
						this.statusCode = 403;
						resHeader.remove("Content-Disposition");
						resHeader.remove("Content-Type");
						resHeader.put("Content-Length", "0");
						System.out.println("Dir not readable.");
					}
				}// end check dir
				else if(f.isFile()) { //check if the path is a file
					if(hasOthersRead(path)) { //check permission of a file
						if(this.hasDebugMsg) {
							System.out.println("hasOthersRead: "+ hasOthersRead(path));//test
							System.out.println("Is a file.");
						}
						
						//get content type
						String mimeType = Files.probeContentType(path);
						if(this.hasDebugMsg) {
							System.out.println("mimeType: " + mimeType);
						}
						
						//read the file content
						//File f = path.toFile();
						BufferedReader br = new BufferedReader(new FileReader(f));
						StringBuilder sb = new StringBuilder();
						String line;
						
						while((line = br.readLine()) != null) {
							sb.append(line);
							sb.append(System.lineSeparator());
				        }
						
						this.resBody = sb.toString();
						
						if(!this.resBody.isEmpty()) {
							this.statusCode = 200;
							
							//add Content-Length header
							resHeader.put("Content-Length", Integer.toString(this.resBody.length()));
							//add Content-Type header
							resHeader.put("Content-Type", mimeType);
							resHeader.remove("Content-Disposition");
						} else {
							this.statusCode = 204;
							this.resBody = "";
							resHeader.remove("Content-Type");
							resHeader.remove("Content-Disposition");
							resHeader.put("Content-Length", "0");
							System.out.println("empty file");
						}
							
						
					}
					else { //file is not readable
						this.statusCode = 403;
						resHeader.remove("Content-Type");
						resHeader.remove("Content-Disposition");
						resHeader.put("Content-Length", "0");
						System.out.println("File not readable.");
					}
				} //end check file
				else { //cannot open the path
					this.statusCode = 404;
					resHeader.remove("Content-Type");
					resHeader.remove("Content-Disposition");
					resHeader.put("Content-Length", "0");
					System.out.println("Cannot open the path");
				}
			}
			catch(NotDirectoryException e) {
				//System.out.println("Dir E in processGetReq");
				e.printStackTrace();
			}
			catch(IOException e) {
				//System.out.println("IO E in processGetReq");
				e.printStackTrace();
			}
		}
		

		public void processPostReq(String queryDir, String rootDir, boolean hasOverwrite, String reqBody) {
			boolean hasCreatedDirFile = false;
			
			//check secure access, if go outside of the root directory, return directly
			if(this.hasDebugMsg) {
				System.out.println("checkSecureAccess() in processPostReq(): "+checkSecureAccess(queryDir));
			}
			if(!checkSecureAccess(queryDir)) {
				System.out.println("Cannot read/write any file outside of the root directory!");
				return;
			}
			
			String fullPath = rootDir;
			String[] splittedQueryDir = queryDir.split("/");
			//test
			if(this.hasDebugMsg) {
				System.out.println("Print splittedQueryDir:");
				for(int k=1; k< splittedQueryDir.length; k++) {
					System.out.println(splittedQueryDir[k]);
				}
			}
			
			for(int i=1; i< splittedQueryDir.length; i++) {
				fullPath += File.separator+splittedQueryDir[i];
				//System.out.println("fullPath in processPost: "+ fullPath);
				Path p = Paths.get(fullPath);
				File ff = p.toFile();
				
				if(i != splittedQueryDir.length-1) { //if not the last elt --> dir
					//check if it's Dir (everthing in between the root folder and last element should be directory)
					if(ff.exists()) { //dir exists
						//if the dir is readable, go to the dir
						//if the dir is not readable, end function
						if(!hasOthersRead(p)) { 
							if(this.hasDebugMsg) {
								System.out.println("hasOthersRead: "+ hasOthersRead(p));//test
							}
							this.statusCode = 403;
							resHeader.remove("Content-Type");
							resHeader.remove("Content-Disposition");
							resHeader.put("Content-Length", "0");
							System.out.println("Cannot read the dir.");
							return;
						}
					}
					else {//if dir not exists, create the folder
						try {
							System.out.println(p.toString()+ "doesn't exist, create the dir.");
							Files.createDirectory(p);
							hasCreatedDirFile = true;
						} catch (IOException e) {
							//System.out.println("e createDirectory");
							e.printStackTrace();
						}
					}
				}
				else { //if is last elt --> file
					try {
						if(ff.exists()) { //file exists
							//System.out.println("hasOthersWrite: "+ hasOthersWrite(p));//test
							//check permission
							if(!hasOthersWrite(p)) {
								this.statusCode = 403;
								resHeader.remove("Content-Type");
								resHeader.remove("Content-Disposition");
								resHeader.put("Content-Length", "0");
								System.out.println("Cannot write the file.");
								return;
							}
						}
						else { //file doesn't exist, create file
							Files.createFile(p);
							hasCreatedDirFile = true;
						}
						
						if(!reqBody.isEmpty()) {
							FileWriter fw;
							File f = p.toFile();
							if(!hasOverwrite) { //don't have overwrite, append the content
								fw = new FileWriter(f, true);
							}
							else { //has overwrite, overwrite the content
								fw = new FileWriter(f);
							}
							fw.write(reqBody);
							fw.close();
							System.out.println("Successfully wrote to the file.");
						}
						else {
							System.out.println("No request body.");
						}
						
						//set if anyone can overwrite it
//						if(!hasOverwrite) {
//							removeOthersWrite(p);
//						}
						if(hasCreatedDirFile) {
							this.statusCode = 201;
						}
						else {
							this.statusCode = 200;
						}
						resHeader.remove("Content-Disposition");
						resHeader.remove("Content-Type");
						resHeader.put("Content-Length", "0");
					} 
					catch (IOException e) {
						e.printStackTrace();
					}
					
				}
			}
			
		}
		
		public byte[] processDownload(String queryDir, String rootDir){
			String[] queryDirArr = queryDir.split("/");
			String fileName = queryDirArr[queryDirArr.length-1];
			
			Path path = Paths.get(rootDir+queryDir);
			File f = path.toFile();
			BufferedInputStream bis = null;
			
			try {
				if(hasOthersRead(path)) { //check permission of a file
					//get content type
					String mimeType = Files.probeContentType(path);
					
					//read the file content
					byte[] mybytearray = new byte[(int) f.length()];
				    bis = new BufferedInputStream(new FileInputStream(f));
				    bis.read(mybytearray, 0, mybytearray.length);
				    
//				    OutputStream os = sock.getOutputStream();
//				    os.write(mybytearray, 0, mybytearray.length);
//				    os.flush();
					
				    this.statusCode = 200;
					
				    //add Content-Length header
				    resHeader.put("Content-Length", Integer.toString(mybytearray.length));
				    //add Content-Type header
				    resHeader.put("Content-Type", mimeType);
				    //add Content-Disposition
				    resHeader.put("Content-Disposition", "attachment ; filename = \"" + fileName + "\"");
				    
				    //close BufferedInputStream
				    if(bis != null) {
				    	bis.close();
				    }
				    
				    return mybytearray;
				}
				else { //file is not readable
					this.statusCode = 403;
					resHeader.remove("Content-Disposition");
					resHeader.remove("Content-Type");
					resHeader.put("Content-Length", "0");
					System.out.println("File not readable. Cannot download this file!");
					
				}
			}
			catch(IOException e) {
				e.printStackTrace();
			}
			return null;
		}
		
		/* if the queryDir starts with /.., return false
		 * if the queryDir is /dir1/../../.. return false
		 * otherwise, return true
		 * */
		public boolean checkSecureAccess(String queryDir) {
			int countDirFile = 0;
			int countUpOneFolder = 0;
			String[] splittedDir = queryDir.split("/");
			
			//test
//			System.out.println("\nprint splittedDir in checkSecureAccess(): ");
//			for(String s : splittedDir) {
//				if(s.isEmpty()) {
//					continue;
//				}
//				System.out.println(s);
//			}
//			System.out.println();
			
			if(splittedDir.length > 0) {
				//loop through the string array and check secure access
				for(String s : splittedDir) {
					//first string s is dir or file
					if(s.isEmpty()) {
						continue;
					}
					
					if(s.equals("..")) {
						countUpOneFolder++;
					} else {
						countDirFile++;
					}
					
					/* case:
					 * rootDir/..
					 * rootDir/dir/../.. --> outside of rootDir
					 * rootDir/dir/../dir/../.. --> outside of rootDir
					 * */
					if(countUpOneFolder>countDirFile) {
						resHeader.remove("Content-Disposition");
						resHeader.remove("Content-Type");
						resHeader.remove("Content-Length");
						this.statusCode = 403;
						return false;
					}
				}
			}
			
			return true;
		}
		
		public boolean hasOthersRead(Path p) {
			try {
				Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(p);
				for(PosixFilePermission pp : permissions) {
//					System.out.println(pp.toString());
					if(pp.toString().equals("OTHERS_READ")) {
						return true;
					}
				}
			}
			catch(IOException e) {
				e.printStackTrace();
			}
			return false;
		}
		
		public boolean hasOthersWrite(Path p) {
			try {
				Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(p);
				for(PosixFilePermission pp : permissions) {
//					System.out.println(pp.toString());
					if(pp.toString().equals("OTHERS_WRITE")) {
						return true;
					}
				}
			}
			catch(IOException e) {
				e.printStackTrace();
			}
			return false;
		}
		
		public void removeOthersWrite(Path p) {
			try {
				Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(p);
				permissions.remove(PosixFilePermission.OTHERS_WRITE);
				System.out.println("after remove OTHERS_WRITE");
				
				//test
				for(PosixFilePermission pp : permissions) {
					System.out.println(pp.toString());
				}
				Files.setPosixFilePermissions(p, permissions);
			}
			catch(IOException e) {
				e.printStackTrace();
			}
		}
		
		public String printResponse() {
			StringBuilder sb = new StringBuilder();
			//status line
			sb.append("HTTP/1.0 ").append(this.statusCodePhrase.get(this.statusCode)).append("\r\n");
			
			//response header line
			//traverse the HashMap and append the key-val pair to sb
			for(Map.Entry<String, String> entry : resHeader.entrySet()) {
				sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
			}
			
			//Date header
			DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
			String dataString = LocalDateTime.now().atZone(ZoneId.of("GMT")).format(formatter);
			sb.append("Date: ").append(dataString).append("\r\n");
     
			sb.append("Server: Concordia Server-HTTP/1.0\r\n");
			sb.append("MIME-version: 1.0\r\n");
			
			//blank line
			sb.append("\r\n");
			
			//entity
			if(!this.resBody.isEmpty()) {
				sb.append(this.resBody);
			}
			resBody = ""; //reset resBody
			return sb.toString();
		}
}
