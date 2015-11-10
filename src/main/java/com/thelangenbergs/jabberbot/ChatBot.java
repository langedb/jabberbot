/*
 * ChatBot.java
 *
 * Created on May 19, 2006, 5:16 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.thelangenbergs.jabberbot;

import org.apache.log4j.Logger;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.MultiUserChat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * connect up to a MUC room and handle requests
 *
 * @author davel
 */
public class ChatBot implements PacketListener {
	private static Logger logger = Logger.getLogger(ChatBot.class);
	
	private XMPPConnection conn;
	private MultiUserChat muc;
	
	private Properties configuration;
	private static String keywords[] = {"getinfo", "time", "sleep", "fortune", "mail"};
	
	/**
	 * Creates a new chatbot listener.
	 * @param c - connection back to server for sending messages
	 * @param m - connection to Chatroom for doing various things
	 * @param config - application configuration object
	 */
	public ChatBot(XMPPConnection c,MultiUserChat m,Properties config) {
		logger.debug("listener registered:");
		conn = c; //for sending back messages
		muc = m;
		configuration = config;
	}

	/**
	 * Processes an incoming packet from the chatroom. Determines if it's a message 
	 * for us or not.
	 * @param packet The incoming message from the chatroom.
	 */
	public void processPacket(Packet packet) {
		//cast the packet as a Message
		Message msg = (Message) packet;
		logger.debug("received message "+msg.getBody());

		String body = msg.getBody();
				
		if(isMessageForMe(body)  && !(msg.getFrom().equals(muc.getNickname()))){
			//we have something to deal with
			String command = getCommand(msg);
			//we have a valid command -- do something with it
			//the reason we spawn off a thread to do the dirty work is because some of our commands will
			//take quite a bit of time to complete and we don't want to block
			new CommandHandler(muc,msg,command,configuration);
		}
	}
	
	/**
	 * Return the nickname of the individual who sent the message.
	 * @param msg The message who we're trying to identify the sender on.
	 * @return the nickname of the person who sent the message.
	 */
	public static String getFrom(Message msg){
		return msg.getFrom().substring(msg.getFrom().indexOf('/')+1);
	}
	
	/**
	 * gets the command out of the message.  Commands of the form nickname: <command>
	 * @param m The Message which we are trying to extract the command from
	 * @return The command from the message
	 */
	protected String getCommand(Message m){
		StringTokenizer st = new StringTokenizer(m.getBody());
		st.nextToken(); //our nickname
		return st.nextToken(); //the command
	}
	
	/**
	 * Sets the nickname for us in the chatroom we are listening to.
	 * @param nick The new nickname
	 * @throws org.jivesoftware.smack.XMPPException if there was a problem setting the new nickname
	 */
	public void setMyNick(String nick) throws XMPPException{
		muc.changeNickname(nick);
	}
	
	/**
	 * Return the nickname that we are connected to the room with
	 * @return Our nickname for the room we are listening in
	 */
	public String getMyNick(){
		return muc.getNickname();
	}

	/**
	 * Determine if this message is for us
	 * @param body
	 * @return true if it is
	 */
	private boolean isMessageForMe(String body) {
		if(body.startsWith(getMyNick())){
			return true;
		}
		else if(body.startsWith("!.")){
			return true;
		}
		else if(body.contains("INC")){
			return true;
		}
		else if(body.contains("PRB")){
			return false;
		}
		else if(body.contains("CHG")){
			return false;
		}
		
		return false;
	}
}

class CommandHandler implements Runnable {
	Thread t;
	
	private MultiUserChat conn;
	private Message mesg;
	private String cmd;
	private static Logger logger = Logger.getLogger(CommandHandler.class);
	
	private Properties configuration;
	private Pattern serviceNowPattern;
	
	public CommandHandler(MultiUserChat c, Message m, String command, Properties config){
		t = new Thread(this);
		conn = c;
		mesg = m;
		cmd = command;
		configuration = config;
		
		serviceNowPattern = Pattern.compile("INC\\p{Digit}{7}");
		
		
		t.setName(cmd+" handler");
		t.start();
	}
	
	/**
	 * handle the command do the work
	 */
	public void run(){
		//ok so if we are in here we have a valid command as defined in ChatBot.keywords
		try{
			
			String body = mesg.getBody();
			Matcher m = serviceNowPattern.matcher(body);
			
			
			if(body.contains("INC")){
				//grab the INC word
				String incident = null;
				while(m.find()){
					incident = body.substring(m.start(), m.end());
					conn.sendMessage("https://uchicago.service-now.com/incident.do?sysparm_query=number="+incident);
				}
				return;
			}
			
			if(cmd.equals("sleep")){
				
				try{
					t.sleep(10000);
					conn.sendMessage("slept for 10 seconds");
				} catch(Exception e){
					logger.error(e.getMessage(), e);
				}
			} else if (cmd.equals("time")){
				TimeZone tz = TimeZone.getTimeZone("GMT:00");
				SimpleDateFormat sdf = new SimpleDateFormat("EE MMM d yyyy HH:mm:ss z");
				sdf.setTimeZone(tz);
				conn.sendMessage("The current time is: "+sdf.format(new Date()));
			} 
			else if(cmd.equals("fortune")){
				getFortune();
			}
		}
		catch(Exception e){
			logger.error(e.getMessage(),e);
			try{
				conn.sendMessage("I'm sorry but I'm unable to complete your request -- please see my log for more details");
			}
			catch(XMPPException ex){
				logger.error(ex.getMessage(),ex);
			}
		}
	}
	
	private void getFortune() throws XMPPException {
		Runtime r = Runtime.getRuntime();
		
		String command = "fortune";
		
		logger.debug("execing command "+command);
		
		try{
			Process p = r.exec(command);
			
			//get the output
			logger.debug("opening output from command for reading");
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			
			String message = "";
			String line;
			while((line = br.readLine()) != null){
				message += line+"\n";
			}
			
			br.close();
			conn.sendMessage(message);
		}
		catch(IOException e){
			logger.warn(e.getMessage(),e);
			conn.sendMessage(mesg.getFrom()+": fortune is not installed on this machine.");
		}
		
	}
}
