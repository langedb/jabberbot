/*
 * jabberBot.java
 *
 * Created on May 11, 2005, 11:11 AM
 */

package com.thelangenbergs.jabberbot;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.apache.log4j.*;
import org.apache.commons.cli.*;
import java.io.*;
import java.util.*;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;

/**
 *
 * @author davel
 */
public class JabberBot {
	
	private static Logger logger = Logger.getLogger(JabberBot.class.getName());
	
	private String _server;
	private int _port;
	private String _user;
	private String _password;
	private String _config;
	/** the directory we should be watching for chatrooms/pages */
	private String _watchDir;
	/** the nickname we're logging into all chatrooms with */
	private String _nickname;
	/** what chatserver to connect to */
	private String _chatServer;
	/** how much history to save so we can mail later */
	private int _chatSaveHistory;
	/** how long to sleep before scanning for pages */
	private int _scanDelay;
	
	
	private Properties configuration;
	
	private XMPPConnection conn;
	private Roster ross;
	
	/**
	 * Read the configuration file
	 */
	protected void readConfigFile() throws FileNotFoundException, IOException {
		File confFile = new File(_config);
		
		if(confFile.canRead()){
			logger.debug("loading config file");
			configuration = new Properties();
			configuration.load(new FileInputStream(confFile));
		}
		else{
			logger.warn("Unable to read "+_config);
		}
	}
	
	/**
	 * Run through the configuration file and pull the configuration into the 
	 * program
	 */
	protected void processConfiguration() throws NumberFormatException {
		_server = configuration.getProperty("jabber.server");
		_port = Integer.parseInt(configuration.getProperty("jabber.port"));
		_user = configuration.getProperty("jabber.user");
		_password = configuration.getProperty("jabber.password");
		
		_watchDir = configuration.getProperty("watchdir");
		_nickname = configuration.getProperty("jabber.muc.nickname");
		_chatServer = configuration.getProperty("jabber.muc.servicename");
		
		_scanDelay = Integer.parseInt(configuration.getProperty("scandelay"));
		_chatSaveHistory = Integer.parseInt(configuration.getProperty("jabber.muc.saveHistory"));
	}
	
	/** Creates a new instance of jabberBot */
	public JabberBot(String[] args) {
		doOptions(args);
		//for starters, login to jabber and send davel a message
		try{
			readConfigFile();
			processConfiguration();
			
			//keep trying to connect
			boolean connected = false;
			while(!connected){
				logger.debug("Connecting to "+_server);
				try{
					ConnectionConfiguration cfg = new ConnectionConfiguration(_server,_port);
					cfg.setSecurityMode(ConnectionConfiguration.SecurityMode.enabled);
					conn = new XMPPConnection(cfg);
					conn.connect();
				}
				catch(Exception e){
					logger.warn(e.getMessage());
				}
				
				if(conn != null && conn.isConnected()){
					logger.debug("Connected");
					connected = true;
				}
				else{
					logger.warn("Failed to connect to "+_server+" trying again");
				}
			}
			logger.debug("logging in as "+_user+"/"+_nickname);
			conn.login(_user,_password, _nickname);
			
			if(conn.isAuthenticated()){
				logger.debug("success");
			}
			else{
				logger.error("failed to login as "+_user);
			}
			
			logger.debug("getting roster");
			ross = conn.getRoster();
			ross.setSubscriptionMode(Roster.SubscriptionMode.accept_all);//make the roster accept anybody who wants to subscribe
			
			//now scan the watchDirectory and figure out chatrooms we need to connect to
			logger.debug("begging scan of "+_watchDir+" for chatrooms to connect to");
			ArrayList rooms = scanForRooms(_watchDir);
			
			//ok, now join up chatbots to each room 
			Hashtable roomObjs = new Hashtable();
			for(int i=0; i<rooms.size(); i++){
				String room = (String) rooms.get(i);
				
				logger.debug("joining "+room);
				//don't load up any history when we connect so we don't end up answering a ton of queries
				DiscussionHistory hist = new DiscussionHistory();
				hist.setMaxStanzas(0);
				MultiUserChat muc = new MultiUserChat(conn,room+"@"+_chatServer);
				muc.join(_nickname,getRoomPassword(room),hist,SmackConfiguration.getPacketReplyTimeout());
				muc.addMessageListener(new ChatBot(conn,muc));
				//start a page-scanner for this room
				new PageScanner(muc,new File(_watchDir+System.getProperty("file.separator")+room),_scanDelay);

				//store this room in a hashtable.  We may want in the future
				//to do something with these rooms.
				roomObjs.put((String)rooms.get(i),muc);
			}
			
			//go infinite loop & let the other threads handle things
			while(true){
				Thread.sleep(999999999);
			}
			
		}
		catch(Exception e){
			logger.error(e.getMessage(),e);
		}
	}
	
