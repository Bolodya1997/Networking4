package ru.nsu.fit.bolodya.chat;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Disconnector {

    private DatagramSocket socket;

    private Map<UUID, Message> messages = new HashMap<>();
    private Map<InetSocketAddress, Connection> neighbours = new HashMap<>();

    private Connector connector;
    private Accepter accepter;
    private Messenger messenger;

    private PacketReceived packetReceived;
    private PacketSpecial packetSpecial;
    private PrintMessage printMessage;

    public Disconnector(DatagramSocket socket,
                        Map<UUID, Message> messages, Map<InetSocketAddress, Connection> neighbours,
                        Connector connector, Accepter accepter, Messenger messenger,
                        PacketReceived packetReceived, PacketSpecial packetSpecial, PrintMessage printMessage) {
        this.socket = socket;
        this.messages = messages;
        this.neighbours = neighbours;
        this.connector = connector;
        this.accepter = accepter;
        this.messenger = messenger;
        this.packetReceived = packetReceived;
        this.packetSpecial = packetSpecial;
        this.printMessage = printMessage;
    }

    /*
     *  1.  Send all messages
     *  2.  Remove parent from neighbours
     *  3.  Notify all children and wait for their accepts
     *  4.  Notify parent and wait for him accept
     */
    void routine() {
        firstLoop();

        connector.removeParent();

    }

    private void firstLoop() {

    }

    private void disconnectLoop() {
        DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET], 0, 0);
        while (true) {
            messages.values().forEach(Message::send);
            messenger.updateLastMessages(connector);

            /*
             *  failed on receive
             *  skipped
             *  bad receiveMessage
             */
            if (!packetReceived.run(receivePacket))
                continue;

            byte[] data = receivePacket.getData();
            UUID id = getID(data);
            InetSocketAddress address = (InetSocketAddress) receivePacket.getSocketAddress();

            /*
             *  DISCONNECT + ACCEPT
             *  CONNECT
             *  not neighbour
             */
            if (packetSpecial.run(data, id, address))
                continue;

            if (isAccept(data)) {
                if (getType(data) == CONNECT)
                    connector.receiveAcceptConnect(accepter, id, address);
                else
                    accepter.receiveAccept(id, neighbours.get(address));

                continue;
            }

            if (getType(data) == DISCONNECT)
                connector.receiveDisconnect(data, address);
        }
    }
}
