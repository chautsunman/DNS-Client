import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Random;
import java.io.IOException;

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

    static final int NAME_ERROR_TTL = -1;
    static final int TIMEOUT_EXCEPTION_ERROR_TTL = -2;
    static final int OTHER_ERROR_TTL = -4;
    static final int PSEUDO_ERROR_TTL = -6;
    static final String ERROR_IP = "0.0.0.0";

    static HashMap<String, DNSRecord> additionalsCache = new HashMap();

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        String fqdn;
        String mainFQDN;
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
        mainFQDN = fqdn;

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
        socket.setSoTimeout(5000);


        // resolve the domain name
        resolve(socket, fqdn, rootNameServer);


        // close the socket
        socket.close();
    }


    /**
     * Resolve the domain name
     */
    private static void resolve(DatagramSocket socket, String fqdn, InetAddress server) throws Exception {
        System.out.println(fqdn + " " + server.getHostAddress());

        /* sending a query */
        byte[] id = null;
        try {
            id = sendQuery(socket, fqdn, server);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        // TODO: save the query


        /* getting a response */
        // response
        byte[] buf = new byte[512];
        DatagramPacket responsePacket = new DatagramPacket(buf, buf.length);
        byte[] responseData;

        // get the response
        responseData = getResponse(socket, responsePacket, id);
        if (responseData == null) {
            printErrorResponse(fqdn, TIMEOUT_EXCEPTION_ERROR_TTL, ERROR_IP);
            return;
        }


        /* parse the response */
        DNSResponse response = new DNSResponse(responseData, responseData.length);
        // check for errors
        int responseRCODE = response.getRCODE();
        boolean responseAA = response.getAA();
        int responseANCOUNT = response.getANCOUNT();
        int responseNSCOUNT = response.getNSCOUNT();
        int responseARCOUNT = response.getARCOUNT();
        switch (responseRCODE) {
            case DNSResponse.RCODE_NAME_ERROR:
                printErrorResponse(fqdn, NAME_ERROR_TTL, ERROR_IP);
                return;
            case DNSResponse.RCODE_REFUSED_ERROR:
            case DNSResponse.RCODE_SERVER_ERROR:
                printErrorResponse(fqdn, OTHER_ERROR_TTL, ERROR_IP);
                return;
            case DNSResponse.RCODE_NO_ERROR:
                if (responseAA && responseANCOUNT == 0) {
                    printErrorResponse(fqdn, PSEUDO_ERROR_TTL, ERROR_IP);
                    return;
                }
        }


        if (responseANCOUNT == 0) {
            ArrayList<DNSRecord> servers = response.getServers();
            ArrayList<DNSRecord> additionals = response.getAdditionals();

            // put the additional records into the cache
            for (DNSRecord additional : additionals) {
                if (additional.getTYPE() == DNSRecord.TYPE_A) {
                    additionalsCache.put(additional.getName(), additional);
                }
            }

            // get the next server to query
            String nextServerName = servers.get(0).getRDATA();
            String nextServerIP = additionalsCache.get(nextServerName).getRDATA();
            System.out.println(nextServerName + " " + nextServerIP);
            InetAddress nextServer = InetAddress.getByName(nextServerIP);

            resolve(socket, fqdn, nextServer);
        }


        /* print the response */
        ArrayList<DNSRecord> answers = response.getAnswers();
        for (DNSRecord answer : answers) {
            printResponse(answer.getName(), answer.getTTL(), false, answer.getRDATA());
        }
    }


    /**
     * Write the query
     */
    private static byte[] writeQuery(byte[] id, String fqdn, String mainFQDN, byte[] mainQuery) {
        // return the main query if
        if (fqdn == mainFQDN && mainQuery != null) {
            return mainQuery;
        }

        ByteArrayOutputStream queryOStream = new ByteArrayOutputStream();

        writeHeader(queryOStream, id);

        // QNAME
        int questionLength = 0;
        String[] labels = fqdn.split("\\.");
        for (String label : labels) {
            byte[] labelBytes = label.getBytes();
            int labelLength = labelBytes.length;

            queryOStream.write(labelLength);
            queryOStream.write(labelBytes, 0, labelLength);

            questionLength += 1 + labelLength;
        }
        // terminate the domain name with a 0 byte
        queryOStream.write(0);
        questionLength += 1;
        // QTYPE
        queryOStream.write(0);
        queryOStream.write(1);
        questionLength += 2;
        // QCLASS
        queryOStream.write(0);
        queryOStream.write(1);
        questionLength += 2;

        // return the query
        return queryOStream.toByteArray();
    }


    /**
     * Write the header of the query
     */
    private static void writeHeader(ByteArrayOutputStream queryOStream, byte[] id) {
        // write the identifier to the message
        queryOStream.write(id, 0, 2);

        // QR, OPCODE, AA, TC, RD, RA, Z, RCODE
        // QR, the message is a query (0)
        // OPCODE, the message is a standard query (0000)
        // AA
        // TC
        // RD
        queryOStream.write(0);
        // RA
        // Z
        // RCODE
        queryOStream.write(0);

        // QDCOUNT, ANCOUNT, NSCOUNT, ARCOUNT
        // QDCOUNT, there is 1 question
        queryOStream.write(0);
        queryOStream.write(1);
        // ANCOUNT
        queryOStream.write(0);
        queryOStream.write(0);
        // NSCOUNT
        queryOStream.write(0);
        queryOStream.write(0);
        // ARCOUNT
        queryOStream.write(0);
        queryOStream.write(0);
    }


    /**
     * Send the query
     */
    private static byte[] sendQuery(DatagramSocket socket, String fqdn, InetAddress server) throws IOException {
        // generate a 16-bit identifier
        byte[] id = new byte[2];
        new Random().nextBytes(id);
        // write the query
        byte[] query = writeQuery(id, fqdn, fqdn, null);

        // create the packet
        DatagramPacket packet = new DatagramPacket(query, query.length, server, 53);

        // send the packet
        socket.send(packet);

        // return the id
        return id;
    }


    /**
     * Get the response
     */
    private static byte[] getResponse(DatagramSocket socket, DatagramPacket responsePacket, byte[] id) {
        byte[] responseData;

        while (true) {
            try {
                socket.receive(responsePacket);
            } catch (SocketTimeoutException e) {
                // TODO: resend the packet for a second time
                return null;
            } catch (IOException e) {
                return null;
            }

            responseData = responsePacket.getData();

            // check the id
            if (responseData[0] == id[0] && responseData[1] == id[1]) {
                break;
            }
        }

        return responseData;
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
     * Print error response
     */
    private static void printErrorResponse(String fqdn, int ttl, String ip) {
        System.out.println(fqdn + " " + Integer.toString(ttl) + "   A " + ip);
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
