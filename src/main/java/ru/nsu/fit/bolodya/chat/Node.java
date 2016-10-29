package ru.nsu.fit.bolodya.chat;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

public class Node {

    private DatagramSocket socket;

    private Map<UUID, Message> messages = new HashMap<>();
    private Map<InetSocketAddress, Connection> neighbours = new HashMap<>();

    private Connector connector;
    private Accepter accepter;
    private Messenger messenger;

    static {
        try {
            "test".getBytes("UTF8");
        }
        catch (UnsupportedEncodingException e) {
            System.err.println("UTF8 must be supported");
            System.exit(-1);
        }
    }

    public Node(int myPort) throws SocketException, UnknownHostException {
        this(myPort, null, 0);
    }

    public Node(int myPort, String hostName, int port) throws SocketException, UnknownHostException {
        socket = new DatagramSocket(myPort);
        socket.setSoTimeout(100);

        Connection[] parent = new Connection[1];

        if (hostName != null) {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(hostName), port);

            parent[0] = new Connection(socket, address);
            neighbours.put(address, parent[0]);

            UUID id = nextID();
            messages.put(id, new Message(connectMessage(id), parent[0]).send());
        } else {
            parent[0] = null;
        }

        connector = new Connector(socket, messages.values(), neighbours, parent);
        accepter = new Accepter(messages);
        messenger = new Messenger(messages, neighbours.values(), this::printMessage);

        Disconnector disconnector = new Disconnector(socket,
                messages, neighbours,
                connector, accepter, messenger,
                this::packetReceived, this::packetSpecial, this::printMessage);
        Runtime.getRuntime().addShutdownHook(new Thread(disconnector::routine));

        mainLoop();
    }

    private void mainLoop() {
        DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET], 0, 0);
        while (true) {
            messageRoutine();

            messages.values().forEach(Message::send);
            messenger.updateLastMessages(connector);

            /*
             *  failed on receive
             *  skipped
             *  bad receiveMessage
             */
            if (!packetReceived(receivePacket))
                continue;

            byte[] data = receivePacket.getData();
            UUID id = getID(data);
            InetSocketAddress address = (InetSocketAddress) receivePacket.getSocketAddress();

            /*
             *  DISCONNECT + ACCEPT
             *  CONNECT
             *  not neighbour
             */
            if (packetSpecial(data, id, address))
                continue;

            if (isAccept(data)) {
                if (getType(data) == CONNECT)
                    connector.receiveAcceptConnect(accepter, id, address);
                else
                    accepter.receiveAccept(id, neighbours.get(address));

                continue;
            }

            switch (getType(data)) {
                case MESSAGE:
                    messenger.receiveMessage(data, neighbours.get(address));
                    break;
                case DISCONNECT:
                    connector.receiveDisconnect(data, address);
            }
        }
    }

    private void messageRoutine() {
        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNext()) {
            try {
                messenger.sendMessage(packMessage(nextID(), scanner.next().getBytes("UTF8")));
            }
            catch (UnsupportedEncodingException ignored) {}
        }
    }

    private boolean packetReceived(DatagramPacket receivePacket) {
        try {
            socket.receive(receivePacket);
        }
        catch (IOException e) {
            return false;
        }
        return Math.random() >= 0.99 && !filter(receivePacket.getData());

    }

    private boolean packetSpecial(byte[] data, UUID id, InetSocketAddress address) {
        if (getType(data) == CONNECT && !isAccept(data)) {
            connector.receiveConnect(id, address);
            return true;
        }

        if (getType(data) == DISCONNECT && isAccept(data)) {
            connector.receiveAcceptDisconnect(accepter, id, address);
            return true;
        }

        return !neighbours.containsKey(address);

    }

    private UUID nextID() {
        return UUID.randomUUID();
    }

    private void printMessage(byte[] data) {
        System.out.printf("%s:  %s\n", getID(data), getMessage(data));
    }
}
