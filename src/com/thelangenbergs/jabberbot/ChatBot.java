/*
 * ChatBot.java
 *
 * Created on May 19, 2006, 5:16 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.thelangenbergs.jabberbot;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.*;
import org.apache.log4j.*;
import org.apache.commons.cli.*;
import java.io.*;
import java.util.*;
import java.lang.*;
import org.apache.xmlrpc.*;
import org.jivesoftware.smackx.muc.MultiUserChat;

/**
 * connect up to a MUC room and handle requests
 *
 * @author davel
 */
public class ChatBot implements Runnable {
	private static Logger logger = Logger.getLogger(ChatBot.class.getName());
	
	Thread t;
	XMPPConnection conn;
	MultiUserChat muc;
	/** Creates a new instance of ChatBot */
	public ChatBot(XMPPConnection c) {
		conn = c;
		t = new Thread(this);
		
		//join the chatroom
		muc = new MultiUserChat(conn,"davelchat@conference.im.uchicago.edu");
		try {
			muc.join("jabberbot");
		} catch (XMPPException ex) {
			logger.error(ex.getMessage(),ex);
		}
		
		t.start();
	}

	public void run(){
		while(true){
			Message m = muc.nextMessage();
			
			logger.debug("received message: "+m.getBody());
		}
	}
	
}
