import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 *
 */

/**
 * @author Donald Acton
 * This example is adapted from Kurose & Ross
 * Feel free to modify and rearrange code as you see fit
 */
public class DNSlookup {
    static final int MIN_PERMITTED_ARGUMENT_COUNT = 2;
    static final int MAX_PERMITTED_ARGUMENT_COUNT = 3;

    static final int HEADER_LENGTH = 12;

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        String fqdn;
        DNSResponse response; // Just to force compilation
        int argCount = args.length;
        boolean tracingOn = false;
        boolean IPV6Query = false;
        InetAddress rootNameServer;

        if (argCount < MIN_PERMITTED_ARGUMENT_COUNT || argCount > MAX_PERMITTED_ARGUMENT_COUNT) {
            usage();
            return;
        }

        rootNameServer = InetAddress.getByName(args[0]);
        fqdn = args[1];

        if (argCount == 3) {  // option provided
            if (args[2].equals("-t"))
                tracingOn = true;
            else if (args[2].equals("-6"))
                IPV6Query = true;
            else if (args[2].equals("-t6")) {
                tracingOn = true;
                IPV6Query = true;
            } else { // option present but wasn't valid option
                usage();
                return;
            }
        }


        // Start adding code here to initiate the lookup
        // create a socket
        DatagramSocket socket = new DatagramSocket();


        /* sending a query */
        // message
        ByteArrayOutputStream messageOStream = new ByteArrayOutputStream();
        byte[] message;

        byte[] id = writeHeader(messageOStream);

        // QNAME
        int questionLength = 0;
        String[] labels = fqdn.split("\\.");
        for (String label : labels) {
            byte[] labelBytes = label.getBytes();
            int labelLength = labelBytes.length;

            messageOStream.write(labelLength);
            messageOStream.write(labelBytes, 0, labelLength);

            questionLength += 1 + labelLength;
        }
        // terminate the domain name with a 0 byte
        messageOStream.write(0);
        questionLength += 1;
        // QTYPE
        messageOStream.write(0);
        messageOStream.write(1);
        questionLength += 2;
        // QCLASS
        messageOStream.write(0);
        messageOStream.write(1);
        questionLength += 2;

        // get the message
        message = messageOStream.toByteArray();

        // create the packet
        DatagramPacket packet = new DatagramPacket(message, message.length, rootNameServer, 53);

        // send the packet
        socket.send(packet);


        /* getting a response */
        // response
        byte[] buf = new byte[512];
        DatagramPacket responsePacket = new DatagramPacket(buf, buf.length);
        byte[] responseData;

        // get the response
        socket.receive(responsePacket);
        responseData = responsePacket.getData();


        /* parse the response */
        // check the id
        if (responseData[0] != id[0] || responseData[1] != id[1]) {
            System.out.println("different id");
            socket.close();
            return;
        }

        // ANCOUNT, NSCOUNT, ARCOUNT
        int ancount = parseByteToUnsignedInt(responseData[6]) * 256 + parseByteToUnsignedInt(responseData[7]);
        int nscount = parseByteToUnsignedInt(responseData[8]) * 256 + parseByteToUnsignedInt(responseData[9]);
        int arcount = parseByteToUnsignedInt(responseData[10]) * 256 + parseByteToUnsignedInt(responseData[11]);

        // answers
        String[] answers;

        int answerStartIndex = HEADER_LENGTH + questionLength;
        for (int i = 0; i < ancount; i++) {
            // TODO: check if the first 2 bits of the answer is 11 for message compression

            // name
            // OFFSET
            int nameOffset = parseByteToIntValue(responseData, answerStartIndex, 2) - 49152;
            String name = fqdn;
            // TODO: parse compressed name

            // TYPE
            int type = parseByteToIntValue(responseData, answerStartIndex+2, 2);

            // CLASS
            int cl = parseByteToIntValue(responseData, answerStartIndex+4, 2);

            // TTL
            int ttl = parseByteToIntValue(responseData, answerStartIndex+6, 4);

            // RDLENGTH
            int rdlength = parseByteToIntValue(responseData, answerStartIndex+10, 2);

            // RDATA, IP
            String ip = "";
            if (type == 1 && cl == 1) {
                ip = parseIPv4(responseData, answerStartIndex+12);
            }

            // print the data
            printResponse(name, ttl, false, ip);
        }


        // close the socket
        socket.close();
    }


    /**
     * Write the header of the query
     */
    private static byte[] writeHeader(ByteArrayOutputStream messageOStream) {
        // identifier
        // generate a 16-bit identifier
        byte[] id = new byte[2];
        new Random().nextBytes(id);
        // write the identifier to the message
        messageOStream.write(id, 0, 2);

        // QR, OPCODE, AA, TC, RD, RA, Z, RCODE
        // QR, the message is a query (0)
        // OPCODE, the message is a standard query (0000)
        // AA
        // TC
        // RD
        messageOStream.write(0);
        // RA
        // Z
        // RCODE
        messageOStream.write(0);

        // QDCOUNT, ANCOUNT, NSCOUNT, ARCOUNT
        // QDCOUNT, there is 1 question
        messageOStream.write(0);
        messageOStream.write(1);
        // ANCOUNT
        messageOStream.write(0);
        messageOStream.write(0);
        // NSCOUNT
        messageOStream.write(0);
        messageOStream.write(0);
        // ARCOUNT
        messageOStream.write(0);
        messageOStream.write(0);

        return id;
    }


    /**
     * Print the response in the required format
     */
    private static void printResponse(String fqdn, int ttl, boolean v6, String ip) {
        if (v6) {
            System.out.println(fqdn + " " + Integer.toString(ttl) + "   AAAA " + ip);
        } else {
            System.out.println(fqdn + " " + Integer.toString(ttl) + "   A " + ip);
        }
    }


    /**
     * Parse the bytes from i as an IPv4 address
     */
    private static String parseIPv4(byte[] bytes, int i) {
        String ip = Integer.toString(parseByteToUnsignedInt(bytes[i]));

        for (int j = 1; j < 4; j++) {
            ip += "." + Integer.toString(parseByteToUnsignedInt(bytes[i+j]));
        }

        return ip;
    }


    /**
     * Parse l bytes from i as an integer
     */
    private static int parseByteToIntValue(byte[] bytes, int i, int l) {
        int value = 0;

        for (int j = 0; j < l; j++) {
            value += parseByteToUnsignedInt(bytes[i+j]) * Math.pow(256, l-j-1);
        }

        return value;
    }


    /**
     * Parse a byte to an unsigned integer
     */
    private static int parseByteToUnsignedInt(byte b) {
        return b & 0xFF;
    }


    private static void usage() {
        System.out.println("Usage: java -jar DNSlookup.jar rootDNS name [-6|-t|t6]");
        System.out.println("   where");
        System.out.println("       rootDNS - the IP address (in dotted form) of the root");
        System.out.println("                 DNS server you are to start your search at");
        System.out.println("       name    - fully qualified domain name to lookup");
        System.out.println("       -6      - return an IPV6 address");
        System.out.println("       -t      - trace the queries made and responses received");
        System.out.println("       -t6     - trace the queries made, responses received and return an IPV6 address");
    }
}
