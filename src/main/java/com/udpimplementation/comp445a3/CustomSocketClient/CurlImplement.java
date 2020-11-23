package com.udpimplementation.comp445a3.CustomSocketClient;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;


public class CurlImplement {
	//all variables
	private CommandLine cmd=null;
	private URL argUrl;
	private Options postOptions;
	private Options getOptions;
	private Options helpOptions;
	private String method;
	private HashMap<String, String> headers = new HashMap<>(); //parse key-value pair for -h
	private String argsBody; //parse -d
	private String argsFile; //parse -f
	private String argsOutFileName;
	public boolean hasVerbose = false; //parse -v
	public boolean hasHeaders = false;
	public boolean hasData = false;
	public boolean hasFile = false;
	public boolean hasOutputFile = false;
	
	//all command constant
	private final String VERBOSE = "v";
	private final String HEADERS = "h";
	private final String DATA = "d";
	private final String FILE = "f";
	private final String OUT = "o";
	private final String GET_OPTION = "get";
	private final String POST_OPTION = "post";
	private final String HELP = "help";
	
	
	
	//constructor
	public CurlImplement(String[] args) {
		//initialize postOptions and getOptions
		initializeOptions();
		
		if(validateArgs(args)) {
			//System.out.println("Valid args");
			parseOptions(args);
		}
		else {
			System.out.println("Invalid args");
			printHelpMsg();
		}
	}
	
	//config options and option group
	private void initializeOptions() {
		postOptions = new Options();
		getOptions = new Options();
		helpOptions = new Options();
		OptionGroup optionGroup = new OptionGroup();
		
		postOptions.addOption(VERBOSE, false, "Prints the details of the response such as protocol, status, and headers.");
		postOptions.addOption(HEADERS, true, "Associates headers to HTTP Request with the format 'key:value'.");
		postOptions.addOption(OUT, true, "Allow the HTTP client to write the body of the response to the specified file instead of the console.");
//		postOptions.addOption(OVERWRITE, false, "Overwrite the file specified by the method in the data directory with the content of the body of the request.");
		
		getOptions.addOption(VERBOSE, false, "Prints the details of the response such as protocol, status, and headers.");
		getOptions.addOption(HEADERS, true, "Associates headers to HTTP Request with the format 'key:value'.");
		getOptions.addOption(OUT, true, "Allow the HTTP client to write the body of the response to the specified file instead of the console.");
		
		helpOptions.addOption(GET_OPTION, false, "Executes a HTTP GET request and prints the response.");
		helpOptions.addOption(POST_OPTION, false, "Executes a HTTP POST request and prints the response.");
		helpOptions.addOption(HELP, false, "Print help information");
		
		//option -d and -f are mutually exclusive
		optionGroup.addOption(
				Option.builder(DATA)
//				.longOpt("inline data")
				.hasArg()
				.desc("Associates an inline data to the body HTTP POST Request.")
				.build());
		optionGroup.addOption(
				Option.builder(FILE)
//				.longOpt("file")
				.hasArg()
				.desc("Associates the content of a file to the body HTTP POST request.")
				.build());
		
		//set required //post request can have empty entity body, get request dosen't have entity body
		optionGroup.setRequired(false);
		
		postOptions.addOptionGroup(optionGroup);
	}
	
