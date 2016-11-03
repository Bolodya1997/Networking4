package ru.nsu.fit.bolodya;

import org.apache.commons.cli.*;
import ru.nsu.fit.bolodya.chat.Node;

import java.net.SocketException;
import java.net.UnknownHostException;

public class Main {

    private static Options options = new Options();

    static {
        options.addOption("p", "port", true, "node port");
        options.addOption("pp", "parent_port", true, "port to connect");
        options.addOption("h", "host", true, "host to connect");
    }

    public static void main(String[] args) {
        Parser parser = new GnuParser();
        CommandLine commandLine;
        try {
            commandLine = parser.parse(options, args);
        }
        catch (ParseException e) {
            onError();
            return;
        }

        if (!commandLine.hasOption("p")) {
            onError();
            return;
        }
        int port = Integer.decode(commandLine.getOptionValue("p"));

        try {
            if (!commandLine.hasOption("pp") || !commandLine.hasOption("h")) {
                System.out.printf("Root on: %d\n", port);
                new Node(port);
            } else {
                String host = commandLine.getOptionValue("h");
                Integer parentPort = Integer.decode(commandLine.getOptionValue("pp"));

                System.out.printf("Node on: %d\n", port);
                new Node(port, host, parentPort);
            }
        }
        catch (UnknownHostException | SocketException e) {
            e.printStackTrace();
        }
    }

    private static void onError() {
        System.err.println("" + options);
    }
}
