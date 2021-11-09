package com.github.melodeiro.servermanager.servers;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import javax.websocket.*;

/**
 * Created by Daniel on 20.02.2017.
 * @author Melodeiro
 */

@ClientEndpoint
public class WebSocketClientEndpoint {

    Session userSession = null;
    private MessageHandler messageHandler;
    private OnConnectedHandler onConnectedHandler;
    private URI endpointURI;
    private boolean isConnecting = false;

    WebSocketClientEndpoint(URI endpointURI) {
        this.endpointURI = endpointURI;
    }

    public void connect() {
        Runnable connect = () -> {
            if (!this.isConnecting) {
                this.isConnecting = true;
                try {
                    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                    container.connectToServer(this, endpointURI);
                    if (this.onConnectedHandler != null)
                        this.onConnectedHandler.handleOnConnected();
                } catch (IOException | DeploymentException e) {
                    System.out.println(e.getClass().getName() + ": " + e.getMessage());
                }
                this.isConnecting = false;
            }
        };

        new Thread(connect).start();
    }

    public void disconnect() {
        try {
            if(this.userSession != null)
                this.userSession.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Callback hook for Connection open events.
     *
     * @param userSession the userSession which is opened.
     */
    @OnOpen
    public void onOpen(Session userSession) {
        System.out.println("Connected to endpoint: " + userSession.getBasicRemote());
        this.userSession = userSession;
    }

    /**
     * Callback hook for Connection close events.
     *
     * @param userSession the userSession which is getting closed.
     * @param reason the reason for connection close
     */
    @OnClose
    public void onClose(Session userSession, CloseReason reason) {
        System.out.println("closing websocket");
        this.userSession = null;
    }

    @OnMessage
    public void onMessage(String message) {
        if (this.messageHandler != null && message != null) {
            this.messageHandler.handleMessage(message);
        }
    }

    @OnError
    public void processError(Throwable t) {
        t.printStackTrace();
    }

    /**
     * register message handler
     *
     * @param msgHandler
     */
    public void addMessageHandler(MessageHandler msgHandler) {
        this.messageHandler = msgHandler;
    }

    public void addOnConnectedHandler(OnConnectedHandler onConnectedHandler) {
        this.onConnectedHandler = onConnectedHandler;
    }

    /**
     * Send a message.
     *
     * @param message
     */
    public boolean sendMessage(String identifier, String message) {
        if (this.userSession != null)
        {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("Identifier", identifier);
            jsonObject.put("Message", message);
            jsonObject.put("Name", "WebRcon");
            String packet = jsonObject.toString();

            this.userSession.getAsyncRemote().sendText(packet);
            return true;
        }
        else
            return false;
    }

    /**
     * Message handler.
     *
     * @author Jiji_Sasidharan
     */
    public static interface MessageHandler {

        public void handleMessage(String message);
    }

    public static interface OnConnectedHandler {

        public void handleOnConnected();
    }
}