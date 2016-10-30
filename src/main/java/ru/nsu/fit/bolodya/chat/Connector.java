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

    void acceptCapture(UUID id, Connection connection) {
        captureSet.add(connection);
        connection.send(accept(CAPTURE, id));
    }

    void declineCapture(UUID id, Connection connection) {
        connection.send(decline(CAPTURE, id));
    }

    void handleConnect(UUID id, InetSocketAddress address) {
        if (!neighbours.containsKey(address)) {
            Connection connection = new Connection(socket, address);

            neighbours.put(address, connection);
            messenger.addConnection(connection);
        }

        neighbours.get(address).send(response(CONNECT, id));
    }

    void handleDisconnect(byte[] data, InetSocketAddress address) {
        Connection connection = neighbours.get(address);
        if (connection == null)
            connection = new Connection(socket, address);

        captureSet.remove(connection);

        connection.send(response(DISCONNECT, getID(data)));
        neighbours.remove(address);
    }

    void handleDisconnectResponse(Responser responser, UUID id, Connection connection) {
        responser.handleResponse(id, connection);
        neighbours.remove(connection.getAddress());
    }

    void lostConnection(Connection connection) {
        InetSocketAddress address = connection.getAddress();

        neighbours.remove(address);

        messenger.removeConnection(connection);
    }

    void sendDisconnect(InetSocketAddress parentAddress) {
        if (neighbours.isEmpty())
            return;

        Connection parent;
        if (parentAddress == null)
            parent = neighbours.values().iterator().next();     //  set first child as a parent for the others
        else
            parent = new Connection(socket, parentAddress);

        UUID id = Message.nextID();
        byte[] data = disconnect(id, parent.getAddress());
        messenger.addSystemMessage(id, new Message(data, neighbours.values()).removeConnection(parent));
    }
}
