package com.udpimplementation.comp445a3.CustomSocketServer;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class ServerCLIImplement {
	//all variables
	private CommandLine cmd=null;
	private Options helpOptions;
	public boolean hasDebugMsg = false; //parse -v
	public boolean hasDir = false; //parse -d
	public boolean hasPortNum = false; //parse -p
	private final String DEFAULT_DIR = "/home/yilu/Documents"; //default root path
	private String argsDir = ""; //root dir
	private String fullDirPath = DEFAULT_DIR;
	private final int DEFAULT_PORT_NUM = 8080; //default port number
	private int argsPortNum = 0; //parse -p
	private int finalPortNum = DEFAULT_PORT_NUM;
	
	//all command constant
	private final String DEBUGMSG = "v";
	private final String DIR = "d";
	private final String PORT_NUM = "p";
	private final String HELP = "help";
	
	//constructor
	public ServerCLIImplement(String[] args) {
		//initialize postOptions and getOptions
		initializeOptions();
		
		if(validateArgs(args)) {
			//System.out.println("Valid args");
			parseOptions(args);
		}
		else {
//			System.out.println("Invalid args");
			printHelpMsg();
		}
	}
		
	//config options and option group
	private void initializeOptions() {
		helpOptions = new Options();
		
		helpOptions.addOption(DEBUGMSG, false, "Prints debugging messages.");
		helpOptions.addOption(PORT_NUM, true, "Specifies the port number that the server will listen and serve at. Default is 8080.");
		helpOptions.addOption(DIR, true, "Specifies the directory that the server will use to read/write requested files. Default is the current directory when launching the application.");
		helpOptions.addOption(HELP, false, "Print help information");
	}
	
	//check the validity of command line
	private boolean validateArgs(String[] args) {
		//check httpfs help and httpfs (no arg) and httpfs (wrong args)
		if(args.length == 0 || (args.length == 1 && args[0].toLowerCase().equals(HELP))) {
//			printHelpMsg();
			return false;
		}
		
		//loop through args and check if has invalid options
		for(int i=0; i<args.length; i++) {
			if(args[i].charAt(0) == '-') {
				//check options other than -v, -d, -p
				if((args[i].charAt(1) != 'v' && args[i].charAt(1) != 'd' && args[i].charAt(1) != 'p') || args[i].length() > 2) {
//					System.out.println("v" + (args[i].charAt(1) != 'v'));
//					System.out.println("d " + (args[i].charAt(1) != 'd'));
//					System.out.println("p " + (args[i].charAt(1) != 'p'));
//					System.out.println("d " + (args[i].charAt(1) != 'd'));
					System.out.println("Invalid command. in validateArgs");
					printHelpMsg();
					return false;
				}
			}
		}
		return true;
	}
	
	//parse the value of the command line
	protected void parseOptions(String[] args){
		//parse all options
		CommandLineParser parser = new DefaultParser();
		try {
			//change options according to method
			cmd = parser.parse(helpOptions, args);
			
			//check if has options other than -v, -p, -d
			if(!cmd.hasOption(DEBUGMSG) && !cmd.hasOption(DIR) && !cmd.hasOption(PORT_NUM)) {
//				System.out.println("cmd.hasOption(DEBUGMSG)" + cmd.hasOption(DEBUGMSG));
//				System.out.println("cmd.hasOption(DIR)" + cmd.hasOption(DIR));
//				System.out.println("cmd.hasOption(PORT_NUM)" + cmd.hasOption(PORT_NUM));
				System.out.println("Invalid command. in parseOptions");
				printHelpMsg();
			}
			
			if(cmd.hasOption(DIR)) {
				this.hasDir = true;
				this.argsDir = cmd.getOptionValue(DIR);
				
				//***validate the root directory
				//must exist and must be a directory
				Path updatedPath = Paths.get(this.argsDir);
				if(Files.isDirectory(updatedPath) && Files.exists(updatedPath)) {
					this.fullDirPath = this.argsDir;
					System.out.println("Set root directory to "+this.fullDirPath);
				}
				else {
					this.fullDirPath = this.DEFAULT_DIR;
					System.out.println("Invalid root directory. Set root directory to default directory "+ this.DEFAULT_DIR);
				}
			}
			
			//has option -p and parse port number
			if(cmd.hasOption(PORT_NUM)) {
				this.argsPortNum = Integer.parseInt(cmd.getOptionValue(PORT_NUM));
				this.hasPortNum = true;
				
				//check if the parsed port number is reserved or invalid
				if(this.argsPortNum >= 1 && this.argsPortNum <= 1023) {
					this.finalPortNum = DEFAULT_PORT_NUM;
					System.out.println("Port number "+ this.argsPortNum + " is reserved. Set port number to default port number 8080.");
				} 
				else if (this.argsPortNum<= 65535){
					this.finalPortNum = this.argsPortNum;
					System.out.println("Current port number is "+this.argsPortNum);
				}
				else {
					this.finalPortNum = DEFAULT_PORT_NUM;
					System.out.println("Non-existing port number. Set port number to default port number 8080.");
				}
					
			}
			
			//has option -v
			if(cmd.hasOption(DEBUGMSG)) {
				System.out.println("has v option.");
				this.hasDebugMsg = true;
			}
		} 
		catch(ParseException e) {
			System.out.println(e.getMessage());
			printHelpMsg();
		} 
	}
	
	private void printHelpMsg() {
		String cmlSyntax = "usage: httpfs [-v] [-p PORT] [-d PATH-TO-DIR]";
		String header = "\nhttpfs is a simple file server.\n\n";
		String footer = "\nUse \"httpfs help\" for more information about a command.";
		new HelpFormatter().printHelp(cmlSyntax, header, helpOptions, footer);
		System.exit(0);
	}
	
	public void setFinalPortNum(int portNum) {
		this.finalPortNum = portNum;
	}
	
	public int getFinalPortNum() {
		return this.finalPortNum;
	}
	
	public void setRootDirPath(String dirPath) {
		this.fullDirPath = dirPath;
	}
	
	public String getRootDirPath() {
		return this.fullDirPath;
	}
	
	public boolean getHasDebugMsg() {
		return this.hasDebugMsg;
	}
}
