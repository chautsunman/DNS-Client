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
        String[] labels = fqdn.split("\\.");
        for (String label : labels) {
            byte[] labelBytes = label.getBytes();
            int labelLength = labelBytes.length;

            messageOStream.write(labelLength);
            messageOStream.write(labelBytes, 0, labelLength);
        }
        // terminate the domain name with a 0 byte
        messageOStream.write(0);
        // QTYPE
        messageOStream.write(0);
        messageOStream.write(1);
        // QCLASS
        messageOStream.write(0);
        messageOStream.write(1);

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

        // check the id
        if (responseData[0] != id[0] || responseData[1] != id[1]) {
            System.out.println("different id");
            socket.close();
            return;
        }

        // ANCOUNT, NSCOUNT, ARCOUNT
        int ancount = new Byte(responseData[6]).intValue() * 256 + new Byte(responseData[7]).intValue();
        System.out.println(ancount);
        int nscount = new Byte(responseData[8]).intValue() * 256 + new Byte(responseData[9]).intValue();
        System.out.println(nscount);
        int arcount = new Byte(responseData[10]).intValue() * 256 + new Byte(responseData[11]).intValue();
        System.out.println(arcount);


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
    private static void printResponse(String fqdn, String ttl, boolean v6, String ip) {
        if (v6) {
            System.out.println(fqdn + " " + ttl + "   AAAA " + ip);
        } else {
            System.out.println(fqdn + " " + ttl + "   A " + ip);
        }
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
