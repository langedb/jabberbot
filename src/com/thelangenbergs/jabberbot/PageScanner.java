/*
 * PageScanner.java
 *
 * Created on October 18, 2007, 3:40 PM
 *
 */

package com.thelangenbergs.jabberbot;

import org.jivesoftware.smack.*;
import org.apache.log4j.*;
import java.io.*;
import org.jivesoftware.smackx.muc.MultiUserChat;

/**
 * Watch a directory for new files to appear.  When a new file appears, read it,
 * write the contents back to the connect chatroom, delete the file, continue watching
 *
 * @author David Langenberg <davel@uchicago.edu>
 */
public class PageScanner implements Runnable {
	
	Thread t;
	
	private MultiUserChat conn;
	
	private File _watchDir;
	
	private int sleeptime;
	
	private boolean stopThisMonitor = false;
	
	private static Category logger = Category.getInstance(PageScanner.class.getName());
	
	public void run(){
	
		try{
			while(true){
				
				scanForPages();
				
				//Thread.stop() is deprecated -- documentation says we should
				//check a flag and return from run() if we want a thread to stop
				if(stopThisMonitor){
					return;
				}

				t.sleep(sleeptime*1000);
			}
		}
		catch(IOException ioe){
			logger.error(ioe.getMessage(), ioe);
			try{
				conn.sendMessage("I've encountered an error -- shutting down paging scanner for this room");
			}
			catch(XMPPException e){
				logger.error(e.getMessage(),e);
			}
		}
		catch(Exception e){
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * Scans the paging directory for paging files.  if it finds some it starts
	 * sending pages
	 */
	protected void scanForPages() throws XMPPException, IOException {
		
		if(_watchDir.list().length != 0){
			//there's a file to deal with
			File[] pages = _watchDir.listFiles();
			
			for(int i=0; i<pages.length;i++){
				File aPage = pages[i];
				logger.debug("dealing with page "+aPage);
				
				sendPage(aPage);
				
				//the page has been sent -- delete it
				if(!aPage.delete()){
					logger.error("unable to delete page "+aPage+" stopping scanner");
					conn.sendMessage("I had an error clearing a page -- shutting down scanner for "+_watchDir);
					stopThisMonitor = true;
				}
			}
		}
	}

	/** 
	 * read the page file and send the message in it
	 */
	protected void sendPage(File aPage) throws XMPPException, IOException {
		BufferedReader br = new BufferedReader(new FileReader(aPage));
		
		String message = new String();
		String line;
		
		while((line = br.readLine())!=null){
			message += line +"\n";
		}
		
		br.close();
		
		conn.sendMessage(message);
	}
	
	/** Creates a new instance of PageScanner 
	 * @param muc -- connection to the chatroom (for making announcements)
	 * @param watchDir -- directory to watch for new files
	 * @param sleeptime -- how long to sleep between scans of the directory
	 */
	public PageScanner(MultiUserChat muc, File watchDir, int sleeptime) {
		conn = muc;
		_watchDir = watchDir;
		this.sleeptime = sleeptime;
		
		t = new Thread(this);
		t.setName(watchDir.getName()+" pagescanner");
		//let's get the ball rolling on directory monitoring
		t.start();
	}
	
}
