package ru.nsu.fit.bolodya.chat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.UUID;

import static ru.nsu.fit.bolodya.chat.Protocol.ACCEPT_LENGTH;
import static ru.nsu.fit.bolodya.chat.Protocol.acceptMessage;

class Connection {

    private DatagramSocket socket;
    private InetSocketAddress address;

    Connection(DatagramSocket socket, InetSocketAddress address) {
        this.socket = socket;
        this.address = address;
    }

    void send(byte[] data) {
        try {
            socket.send(new DatagramPacket(data, data.length, address));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    void sendAccept(byte type, UUID id) {
        try {
            socket.send(new DatagramPacket(acceptMessage(type, id), ACCEPT_LENGTH, address));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    InetSocketAddress getAddress() {
        return address;
    }
}
