package ru.nsu.fit.bolodya.chat;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

public class Node {

    private DatagramSocket socket;

    private Map<Long, Message> messages = new HashMap<>();
    private Map<SocketAddress, Connection> neighbours = new HashMap<>();

    private Connector connector;
    private Accepter accepter;
    private Messenger messenger;
    private Disconnector disconnector;

    private long id;

    static {
        try {
            "test".getBytes("UTF8");
        }
        catch (UnsupportedEncodingException e) {
            System.err.println("UTF8 must be supported");
            System.exit(-1);
        }
    }

    public Node(String hostName, int port) throws SocketException, UnknownHostException {
        socket = new DatagramSocket(port);
        socket.setSoTimeout(100);

        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(hostName), port);

        ByteBuffer tmp = (ByteBuffer) ByteBuffer.allocate(Long.BYTES)
                .put(address.getAddress().getAddress())
                .putInt(port)
                .position(0);
        id = tmp.getLong();

        Connection[] parent = new Connection[1];
        parent[0] = new Connection(socket, address);
        neighbours.put(address, parent[0]);

        messages.put(id, new Message(connectMessage(id), parent[0]).send());

        connector = new Connector(socket, messages.values(), neighbours);
        accepter = new Accepter(messages);
        messenger = new Messenger(messages, neighbours.values(), this::printMessage);
        disconnector = new Disconnector(neighbours, parent);

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

            switch (getType(data)) {
                case CONNECT:
                    connector.connect(receivePacket.getSocketAddress());
                    break;
                case ACCEPT:
                    accepter.accept(getID(data), neighbours.get(receivePacket.getSocketAddress()));
                    break;
                case MESSAGE:
                    messenger.message(data, neighbours.get(receivePacket.getSocketAddress()));
                    break;
                case DISCONNECT:
                    disconnector.disconnect(data, neighbours.get(receivePacket.getSocketAddress()));
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

    private long nextID() {
        return ++id;
    }

    private void printMessage(byte[] data) {

    }
}
