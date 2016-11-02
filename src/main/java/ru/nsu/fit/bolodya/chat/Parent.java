package ru.nsu.fit.bolodya.chat;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Parent {

    private Map<InetSocketAddress, Connection> neighbours;

    private Messenger messenger;
    private Connector connector;

    private InetSocketAddress parentAddress;

    private InetSocketAddress newParentAddress;
    private boolean reconnecting;
    private UUID savedID;

    private Runnable shutdown;

    Parent(Map<InetSocketAddress, Connection> neighbours,
           Messenger messenger, Connector connector,
           InetSocketAddress parentAddress, Runnable setCaptureFlag) {
        this.neighbours = neighbours;
        this.messenger = messenger;
        this.connector = connector;
        this.parentAddress = parentAddress;
        this.shutdown = setCaptureFlag;
    }

//  Check

    boolean isParent(InetSocketAddress address) {
        if (parentAddress == null)
            return false;

        if (!neighbours.containsKey(parentAddress))
            parentAddress = null;

        return address.equals(parentAddress);
    }

    boolean isNewParent(InetSocketAddress address) {
        if (reconnecting && !neighbours.containsKey(newParentAddress))
            reconnect(null);

        return reconnecting && address.equals(newParentAddress);
    }

    boolean isRoot() {
        return parentAddress == null || !neighbours.containsKey(parentAddress);
    }

//  Reconnecting

    void handleDisconnect(byte[] data) {
        newParentAddress = getParent(data);
        if (newParentAddress == null) {
            reconnect(null);
            return;
        }

        reconnecting = true;
        savedID = getID(data);
        neighbours.get(parentAddress).send(decline(DISCONNECT, savedID));

        UUID id = Message.nextID();
        connector.sendConnect(id, newParentAddress);
    }

    private void reconnect(InetSocketAddress address) {
        connector.acceptDisconnect(savedID, parentAddress);
        parentAddress = address;
        reconnecting = false;
    }

    void handleConnectAccept(UUID id) {
        if (reconnecting)
            reconnect(newParentAddress);

        Connection parent = neighbours.get(parentAddress);
        messenger.handleAccept(id, parent);
    }

//  Final disconnecting

    void sendCapture() {
        UUID id = Message.nextID();
        messenger.sendSystemMessage(id, new Message(capture(id), neighbours.get(parentAddress)));
    }

    void handleCaptureAccept() {
        shutdown.run();
    }

    InetSocketAddress getParentAddress() {
        return parentAddress;
    }
}
