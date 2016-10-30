package ru.nsu.fit.bolodya.chat;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Parenter {

    private Map<UUID, Message> messages;
    private Map<InetSocketAddress, Connection> neighbours;

    private Connector connector;
    private Responser responser;

    private InetSocketAddress parentAddress;

    private Connection oldParent;
    private UUID oldParentID;
    private boolean reconnecting = false;

    private Runnable shutdown;
    private boolean disconnecting = false;

    Parenter(Map<UUID, Message> messages, Map<InetSocketAddress, Connection> neighbours,
             Connector connector, Responser responser,
             InetSocketAddress parentAddress, Runnable shutdown) {
        this.messages = messages;
        this.neighbours = neighbours;
        this.connector = connector;
        this.responser = responser;
        this.parentAddress = parentAddress;
        this.shutdown = shutdown;
    }

    boolean isParent(InetSocketAddress address) {
        if (parentAddress != address)
            return false;

        if (!neighbours.containsKey(address)) {
            parentAddress = null;
            return false;
        }

        return true;
    }

    void handleDisconnect(byte[] data) {
        oldParent = neighbours.get(parentAddress);
        oldParentID = getID(data);
        reconnecting = true;

        neighbours.remove(parentAddress);

        parentAddress = getParent(data);
        if (parentAddress == null) {
            oldParent.send(response(DISCONNECT, oldParentID));
            reconnecting = false;
        }
    }

    void handleCaptureAccept() {
        neighbours.remove(parentAddress);
        shutdown.run();
    }

    void handleCaptureDecline() {
        disconnecting = true;
    }

    void handleConnectResponse(byte[] data) {
        if (reconnecting) {
            oldParent.send(response(DISCONNECT, oldParentID));
            reconnecting = false;
        }

        Connection parent = neighbours.get(parentAddress);
        if (disconnecting) {
            UUID id = Message.nextID();
            messages.put(id, new Message(capture(id), parent));
        }

        responser.handleResponse(getID(data), parent);
    }

    InetSocketAddress getParentAddress() {
        return parentAddress;
    }
}
