/*
 * LogMonitor.java
 *
 * Created on May 19, 2006, 6:38 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.thelangenbergs.jabberbot;

import java.lang.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.apache.log4j.*;
import org.apache.commons.cli.*;
import java.io.*;
import java.util.*;
import org.jivesoftware.smackx.muc.MultiUserChat;
/**
 *
 * @author davel
 */
public class LogMonitor implements Runnable {
	private static Category logger = Category.getInstance(LogMonitor.class);
	
	XMPPConnection conn;
	Thread t;
	/** Creates a new instance of LogMonitor */
	public LogMonitor(XMPPConnection c) {
		t = new Thread(this);
		conn = c;
		t.start();
	}
	
	public void run(){
		try{
			processFile();
		}
		catch(IOException e){
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
}
