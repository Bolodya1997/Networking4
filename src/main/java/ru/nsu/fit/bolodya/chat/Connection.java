package ru.nsu.fit.bolodya.chat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

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

    InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != Connection.class)
            return false;

        Connection connection = (Connection) obj;

        return address.equals(connection.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }
}
