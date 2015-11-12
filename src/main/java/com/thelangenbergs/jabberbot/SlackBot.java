package com.thelangenbergs.jabberbot;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by davel on 11/11/15.
 */
public class SlackBot {

    protected static Logger log = Logger.getLogger(SlackBot.class);

    private String endpoint;
    private String iconURL;
    private String channel;
    private String username;

    public SlackBot(String endpoint, String iconURL, String channel, String username) {
        this.endpoint = endpoint;
        this.iconURL = iconURL;
        this.channel = channel;
        this.username = username;

        if(!channel.startsWith("#")){
            this.channel = new String("#" + channel);
        }
    }

    public void sendMessage(String message) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(endpoint);

        JSONObject sendMe = new JSONObject();
        sendMe.put("text", message);
        sendMe.put("channel", channel);
        sendMe.put("username", username);

        if (iconURL.startsWith(":")) {
            sendMe.put("icon_emoji", iconURL);
        } else {
            sendMe.put("icon_url", iconURL);
        }


        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("payload", sendMe.toString()));

        log.debug("Sending message " + message + " to " + channel);
        httpPost.setEntity(new StringEntity(sendMe.toString()));

        CloseableHttpResponse response = httpClient.execute(httpPost);
        log.debug("response is " + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());

        response.close();
        httpClient.close();

    }
}
