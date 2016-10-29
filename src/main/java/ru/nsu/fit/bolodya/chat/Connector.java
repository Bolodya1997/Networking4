package ru.nsu.fit.bolodya.chat;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Connector {

    private DatagramSocket socket;
    private Collection<Message> messages;
    private Map<InetSocketAddress, Connection> neighbours;
    private Set<Connection> captureSet;

    private Connection[] parent;
    private boolean parentDisconnecting = false;
    private UUID oldParentDisconnectId;
    private Connection oldParent;

    private Runnable shutdown;

    Connector(DatagramSocket socket, Collection<Message> messages, Map<InetSocketAddress,
              Connection> neighbours, Set<Connection> captureSet, Connection[] parent,
              Runnable shutdown) {
        this.socket = socket;
        this.messages = messages;
        this.neighbours = neighbours;
        this.captureSet = captureSet;
        this.parent = parent;
        this.shutdown = shutdown;
    }

    void receiveAcceptConnect(Accepter accepter, UUID id, InetSocketAddress address) {
        if (!parent[0].getAddress().equals(address))
            return;

        if (parentDisconnecting) {
            oldParent.send(accept(DISCONNECT, oldParentDisconnectId));
            parentDisconnecting = false;
        }

        accepter.receiveAccept(id, neighbours.get(address));
    }

    void receiveConnect(UUID id, InetSocketAddress address) {
        if (!neighbours.containsKey(address)) {
            Connection connection = new Connection(socket, address);

            neighbours.put(address, connection);
            for (Message message : messages)
                message.addConnection(connection);
        }

        neighbours.get(address).send(accept(CONNECT, id));
    }

    void receiveDisconnect(byte[] data, InetSocketAddress address) {
        Connection connection = neighbours.get(address);
        if (connection == null)
            connection = new Connection(socket, address);

        if (connection == parent[0]) {
            InetSocketAddress parentAddress = Protocol.getParent(data);

            oldParent = parent[0];
            oldParentDisconnectId = getID(data);
            parentDisconnecting = true;

            //  TODO:   add shutdown with answering to the old parent

            parent[0] = new Connection(socket, parentAddress);
            neighbours.put(parentAddress, parent[0]);
        }
        else {
            captureSet.remove(connection);

            connection.send(accept(DISCONNECT, getID(data)));
            neighbours.remove(address);
        }
    }

    void receiveAcceptDisconnect(Accepter accepter, UUID id, Connection connection) {
        accepter.receiveAccept(id, connection);
        neighbours.remove(connection.getAddress());
    }

    void lostConnection(Connection connection) {
        InetSocketAddress address = connection.getAddress();

        neighbours.remove(address);
        if (connection == parent[0]) {
            parent[0] = null;
            //  TODO:   add shutdown
        }

        for (Message message : messages)
            message.removeConnection(connection);
    }
}
