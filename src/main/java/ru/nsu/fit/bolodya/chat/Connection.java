package ru.nsu.fit.bolodya.chat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.UUID;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Connection {

    private DatagramSocket socket;
    private SocketAddress address;

    Connection(DatagramSocket socket, SocketAddress address) {
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

    void accept(UUID id) {  //  TODO:   fix type of accept
        try {
            socket.send(new DatagramPacket(acceptMessage(MESSAGE, id), ACCEPT_LENGTH, address));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