	/**
	 * Look for and retreive the password for a room
	 *
	 * @param room the room which we believe has a password on it
	 * @return the room password or empty-string
	 */
	protected String getRoomPassword(String room) throws IOException {
		File passwd = new File(_watchDir+System.getProperty("file.separator")+room+".passwd");
		
		if(passwd.exists()){
			BufferedReader br = new BufferedReader(new FileReader(passwd));
			String line = br.readLine().trim();
			
			return line;
		} else{
			logger.debug("no password exists for "+room);
			return "";
		}
	}
	
	/**
	 * Scan a directory for subdirectories.  Return all sub-directories in that
	 * directory
	 */
	protected ArrayList scanForRooms(String directory){
		ArrayList results = new ArrayList();
		
		File dirLvl = new File(directory);
		if(!dirLvl.isDirectory()){
			throw new IllegalArgumentException(directory+" is not a directory");
		}
		
		for(int i=0; i<dirLvl.list().length; i++){
			String candidate = dirLvl.list()[i];
			
			logger.debug("candidate is "+candidate);
			
			File isADir = new File(directory+System.getProperty("file.separator")+candidate);
			
			if(isADir.isDirectory()){
				logger.debug("adding "+candidate+" to list of directories to watch for pages");
				results.add(candidate);
			}
		}
		
		return results;
	}
	
	
	public static void main(String args[]){
		PropertyConfigurator.configure(JabberBot.class.getResource("/conf/log4j.properties"));
		logger.debug("Logging successfully initialized");
		
		new JabberBot(args);
	
	}
	
	/**
	 * Process the command line arguments
	 * @param args input args from the command line
	 */
	protected void doOptions(String args[]){
		Options commandOptions = new Options();
		commandOptions.addOption("v","verobse",false,"log level = info");
		commandOptions.addOption("d","debug",false,"log level = debug");
		commandOptions.addOption("h","help",false,"display help");

		Option config = OptionBuilder.withArgName("config")
									.hasArg()
									.withDescription("configuration file location")
									.create("c");
		
		commandOptions.addOption(config);
		
		//now create the CLI parser
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		
		try{
			cmd = parser.parse(commandOptions,args);
		}
		catch(org.apache.commons.cli.ParseException e){
			logger.fatal(e.getMessage());
			new HelpFormatter().printHelp("jabberbot",commandOptions,true);
			System.exit(-1);
		}
		
		//check if we have any options passed in, if not print help and exit
		if(cmd.hasOption("h")){
			new HelpFormatter().printHelp("jabberbot",commandOptions,true);
			System.exit(0);
		}
	
		if(cmd.hasOption("d")){
			logger.getRootLogger().setLevel(Level.DEBUG);
			logger.debug("logging set to debug");
		}
		
		if(cmd.hasOption("v")){
			logger.setLevel(Level.INFO);
			logger.info("verbose logging turned on");
		}
		
		if(cmd.hasOption("c")){
			_config = cmd.getOptionValue("c");
		}
	}
	
}
