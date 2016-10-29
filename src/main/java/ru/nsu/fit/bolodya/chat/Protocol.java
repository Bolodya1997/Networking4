package ru.nsu.fit.bolodya.chat;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

class Protocol {
    static final int MAX_PACKET = 1024 * 1024;

    private static final byte TYPE_MASK     = 0b01111;
    private static final byte ACCEPT_MASK   = 0b10000;

    static final byte CONNECT       = 0b0001;
    static final byte MESSAGE       = 0b0010;
    static final byte DISCONNECT    = 0b0100;

    private static final byte ERR_TYPE = 0;
    private static final UUID ERR_ID = null;

    static final long MAX_MESSAGE_LIFE = 1000;  //  in milliseconds

    private static int TYPE_LENGTH = 1;
    private static int ID_LENGTH = 2 + Long.BYTES;  //  sizeof UUID
    private static int META_LENGTH = TYPE_LENGTH + ID_LENGTH;

    static final int ACCEPT_LENGTH = TYPE_LENGTH + ID_LENGTH;

    /*
     *  if (data.length + META_LENGTH > MAX_PACKET) data will be cut
     */
    private static byte[] message(byte type, UUID id, byte[] data) {
        int dataLength = (data == null) ? 0 : data.length;
        if (META_LENGTH + dataLength > MAX_PACKET)
            dataLength = MAX_PACKET - META_LENGTH;

        ByteBuffer buffer = ByteBuffer.allocate(META_LENGTH + dataLength)
                .put(type)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits());

        if (dataLength > 0)
            buffer.put(data, 0, dataLength);

        return buffer.array();
    }

    static byte[] connectMessage(UUID id) {
        return message(CONNECT, id, null);
    }

    static byte[] acceptMessage(byte type, UUID id) {
        return message((byte) (type | ACCEPT_MASK), id, null);
    }

    static byte[] disconnectMessage(UUID id, InetSocketAddress socket) {
        byte[] address = socket.getAddress().getAddress();
        byte[] data = ByteBuffer.allocate(address.length + Integer.BYTES)
                .put(address)
                .putInt(socket.getPort())
                .array();

        return message(DISCONNECT, id, data);
    }

    static byte[] packMessage(UUID id, byte[] data) {
        return message(MESSAGE, id, data);
    }

    static boolean filter(byte[] data) {
        return !(getType(data) == ERR_TYPE || getID(data) == ERR_ID);
    }

    static boolean isAccept(byte[] data) {
        return (data[0] & ACCEPT_MASK) != 0;
    }

    static byte getType(byte[] data) {
        if (data.length < TYPE_LENGTH || (data[0] & TYPE_MASK) > DISCONNECT)
            return ERR_TYPE;

        return (byte) (data[0] & TYPE_MASK);
    }

    static UUID getID(byte[] data) {
        if (data.length < TYPE_LENGTH + ID_LENGTH)
            return ERR_ID;

        ByteBuffer buffer = ByteBuffer.wrap(data, TYPE_LENGTH, ID_LENGTH);
        long most = buffer.getLong();
        long least = buffer.getLong();

        return new UUID(most, least);
    }

    static String getMessage(byte[] data) {
        int length = data.length - META_LENGTH;

        byte[] tmp = new byte[length];
        ((ByteBuffer) ByteBuffer.wrap(data).position(META_LENGTH)).get(tmp);

        String result = null;
        try {
            result = new String(tmp, "UTF8");
        }
        catch (UnsupportedEncodingException ignored) {}

        return result;
    }

    static InetSocketAddress getParent(byte[] data) {
        if (getType(data) != DISCONNECT)
            return null;

        try {
            ByteBuffer buffer = (ByteBuffer) ByteBuffer.wrap(data).position(META_LENGTH);

            int addressLength = buffer.getInt();
            byte[] tmp = new byte[addressLength];
            buffer.get(tmp);
            InetAddress address = InetAddress.getByAddress(tmp);

            return new InetSocketAddress(address, buffer.getInt());
        } catch (Exception e) {
            return null;
        }
    }
}