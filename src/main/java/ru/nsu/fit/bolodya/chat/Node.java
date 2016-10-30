package ru.nsu.fit.bolodya.chat;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

public class Node {

    private DatagramSocket socket;

    private Map<UUID, Message> messages = new HashMap<>();
    private Map<InetSocketAddress, Connection> neighbours = new HashMap<>();
    private Set<Connection> captureSet = new HashSet<>();

    private Connector connector;
    private Responser responser;
    private Messenger messenger;
    private Parenter parenter;

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

        InetSocketAddress parentAddress;

        if (hostName != null) {
            parentAddress = new InetSocketAddress(InetAddress.getByName(hostName), port);

            Connection parent = new Connection(socket, parentAddress);
            neighbours.put(parentAddress, parent);

            UUID id = nextID();
            messages.put(id, new Message(connect(id), parent).send());
        } else {
            parentAddress = null;
        }

        connector = new Connector(socket, messages.values(), neighbours, captureSet);
        responser = new Responser(messages);
        messenger = new Messenger(messages, neighbours.values(), this::printMessage);
        parenter = new Parenter(messages, neighbours, connector, responser, messenger, parentAddress);

        //  TODO:   shutdown hook

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
             *  bad handleMessage
             */
            if (!packetReceived(receivePacket))
                continue;

            byte[] data = receivePacket.getData();
            UUID id = getID(data);
            InetSocketAddress address = (InetSocketAddress) receivePacket.getSocketAddress();

            if (parenter.isParent(address)) {
                parenter.handlePacket(data);
            }

            /*
             *  CONNECT
             *  DISCONNECT
             *  not neighbour
             */
            if (packetSpecial(data, id, address))
                continue;

            if (isResponse(data)) {
                switch (getType(data)) {
                    case CONNECT:

                        break;
                    case MESSAGE:
                        responser.handleResponse(id, neighbours.get(address));
                        break;
                    case DISCONNECT:
                        connector.handleDisconnectResponse(responser, id, neighbours.get(address));
                }

                continue;
            }

            switch (getType(data)) {
                case CAPTURE:

                    break;
                case MESSAGE:
                    messenger.handleMessage(data, neighbours.get(address));
                    break;
                case DISCONNECT:
                    connector.handleDisconnect(data, address);
            }
        }
    }

    private void messageRoutine() {
        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNext()) {
            try {
                messenger.sendMessage(message(nextID(), scanner.next().getBytes("UTF8")));
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
        return Math.random() > 0.01 && !filter(receivePacket.getData());
    }

    private boolean packetSpecial(byte[] data, UUID id, InetSocketAddress address) {
        if (getType(data) == CONNECT && !isResponse(data)) {
            connector.handleConnect(id, address);
            return true;
        }

        if (getType(data) == DISCONNECT && !isResponse(data)) {
            connector.handleDisconnect(data, address);
            return true;
        }

        return !neighbours.containsKey(address);
    }

    private UUID nextID() {
        return UUID.randomUUID();
    }

    private void printMessage(byte[] data) {
        System.out.printf("%s:  %s\n", getID(data), getData(data));
    }

    //  Shutdown routines

}
