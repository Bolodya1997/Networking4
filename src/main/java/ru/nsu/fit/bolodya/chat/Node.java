package ru.nsu.fit.bolodya.chat;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

public class Node {

    private DatagramSocket socket;

    private Map<InetSocketAddress, Connection> neighbours = new HashMap<>();

    private Set<Connection> captureSet = new HashSet<>();

    private Messenger messenger;
    private Connector connector;
    private Parent parent;

    private boolean shutdownFlag;
    private boolean captureFlag;

    private String name;
    {
        ArrayList<String> names = new ArrayList<>();
        names.add("Peter");
        names.add("Svinusha");
        names.add("Donald");
        names.add("Serega");
        names.add("Bolodya");
        names.add("Losos");
        names.add("Fedor");
        names.add("Katyusha");
        names.add("Grigory");
        names.add("Daniel");
        names.add("Len04ka");
        names.add("Alexander");
        names.add("Nikolay");
        names.add("Ilyusha");
        names.add("Nikos");
        names.add("Jason Statehem");

        name = names.get((int) (Math.random() * names.size())) + ((int) (Math.random() * 1000));
    }

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
        socket.setSoTimeout(TIMEOUT);

        messenger = new Messenger(neighbours.values(), this::printMessage);
        connector = new Connector(socket, neighbours, captureSet, messenger);
        messenger.setConnector(connector);

        InetSocketAddress parentAddress;

        if (hostName != null) {
            parentAddress = new InetSocketAddress(InetAddress.getByName(hostName), port);

            UUID id = Message.nextID();
            connector.sendConnect(id, parentAddress);
        } else {
            parentAddress = null;
        }

        parent = new Parent(neighbours, messenger, connector, parentAddress, () -> captureFlag = true);