	//check the validity of command line
	private boolean validateArgs(String[] args) {
		//check httpc help and httpc (no arg) and httpc (wrong args)
		if(args.length <2) {
			printHelpMsg();
		}
		
		//check httpc help get/ httpc help post
		if(args.length == 2 && args[0].toLowerCase().equals(HELP)) {
			if(args[1].toLowerCase().equals(GET_OPTION)) {
				new HelpFormatter().printHelp("httpc get [-v] [-h key:value] URL"
						+ "\n\nGet executes a HTTP GET request for a given URL.\n\n", getOptions);
				System.exit(0);
			}
			else if(args[1].toLowerCase().equals(POST_OPTION)) {
				new HelpFormatter().printHelp("httpc post [-v] [-h key:value] [-d inline-data] [-f file] URL"
									+ "\n\nPost executes a HTTP POST request for a given URL with inline data or form file" 
									+ "\n\nEither [-d] or [-f] can be used but not both.\n\n", postOptions);
				System.exit(0);
			}
			else {
				System.out.println("Invalid command.");
				printHelpMsg();
			}
		}
		//check httpc (get|post) [-v] (-h 'k:v')* [-d inline-data] [-f file] URL
		if(args.length >=2) {
			//if the fist argument is not help, get or post
			if(!args[0].toLowerCase().equals(GET_OPTION) && !args[0].toLowerCase().equals(POST_OPTION)) {
				System.out.println("Invalid command.");
				printHelpMsg();
			}
			else {
				this.method = args[0].toLowerCase();
			}
		}
		
		//parse URL
		try {
			//parse url string without quotes
			String urlString = args[args.length -1];
			if(urlString.charAt(0) == '\'') {
				urlString = urlString.substring(1, urlString.length()-1);
			}
			
			argUrl = new URL(urlString);
		}
		catch(MalformedURLException e) {
			System.out.println("Invalid URL, please try again.");
			return false;
		}
		return true;
	}
	
	//parse the value of the command line
	protected void parseOptions(String[] args){
		//parse all options
		CommandLineParser parser = new DefaultParser();
		try {
			//change options according to method
			cmd = parser.parse(postOptions, args);
			
			//post request cannot have both -f and -d
			if(cmd.hasOption(DATA) && cmd.hasOption(FILE) && this.method.equals("post")) {
				System.out.println("POST request cannot have both -f and -d.");
				printHelpMsg();
			}
			
			//get request cannot have -f or -d
			if(this.method.equals("get") && (cmd.hasOption(DATA) || cmd.hasOption(FILE))) {
				System.out.println("GET request cannot have -f or -d");
				printHelpMsg();
			}
			
			if(cmd.hasOption(HEADERS)) {
				this.hasHeaders = true;
				String[] keyValPair;
				for (String header : cmd.getOptionValues(HEADERS)) {
					keyValPair = header.split(":");
                    this.headers.put(keyValPair[0], keyValPair[1]);
                }
				
				//traverse hashmap and print it out //test
//				for(Map.Entry<String,String> keyVal : this.headers.entrySet()) {
//					System.out.println(keyVal.getKey()+":"+keyVal.getValue());
//				}
			}
			if(cmd.hasOption(DATA)) {
				this.hasData = true;
				this.argsBody = cmd.getOptionValue(DATA);
			}
			if(cmd.hasOption(VERBOSE)) {
				this.hasVerbose = true;
			}
			if(cmd.hasOption(FILE)) {
				this.hasFile = true;
				this.argsFile = cmd.getOptionValue(FILE);
			}
			if(cmd.hasOption(OUT)) {
				this.hasOutputFile = true;
				argsOutFileName = cmd.getOptionValue(OUT);
			}
//			if(cmd.hasOption(OVERWRITE)) {
//				hasOverwrite = true;
//			}
		} 
		catch(ParseException e) {
			System.out.println("parseOptions ParseException");
			System.out.println(e.getMessage());
			printHelpMsg();
		} 
//		catch (IOException e) {
//			System.out.println("parseOptions IOException");
//			System.out.println(e.getMessage());
//			printHelpMsg();
//		}
	}
	
	private void printHelpMsg() {
		//String headers = "httpc (get|post) [-v] (-h 'k:v')* [-d inline-data] [-f file] URL";
		String cmlSyntax = "httpc command [arguments]";
		String header = "\nhttpc is a curl-like application but supports HTTP protocol only.\n\n";
		String footer = "\nUse \"httpc help [command]\" for more information about a command.";
		new HelpFormatter().printHelp(cmlSyntax, header, helpOptions, footer);
		System.exit(0);
	}
	
	public String getMethod() {
		return this.method;
	}
	
	public URL getUrl() {
		return this.argUrl;
	}
	
	public boolean isVerbose() {
		return this.hasVerbose;
	}
	
	public HashMap<String, String> getHeaders(){
		return this.headers;
	}
	
	public String getArgsBody() {
		return this.argsBody;
	}
	
	public String getArgsFile() {
		return this.argsFile;
	}
	
	public String getOutFileName() {
		return argsOutFileName;
	}
	
//	public Boolean getHasOverwrite() {
//		return this.hasOverwrite;
//	}
}
