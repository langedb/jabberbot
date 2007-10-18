/*
 * ChatBot.java
 *
 * Created on May 19, 2006, 5:16 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.thelangenbergs.jabberbot;

import java.net.MalformedURLException;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.*;
import org.apache.log4j.*;
import org.apache.commons.cli.*;
import java.io.*;
import java.util.*;
import java.lang.*;
import org.apache.xmlrpc.*;
import java.text.*;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;

/**
 * connect up to a MUC room and handle requests
 *
 * @author davel
 */
public class ChatBot implements PacketListener {
	private static Category logger = Category.getInstance(ChatBot.class.getName());
	
	private XMPPConnection conn;
	private MultiUserChat muc;
	
	/** directory we're watching for pages */
	private File _watchDir;
	
	private static String keywords[] = {"getinfo", "time", "sleep"};
	
	/**
	 *
	 * @param c - connection back to server for sending messages
	 * @param m - connection to Chatroom for doing various things
	 */
	public ChatBot(XMPPConnection c,MultiUserChat m, File watchDir) {
		logger.debug("listener registered:");
		conn = c; //for sending back messages
		muc = m;
		
	}

	public void processPacket(Packet packet) {
		//cast the packet as a Message
		Message msg = (Message) packet;
		logger.debug("received message "+msg.getBody());

		if(msg.getBody().startsWith(getMyNick())){
			//we have something to deal with
			String command = getCommand(msg);
			try{
				if(validateCommand(msg,command)){
					//we have a valid command -- do something with it
					//the reason we spawn off a thread to do the dirty work is because some of our commands will
					//take quite a bit of time to complete and we don't want to block
					new CommandHandler(muc,msg,command);
				}
			}
			catch(XMPPException e){
				logger.error(e.getMessage(),e);
			}
		}
	}
	
	protected boolean validateCommand(Message msg,String command) throws XMPPException {
		boolean found = false;
		for(int i=0; i<keywords.length; i++){
			if(command.equalsIgnoreCase(keywords[i])){
				found = true;
			}
		}
		
		if(!found){
			muc.sendMessage(getFrom(msg)+": unrecnogized command");
		}
		return found;
	}
	
	public static String getFrom(Message msg){
		return msg.getFrom().substring(msg.getFrom().indexOf('/')+1);
	}
	
	/**
	 * gets the command out of the message.  Commands of the form nickname: <command>
	 */
	protected String getCommand(Message m){
		StringTokenizer st = new StringTokenizer(m.getBody());
		st.nextToken(); //our nickname
		return st.nextToken(); //the command
	}
	
	public void setMyNick(String nick) throws XMPPException{
		muc.changeNickname(nick);
	}
	
	public String getMyNick(){
		return muc.getNickname();
	}
}

class CommandHandler implements Runnable {
	Thread t;
	
	private MultiUserChat conn;
	private Message mesg;
	private String cmd;
	private static Category logger = Category.getInstance(CommandHandler.class.getName());
	
	public CommandHandler(MultiUserChat c, Message m, String command){
		t = new Thread(this);
		conn = c;
		mesg = m;
		cmd = command;
		t.start();
	}
	
	/**
	 * handle the command do the work
	 */
	public void run(){
		//ok so if we are in here we have a valid command as defined in ChatBot.keywords
		try{
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
			} else if (cmd.equals("getinfo")){
				getInformation();
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
	
	protected void getInformation() throws MalformedURLException {
		XmlRpcClient xml = new XmlRpcClient("https://cnet.uchicago.edu/ams/servlet/AMSXMXLSERV");
		
		
	}
}
