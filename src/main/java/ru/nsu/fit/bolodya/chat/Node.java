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

    private Messenger messenger;
    private Connector connector;
    private Responser responser;
    private Parenter parenter;

    static {
        try {
            "UTF8 must be supported".getBytes("UTF8");
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

        messenger = new Messenger(messages, neighbours.values(), this::printMessage);

        InetSocketAddress parentAddress;

        if (hostName != null) {
            parentAddress = new InetSocketAddress(InetAddress.getByName(hostName), port);

            Connection parent = new Connection(socket, parentAddress);
            neighbours.put(parentAddress, parent);

            UUID id = Message.nextID();
            messenger.addSystemMessage(id, new Message(connect(id), parent));
        } else {
            parentAddress = null;
        }

        connector = new Connector(socket, neighbours, captureSet, messenger);
        responser = new Responser(messages);
        parenter = new Parenter(neighbours, messenger, responser, parentAddress, this::shutdown);

        if (parentAddress != null) {
            connectionLoop();
            if (neighbours.isEmpty())
                return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownInit));

        mainLoop();
    }

    private void connectionLoop() {
        DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET], 0, MAX_PACKET);
        while (!messages.isEmpty()) {
            messages.values().forEach(Message::send);
            messenger.updateLastMessages(connector);

            /*
             *  failed on receive
             *  skipped
             *  bad message
             */
            if (packetReceiveFailed(receivePacket))
                continue;

            byte[] data = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
            InetSocketAddress address = (InetSocketAddress) receivePacket.getSocketAddress();

            if (parenter.isParent(address) && getType(data) == CONNECT && getResponse(data) == RESPONSE)
                parenter.handleConnectResponse(data);
        }
    }

    private void mainLoop() {
        DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET], 0, MAX_PACKET);
        while (true) {
            try {
                messageRoutine();
            }
            catch (IOException ignored) {}

            messages.values().forEach(Message::send);
            messenger.updateLastMessages(connector);

            /*
             *  failed on receive
             *  skipped
             *  bad message
             */
            if (packetReceiveFailed(receivePacket))
                continue;

            byte[] data = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
            UUID id = getID(data);
            InetSocketAddress address = (InetSocketAddress) receivePacket.getSocketAddress();

            /*
             *  from parent +
             *      DISCONNECT
             *      CAPTURE + RESPONSE
             *      CONNECT + RESPONSE
             */
            if (packetParent(data, address))
                continue;

            /*
             *  CONNECT
             *  DISCONNECT
             *  not neighbour
             */
            if (packetSpecial(data, id, address))
                continue;

            Connection connection = neighbours.get(address);

            /*
             *  MESSAGE + RESPONSE
             *  DISCONNECT + RESPONSE
             */
            if (packetResponse(data, id, connection))
                continue;

            /*
             *  CAPTURE
             *  MESSAGE
             */
            packet(data, id, connection);
        }
    }

    private void messageRoutine() throws IOException {
        Scanner scanner = new Scanner(System.in);
        if (System.in.available() > 0) {
            try {
                messenger.sendMessage(message(Message.nextID(), scanner.next().getBytes("UTF8")));
            }
            catch (UnsupportedEncodingException ignored) {}
        }
    }

    private boolean packetReceiveFailed(DatagramPacket receivePacket) {
        try {
            socket.receive(receivePacket);
        }
        catch (IOException e) {
            return true;
        }
        return Math.random() > 0.99 || filter(receivePacket.getLength());
    }

    private boolean packetParent(byte[] data, InetSocketAddress address) {
        if (!parenter.isParent(address))
            return false;

        if (getType(data) == DISCONNECT && getResponse(data) == NO_RESPONSE) {
            parenter.handleDisconnect(data);
            return true;
        }

        if (getType(data) == CAPTURE) {
            if (getResponse(data) == ACCEPT) {
                parenter.handleCaptureAccept();
                return true;
            }
            if (getResponse(data) == DECLINE) {
                parenter.handleCaptureDecline();
                return true;
            }
        }

        if (getType(data) == CONNECT && getResponse(data) == RESPONSE) {
            parenter.handleConnectResponse(data);
            return true;
        }

        return false;
    }

    private boolean packetSpecial(byte[] data, UUID id, InetSocketAddress address) {
        if (getType(data) == CONNECT && getResponse(data) == NO_RESPONSE) {
            connector.handleConnect(id, address);
            return true;
        }

        if (getType(data) == DISCONNECT && getResponse(data) == NO_RESPONSE) {
            connector.handleDisconnect(data, address);
            return true;
        }

        return !neighbours.containsKey(address);
    }

    private boolean packetResponse(byte[] data, UUID id, Connection connection) {
        if (getResponse(data) == NO_RESPONSE)
            return false;

        switch (getType(data)) {
            case MESSAGE:
                responser.handleResponse(id, connection);
                break;
            case DISCONNECT:
                connector.handleDisconnectResponse(responser, id, connection);
        }

        return true;
    }

    private void packet(byte[] data, UUID id, Connection connection) {
        switch (getType(data)) {
            case CAPTURE:
                connector.acceptCapture(id, connection);
                break;
            case MESSAGE:
                messenger.handleMessage(data, connection);
        }
    }

    private void printMessage(byte[] data) {
        String message = "";
        try {
            message = new String(getData(data), "UTF8");
        }
        catch (UnsupportedEncodingException ignored) {}

        System.out.printf("%s:  %s\n", getID(data), message);
    }

    //  Shutdown routines

    private void shutdownInit() {
        System.err.println("shutdownInit()");

        if (parenter.getParentAddress() == null)
            shutdown();

        UUID id = Message.nextID();
        InetSocketAddress parentAddress = parenter.getParentAddress();
        messenger.addSystemMessage(id, new Message(capture(id), neighbours.get(parentAddress)));
        waitForCaptureLoop();
    }

    /*
     *  no more:
     *      messages from input
     *      accept on capture
     *
     *  now:
     *      decline on capture
     */
    private void waitForCaptureLoop() {
        DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET], 0, MAX_PACKET);
        while (true) {
            messages.values().forEach(Message::send);
            messenger.updateLastMessages(connector);

            /*
             *  failed on receive
             *  skipped
             *  bad message
             */
            if (packetReceiveFailed(receivePacket))
                continue;

            byte[] data = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
            UUID id = getID(data);
            InetSocketAddress address = (InetSocketAddress) receivePacket.getSocketAddress();

            /*
             *  from parent +
             *      DISCONNECT
             *      CAPTURE + RESPONSE
             *      CONNECT + RESPONSE
             */
            if (packetParent(data, address))
                continue;

            /*
             *  CONNECT
             *  DISCONNECT
             *  not neighbour
             */
            if (packetSpecial(data, id, address))
                continue;

            Connection connection = neighbours.get(address);

            /*
             *  MESSAGE + RESPONSE
             *  DISCONNECT + RESPONSE
             */
            if (packetResponse(data, id, connection))
                continue;

            /*
             *  CAPTURE
             *  MESSAGE
             */
            packet(data, id, connection);
        }
    }

    /*
     *  1.  Send all messages and wait for all captures fell
     *  2.  Send disconnect to all children (and wait for responses)
     *  3.  Send disconnect to the parent (and wait for response)
     */
    private void shutdown() {
        System.err.println("shutdown()");
        messagesAndCapturesLoop();  //  1

        connector.sendDisconnect(parenter.getParentAddress());
        waitAllChildrenLoop();  //  2

        if (parenter.getParentAddress() == null)
            Runtime.getRuntime().halt(0);

        UUID id = Message.nextID();
        Connection parent = new Connection(socket, parenter.getParentAddress());
        messenger.addSystemMessage(id, new Message(disconnect(id, null), parent));

        neighbours.put(parent.getAddress(), parent);    //  for the correct isParent() call
        waitParentLoop();   //  3

        Runtime.getRuntime().halt(0);
    }

    /*
     *  no more:
     *      packets from parent
     *      handle connect
     *      handle message
     */
    private void messagesAndCapturesLoop() {
        System.err.println("messagesAndCapturesLoop()");
        DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET], 0, MAX_PACKET);
        while (!messages.isEmpty() || !captureSet.isEmpty()) {
            messages.values().forEach(Message::send);
            messenger.updateLastMessages(connector);

            /*
             *  failed on receive
             *  skipped
             *  bad message
             */
            if (packetReceiveFailed(receivePacket))
                continue;

            byte[] data = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
            UUID id = getID(data);
            InetSocketAddress address = (InetSocketAddress) receivePacket.getSocketAddress();

            /*
             *  DISCONNECT
             */
            if (getType(data) == DISCONNECT && getResponse(data) == NO_RESPONSE) {
                connector.handleDisconnect(data, address);
                continue;
            }

            if (!neighbours.containsKey(address))
                continue;

            Connection connection = neighbours.get(address);

            /*
             *  MESSAGE + RESPONSE
             *  DISCONNECT + RESPONSE
             */
            if (packetResponse(data, id, connection))
                continue;

            /*
             *  CAPTURE
             */
            if (getType(data) == CAPTURE && getResponse(data) == NO_RESPONSE)
                connector.declineCapture(id, connection);
        }
    }

    /*
     *  no more:
     *      handle disconnect
     *      handle message response
     */
    private void waitAllChildrenLoop() {
        System.err.println("waitAllChildrenLoop()");
        DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET], 0, MAX_PACKET);
        while (!messages.isEmpty()) {
            messages.values().forEach(Message::send);
            messenger.updateLastMessages(connector);

            /*
             *  failed on receive
             *  skipped
             *  bad message
             */
            if (packetReceiveFailed(receivePacket))
                continue;

            byte[] data = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
            UUID id = getID(data);
            InetSocketAddress address = (InetSocketAddress) receivePacket.getSocketAddress();

            if (!neighbours.containsKey(address))
                continue;

            Connection connection = neighbours.get(address);

            /*
             *  DISCONNECT + RESPONSE
             */
            if (getType(data) == DISCONNECT && getResponse(data) == RESPONSE) {
                connector.handleDisconnectResponse(responser, id, connection);
                continue;
            }

            /*
             *  CAPTURE
             */
            if (getType(data) == CAPTURE && getResponse(data) == NO_RESPONSE)
                connector.declineCapture(id, connection);
        }
    }
    /*
     *  now:
     *      handle only disconnect response from parent
     */
    private void waitParentLoop() {
        System.err.println("waitParentLoop()");
        DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET], 0, MAX_PACKET);
        while (!messages.isEmpty()) {
            messages.values().forEach(Message::send);
            messenger.updateLastMessages(connector);

            /*
             *  failed on receive
             *  skipped
             *  bad message
             */
            if (packetReceiveFailed(receivePacket))
                continue;

            byte[] data = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
            InetSocketAddress address = (InetSocketAddress) receivePacket.getSocketAddress();

            if (parenter.isParent(address) && getType(data) == DISCONNECT && getResponse(data) == RESPONSE)
                return;
        }
    }
}
