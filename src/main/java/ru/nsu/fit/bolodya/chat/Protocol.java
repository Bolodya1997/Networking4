package ru.nsu.fit.bolodya.chat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

class Protocol {
    static final int MAX_PACKET = 1024 * 1024;

    static final byte CONNECT       = 0x01;
    static final byte ACCEPT        = 0x02;
    static final byte MESSAGE       = 0x03;
    static final byte DISCONNECT    = 0x04;
    static final byte ERR_TYPE      = 0x00;

    static final long ERR_ID = -1;

    static final long MAX_MESSAGE_LIFE = 5000;

    static byte[] connectMessage(long id) {
        return ByteBuffer.allocate(1 + Long.BYTES)
                .put(CONNECT)
                .putLong(id)
                .array();
    }

    static byte[] packMessage(long id, byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + Long.BYTES + data.length);
        buffer.put(MESSAGE);
        buffer.putLong(id);
        buffer.put(data);

        return buffer.array();
    }

    private static int TYPE_LENGTH = 1;
    private static int ID_LENGTH = Long.BYTES;
    private static int PARENT_LENGTH = 2 * Integer.BYTES;

    static byte getType(byte[] data) {
        if (data.length < TYPE_LENGTH || data[0] > DISCONNECT)
            return ERR_TYPE;
        return data[0];
    }

    static long getID(byte[] data) {
        if (data.length < TYPE_LENGTH + ID_LENGTH)
            return ERR_ID;
        return ByteBuffer.wrap(data, 1, Long.BYTES).getLong();
    }

    static SocketAddress getParent(byte[] data) throws UnknownHostException {
        if (getType(data) != DISCONNECT)
            return null;

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data, TYPE_LENGTH + ID_LENGTH, PARENT_LENGTH);

            byte[] tmp = new byte[Integer.BYTES];
            buffer.get(tmp);
            InetAddress address = InetAddress.getByAddress(tmp);

            return new InetSocketAddress(address, buffer.getInt());
        } catch (Exception e) {
            return null;
        }
    }
}