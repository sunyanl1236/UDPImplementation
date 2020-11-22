package com.udpimplementation.comp445a3;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.udpimplementation.comp445a3.CustomSocketServer.MultiServerThread;
import com.udpimplementation.comp445a3.CustomSocketServer.ServerCLIImplement;

public class httpfs {
	final static ReadWriteLock rwlock = new ReentrantReadWriteLock();
	final static Lock rlock = rwlock.readLock();
	final static Lock wlock = rwlock.writeLock();
	
	public static void main(String[] args) {
		ServerSocket serverSocket = null;
		
		
		ServerCLIImplement parseCommand = new ServerCLIImplement(args);
		int portNumber = parseCommand.getFinalPortNum();
		boolean listening = true;
		int count=0;
		
		try {
			serverSocket = new ServerSocket(portNumber);
			while(listening) {
				Socket cs = serverSocket.accept();
				new MultiServerThread(cs, parseCommand.getRootDirPath(), parseCommand.getHasDebugMsg(), rlock, wlock).start();
			}
			
			if(serverSocket != null) {
				serverSocket.close();
			}
		}
		catch(IOException e) {
			System.err.println("Could not listen on port "+ portNumber);
			System.exit(-1);
		}
	}

}