        if (parentAddress != null) {
            connectionLoop();
            if (neighbours.isEmpty()) {
                System.err.printf("Connection failed: %s\n", parentAddress);
                return;
            }

            System.err.printf("Connection successed: %s\n", parentAddress);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownInit));
        Runtime.getRuntime().addShutdownHook(new Thread(this::hardClose));

        mainLoop();
    }

    private void printMessage(byte[] data) {
        String message = "";
        try {
            message = new String(getData(data), "UTF8");
        }
        catch (UnsupportedEncodingException ignored) {}

        System.out.printf(message);
    }

    private void hardClose() {
        try {
            Thread.sleep(MAX_MESSAGE_LIFE);
        }
        catch (InterruptedException ignored) {}

        System.err.println("*** HARD CLOSE ***");
        Runtime.getRuntime().halt(-1);
    }

    /*
     *  1.  User input                  - optional
     *  2.  Send messages               - constant
     *  3.  Try to receive new message  - constant
     *  4.  Parent routines             - variable
     *  5.  Connector routines          - variable
     *  6.  Messenger routines          - variable
     */
    private void loop(Statement statement, boolean userInputAllowed, ParentRoutines parentRoutines,
                      ConnectorRoutines connectorRoutines, MessengerRoutines messengerRoutines) {
        DatagramPacket packet = new DatagramPacket(new byte[MAX_PACKET], 0, MAX_PACKET);
        while (statement.run()) {
            if (userInputAllowed) {
                try {
                    userInputRoutine();
                }
                catch (IOException ignored) {
                }
            }                                                   //  1

            parent.updateCapture();     //  TODO:   move it into more appropriate place
            messenger.send();                                   //  2

            if (packetReceiveFailed(packet))
                continue;                                       //  3

            InetSocketAddress address = (InetSocketAddress) packet.getSocketAddress();
            byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
            if (parentRoutines != null && parentRoutines.run(address, data))
                continue;                                       //  4

            UUID id = getID(data);
            if (connectorRoutines != null && connectorRoutines.run(address, data, id))
                continue;                                       //  5

            Connection connection = neighbours.get(address);
            if (messengerRoutines != null)
                messengerRoutines.run(connection, data, id);    //  6
        }
    }

    private void userInputRoutine() throws IOException {
        Scanner scanner = new Scanner(System.in);
        if (System.in.available() > 0) {
            try {
                String message = String.format("%s : %s\n", name, scanner.next());
                messenger.sendMessage(message(Message.nextID(), message.getBytes("UTF8")));
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

    private interface Statement {
        boolean run();
    }

    private interface ParentRoutines {
        boolean run(InetSocketAddress address, byte[] data);
    }

    private interface ConnectorRoutines {
        boolean run(InetSocketAddress address, byte[] data, UUID id);
    }

    private interface MessengerRoutines {
        void run(Connection connection, byte[] data, UUID id);
    }

    private void connectionLoop() {
        Statement statement = () -> !messenger.isEmpty();
        boolean userInputAllowed = false;
        ParentRoutines parentRoutines = (address, data) -> {
            if (parent.isParent(address)) {
                if (getType(data) == CONNECT && getResponse(data) == ACCEPT) {
                    parent.handleConnectAccept(getID(data));
                    return true;
                }
            }

            return false;
        };
        ConnectorRoutines connectorRoutines = null;
        MessengerRoutines messengerRoutines = null;

        loop(statement, userInputAllowed, parentRoutines, connectorRoutines, messengerRoutines);
    }

    private ParentRoutines mainParentRoutines;
    private MessengerRoutines mainMessengerRoutines;

    private void mainLoop() {
        Statement statement = () -> !shutdownFlag;
        boolean userInputAllowed = true;
        ParentRoutines parentRoutines = (address, data) -> {
            if (parent.isParent(address)) {
                if (getType(data) == DISCONNECT && getResponse(data) == NO_RESPONSE) {
                    parent.handleDisconnect(data);
                    return true;
                }

                if (getType(data) == CAPTURE && getResponse(data) == ACCEPT) {
                    parent.handleCaptureAccept();
                    return true;
                }
            }

            if (parent.isNewParent(address)) {
                if (getType(data) == CONNECT && getResponse(data) == ACCEPT) {
                    parent.handleConnectAccept(getID(data));
                    return true;
                }
            }

            return false;
        };
        ConnectorRoutines connectorRoutines = (address, data, id) -> {
            if (acceptConnectDisconnect(address, data, id))
                return true;

            if (getType(data) == CAPTURE && getResponse(data) == NO_RESPONSE) {
                connector.acceptCapture(id, neighbours.get(address));
                return true;
            }

            return false;
        };
        MessengerRoutines messengerRoutines = (connection, data, id) -> {
            if (getType(data) == MESSAGE && getResponse(data) == NO_RESPONSE) {
                messenger.acceptMessage(data, connection);
                return;
            }

            handleAcceptDecline(connection, data, id);
        };

        mainParentRoutines = parentRoutines;
        mainMessengerRoutines = messengerRoutines;

        loop(statement, userInputAllowed, parentRoutines, connectorRoutines, messengerRoutines);
    }

    private boolean acceptConnectDisconnect(InetSocketAddress address, byte[] data, UUID id) {
        if (getType(data) == CONNECT && getResponse(data) == NO_RESPONSE) {
            connector.acceptConnect(id, address);
            return true;
        }

        if (getType(data) == DISCONNECT && getResponse(data) == NO_RESPONSE) {
            connector.acceptDisconnect(id, address);
            return true;
        }

        return !neighbours.containsKey(address);
    }

    private void handleAcceptDecline(Connection connection, byte[] data, UUID id) {
        if (getResponse(data) == ACCEPT) {
            messenger.handleAccept(id, connection);
            return;
        }

        if (getResponse(data) == DECLINE)
            messenger.handleDecline(id);
    }

//  Shutdown routines

    private void shutdownInit() {
        shutdownFlag = true;

        if (parent.isRoot())
            shutdown();

        parent.sendCapture();
        waitForCaptureLoop();
        shutdown();
    }

    private void waitForCaptureLoop() {
        Statement statement = () -> !parent.isRoot() && !(captureFlag && captureSet.isEmpty());
        boolean userInputAllowed = false;
        ParentRoutines parentRoutines = mainParentRoutines;
        ConnectorRoutines connectorRoutines = (address, data, id) -> {
            if (acceptConnectDisconnect(address, data, id))
                return true;

            if (getType(data) == CAPTURE && getResponse(data) == NO_RESPONSE) {
                connector.declineCapture(id, neighbours.get(address));
                return true;
            }

            return false;
        };
        MessengerRoutines messengerRoutines = mainMessengerRoutines;

        loop(statement, userInputAllowed, parentRoutines, connectorRoutines, messengerRoutines);
    }

    /*
     *  1.  Wait for all messages to deliever
     *  2.  Send disconnect to all children (and wait for responses)
     *  3.  Send disconnect to the parent (and wait for response)
     */
    private void shutdown() {
        messagesLoop();  //  1

        connector.sendDisconnectToChildren(parent.getParentAddress());
        waitAllChildrenLoop();  //  2

        if (parent.isRoot()) {
            Runtime.getRuntime().halt(0);
        }

        connector.sendDisconnectToParent();
        waitParentLoop();   //  3

        Runtime.getRuntime().halt(0);
    }

    private Statement messageStatement;
    private ConnectorRoutines messageConnectorRoutines;
    private MessengerRoutines messageMessengerRoutines;

    private void messagesLoop() {
        Statement statement = () -> !messenger.isEmpty();
        boolean userInputAllowed = false;
        ParentRoutines parentRoutines = null;
        ConnectorRoutines connectorRoutines = (address, data, id) -> {
            if (getType(data) == DISCONNECT && getResponse(data) == NO_RESPONSE)
                connector.acceptDisconnect(id, address);

            if (getType(data) == CAPTURE && getResponse(data) == NO_RESPONSE) {
                connector.declineCapture(id, neighbours.get(address));
                return true;
            }

            return false;
        };
        MessengerRoutines messengerRoutines = (connection, data, id) -> {
            if (getType(data) == MESSAGE && getResponse(data) == NO_RESPONSE) {
                messenger.declineMessage(data, connection);
            }

            handleAcceptDecline(connection, data, id);
        };

        messageStatement = statement;
        messageConnectorRoutines = connectorRoutines;
        messageMessengerRoutines = messengerRoutines;

        loop(statement, userInputAllowed, parentRoutines, connectorRoutines, messengerRoutines);
    }

    private void waitAllChildrenLoop() {
        Statement statement = messageStatement;
        boolean userInputAllowed = false;
        ParentRoutines parentRoutines = null;
        ConnectorRoutines connectorRoutines = (address, data, id) -> {
            if (messageConnectorRoutines.run(address, data, id))
                return true;

            if (getType(data) == DISCONNECT && getResponse(data) == ACCEPT) {
                connector.handleDisconnectAccept(id, neighbours.get(address));
            }

            return false;
        };
        MessengerRoutines messengerRoutines = messageMessengerRoutines;

        loop(statement, userInputAllowed, parentRoutines, connectorRoutines, messengerRoutines);
    }

    private void waitParentLoop() {
        waitAllChildrenLoop();
    }
}
