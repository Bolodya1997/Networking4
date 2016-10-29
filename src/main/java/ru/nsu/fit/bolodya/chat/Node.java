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

    private Node(int myPort, String hostName, int port) throws SocketException, UnknownHostException {
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

        mainLoop();
    }

    private void mainLoop() {
        DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET], 0, 0);
        while (true) {
            messageRoutine();

            messages.values().forEach(Message::send);

            try {
                socket.receive(receivePacket);
            } catch (IOException e) {
                continue;
            }
            if (Math.random() < 0.99)
                continue;

            byte[] data = receivePacket.getData();
            if (getType(data) == ERR_TYPE || getID(data) == ERR_ID)
                continue;

            if (getType(data) != CONNECT && !neighbours.containsKey(receivePacket.getSocketAddress()))
                continue;

            switch (getType(data)) {
                case CONNECT:
                    connector.connect(getID(data), (InetSocketAddress) receivePacket.getSocketAddress());
                    break;
                case ACCEPT:
                    accepter.accept(getID(data),
                            neighbours.get(receivePacket.getSocketAddress()));
                    break;
                case MESSAGE:
                    messenger.message(data, neighbours.get(receivePacket.getSocketAddress()));
                    break;
                case DISCONNECT:
                    connector.disconnect(data, (InetSocketAddress) receivePacket.getSocketAddress());
                    break;
            }
        }
    }

    private void messageRoutine() {
        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNext()) {
            try {
                messenger.message(packMessage(nextID(), scanner.next().getBytes("UTF8")));
            }
            catch (UnsupportedEncodingException ignored) {}
        }
    }

    private UUID nextID() {
        return UUID.randomUUID();
    }

    private void printMessage(byte[] data) {
        System.out.printf("%s:  %s\n", getID(data), getMessage(data));
    }
}
