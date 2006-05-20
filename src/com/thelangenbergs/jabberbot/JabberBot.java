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
	private String _user;
	private String _password;
	
	private XMPPConnection conn;
	private Roster ross;
	
	/** Creates a new instance of jabberBot */
	public JabberBot(String[] args) {
		doOptions(args);
		
		//for starters, login to jabber and send davel a message
		try{
			logger.debug("Connecting to "+_server);
			conn = new SSLXMPPConnection(_server);
			
			if(conn.isConnected()){
				logger.debug("Connected");
			}
			else{ 
				logger.error("failed to connect to "+_server);
				System.exit(-1);
			}
			
			logger.debug("logging in as "+_user);
			conn.login(_user,_password, "newJabberBot");
			
			if(conn.isAuthenticated()){
				logger.debug("success");
			}
			else{
				logger.error("failed to login as "+_user);
			}
			
			logger.debug("getting roster");
			ross = conn.getRoster();
			ross.setSubscriptionMode(Roster.SUBSCRIPTION_ACCEPT_ALL);//make the roster accept anybody who wants to subscribe
			
			logger.debug("Spawning log monitor");
			LogMonitor lm = new LogMonitor(conn);
			logger.debug("spawned");
			
			logger.debug("joining chatroom");
			//login to the chatroom
			//we don't want any history
			DiscussionHistory hist = new DiscussionHistory();
			hist.setMaxStanzas(0);
			MultiUserChat muc  = new MultiUserChat(conn,"davelchat@conference.im.uchicago.edu");
			muc.join("jabberbot","",hist,SmackConfiguration.getPacketReplyTimeout());
			muc.addMessageListener(new ChatBot(conn,muc));
			
			
		}
		catch(Exception e){
			logger.error(e.getMessage(),e);
		}
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

		Option user = OptionBuilder.withArgName("user")
									.hasArg()
									.isRequired()
									.withDescription("Username to login as")
									.create("u");
		
		Option password = OptionBuilder.withArgName("passsword")
										.hasArg()
										.isRequired()
										.withDescription("password for the user")
										.create("p");
		
		Option server = OptionBuilder.withArgName("server")
									.hasArg()
									.withDescription("server to connect to")
									.create("s");

		commandOptions.addOption(server);
		commandOptions.addOption(user);
		commandOptions.addOption(password);
		
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
		
		//server
		if(cmd.hasOption("s")){
			_server = cmd.getOptionValue("s");
		}
		
		//user
		if(cmd.hasOption("u")){
			_user = cmd.getOptionValue("u");
		}
		
		if(cmd.hasOption("p")){
			_password = cmd.getOptionValue("p");
		}
		
	}
	
}
