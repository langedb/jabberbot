/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.thelangenbergs.jabberbot;

import com.wolfram.alpha.WAEngine;
import com.wolfram.alpha.WAException;
import com.wolfram.alpha.WAPlainText;
import com.wolfram.alpha.WAPod;
import com.wolfram.alpha.WAQuery;
import com.wolfram.alpha.WAQueryResult;
import com.wolfram.alpha.WASubpod;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.jivesoftware.smackx.muc.MultiUserChat;

/**
 * This class handles interaction between wolfram alpha and the bot
 * @author davel
 */
public class AlphaHandler {
	protected static Logger log = Logger.getLogger(AlphaHandler.class);
	
	private String appId;
	private Properties configuration;
	
	WAEngine engine;
	
	public AlphaHandler(Properties config){
		configuration = config;
		
		appId = config.getProperty("alpha.appid");
		
		if(appId == null){
			throw new IllegalArgumentException("Unable to initializ, configuration missing alpha.appid key");
		}
		
		log.debug("appid is: "+appId);
		
		//setup alpha
		engine = new WAEngine();
		engine.setAppID(appId);
		engine.addFormat("plaintext");
	}
	
	public String queryAlpha(String query) throws WAException{
		log.debug("query is: '"+query+"'");
		
		StringBuilder response = new StringBuilder();
		WAQuery q = engine.createQuery(query);
		
		WAQueryResult result = engine.performQuery(q);
		
		if(result.isError()){
			log.warn(result.getErrorCode()+": "+result.getErrorMessage());
			return "received an error -- see log";
		}
		else {
			//succes, get the first pod & send it back
			
			for (WAPod pod : result.getPods()) {
				for (WASubpod subpod : pod.getSubpods()) {
					for (Object element : subpod.getContents()) {
						if (element instanceof WAPlainText) {
							response.append(((WAPlainText) element).getText());
							response.append("\n");
						}
					}
				}
			}
		}
		
		return response.toString();
		
	}
	
	
	
	
	
}
