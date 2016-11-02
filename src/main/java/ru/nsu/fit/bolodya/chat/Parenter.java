package ru.nsu.fit.bolodya.chat;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Parenter {

    private Map<InetSocketAddress, Connection> neighbours;

    private Messenger messenger;
    private Responser responser;

    private InetSocketAddress parentAddress;

    private Connection oldParent;
    private UUID oldParentID;
    private boolean reconnecting = false;

    private Runnable shutdown;
    private boolean disconnecting = false;

    Parenter(Map<InetSocketAddress, Connection> neighbours,
             Messenger messenger, Responser responser,
             InetSocketAddress parentAddress, Runnable shutdown) {
        this.neighbours = neighbours;
        this.messenger = messenger;
        this.responser = responser;
        this.parentAddress = parentAddress;
        this.shutdown = shutdown;
    }

    boolean isParent(InetSocketAddress address) {
        if (parentAddress == null)
            return false;

        if (parentAddress.equals(address)) {
            if (!neighbours.containsKey(address)) {
                parentAddress = null;
                return false;
            }

            return true;
        }

        return false;
    }

    boolean isRoot() {
        if (!neighbours.containsKey(parentAddress))
            parentAddress = null;

        return parentAddress == null;
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
            messenger.sendSystemMessage(id, new Message(capture(id), parent));
        }

        responser.handleResponse(getID(data), parent);
    }

    InetSocketAddress getParentAddress() {
        return parentAddress;
    }
}
