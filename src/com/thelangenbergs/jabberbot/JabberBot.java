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
			conn.login(_user,_password, "JabberBot");
			
			if(conn.isAuthenticated()){
				logger.debug("success");
			}
			else{
				logger.error("failed to login as "+_user);
			}
			
			logger.debug("getting roster");
			ross = conn.getRoster();
			ross.setSubscriptionMode(Roster.SUBSCRIPTION_ACCEPT_ALL);//make the roster accept anybody who wants to subscribe
			
			//login to the chatroom
			ChatBot cb = new ChatBot(conn);
			
			processFile();
			
		}
		catch(Exception e){
			logger.error(e.getMessage(),e);
		}
	}
	
	/**
	 * Do the work -- open catalina.out and watch for exceptions to show up
	 */
	protected void processFile() throws java.io.IOException{
		Runtime r = Runtime.getRuntime();
		
		String command = "/bin/tail -f /opt/jakarta-tomcat/logs/catalina.out";
		
		logger.debug("execing command "+command);
		Process p = r.exec(command);
		
		//get the output
		logger.debug("opening output from command for reading");
		BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
		
		String line;
		String body = "";
		String Subject = "";
		
		int stackCounter = 0;
		
		while((line = br.readLine()) != null){
			
			logger.debug(line);
			
			//look if there's an exception
			if(line.indexOf("Exception")>0){
				logger.info("Detected Exception");
				//we've found an exception
				body += line;
				stackCounter++;
			}
			
			//UAMS Exceptions
			if(line.indexOf("at NSITUAMS") > 0){
				Subject = "NSITUAMS Exception";
			}
			
			//AAMS Exceptions
			if(line.indexOf("at NSITAAMS")> 0){
				Subject = "NSITAAMS Exception";
			}
			
			//AMSXML exception
			if(line.indexOf("at AMSXML")>0){
				Subject = "AMSXML Exception";
			}
			
			if(stackCounter > 0){
				//we've detected an exception, and are now recording the stack
				body += line;
				stackCounter++;
			}
			
			//detect Class file updates and page on thoes too
			if(line.startsWith("WebappClassLoader")){
				
				logger.debug("found new class");
				java.util.StringTokenizer str = new java.util.StringTokenizer(line);
				
				str.nextToken();
				str.nextToken();
				String updated = str.nextToken();
				logger.debug(updated);
				java.util.StringTokenizer st = new java.util.StringTokenizer(updated,"/");
				while(st.hasMoreTokens()){
					String classfile = st.nextToken();
					
					if(classfile.startsWith("classes")){
						String classname = st.nextToken();
						classname = classname.substring(0,classname.indexOf("."));
						Subject = "New version of "+classname+" detected";
						body = "A new version of "+classname+" has been installed";
						notifyUsers(Subject,body);
						Subject = "";
						body = "";
					}
				}
			}
			
			
			//we have enough of the stack to send
			if(stackCounter == 15){
				logger.info("Notifying folks about exception");
				notifyUsers(Subject, body);
				
				//reset
				stackCounter = 0;
				body = "";
				Subject = "";
			}
			
		}
	}
	
	/**
	 * send a message to all users in the roster
	 */
	protected void notifyUsers(String subject, String message){
		//get a fresh roster
		for(Iterator i = conn.getRoster().getEntries(); i.hasNext();){
			RosterEntry re = (RosterEntry) i.next();
			
			logger.info("Sending message to: "+re);
			
			Message msg = new Message(re.getUser());
			msg.setSubject(subject);
			msg.setBody(message);
			conn.sendPacket(msg);
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
			logger.setLevel(Level.DEBUG);
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
