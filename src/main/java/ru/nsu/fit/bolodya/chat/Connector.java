package ru.nsu.fit.bolodya.chat;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Connector {

    private DatagramSocket socket;
    private Map<InetSocketAddress, Connection> neighbours;

    private Set<Connection> captureSet;

    private Messenger messenger;

    Connector(DatagramSocket socket, Map<InetSocketAddress, Connection> neighbours,
              Set<Connection> captureSet, Messenger messenger) {
        this.socket = socket;
        this.neighbours = neighbours;
        this.captureSet = captureSet;
        this.messenger = messenger;
    }

//  CAPTURE

    void acceptCapture(UUID id, Connection connection) {
        captureSet.add(connection);
        connection.send(accept(CAPTURE, id));
    }

    void declineCapture(UUID id, Connection connection) {
        connection.send(decline(CAPTURE, id));
    }

//  CONNECT

    void sendConnect(UUID id, InetSocketAddress address) {
        Connection connection = new Connection(socket, address);
        neighbours.put(address, connection);

        messenger.sendSystemMessage(id, new Message(connect(id), connection));
    }

    void acceptConnect(UUID id, InetSocketAddress address) {
        if (!neighbours.containsKey(address)) {
            Connection connection = new Connection(socket, address);

            neighbours.put(address, connection);
            messenger.addConnection(connection);
        }

        neighbours.get(address).send(accept(CONNECT, id));
    }

    void declineConnect(UUID id, InetSocketAddress address) {
        Connection connection = new Connection(socket, address);
        connection.send(decline(CONNECT, id));
    }

//  DISCONNECT

    void acceptDisconnect(UUID id, InetSocketAddress address) {
        Connection connection = neighbours.get(address);
        if (connection == null)
            connection = new Connection(socket, address);

        captureSet.remove(connection);

        connection.send(accept(DISCONNECT, id));
        neighbours.remove(address);
    }

    void handleDisconnectAccept(UUID id, Connection connection) {
        messenger.handleAccept(id, connection);
    }

//  Final disconnecting

    private Connection parent;

    void sendDisconnectToChildren(InetSocketAddress parentAddress) {
        if (neighbours.isEmpty())
            return;

        Connection parent = neighbours.get(parentAddress);
        if (parent == null)
            parent = neighbours.values().iterator().next();

        this.parent = parent;

        UUID id = Message.nextID();
        byte[] data = disconnect(id, parent.getAddress());
        messenger.sendSystemMessage(id, new Message(data, neighbours.values()).removeConnection(parent));
    }

    void sendDisconnectToParent() {
        UUID id = Message.nextID();
        messenger.sendSystemMessage(id, new Message(disconnect(id, null), parent));
    }

//  Lost connection

    void lostConnection(Connection connection) {
        InetSocketAddress address = connection.getAddress();

        captureSet.remove(connection);
        neighbours.remove(address);

        messenger.removeConnection(connection);
    }
}
