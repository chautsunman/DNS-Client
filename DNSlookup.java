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

    static InetAddress rootServer;

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
        rootServer = InetAddress.getByName(args[0]);
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


        /* resolve the domain name */
        ArrayList<DNSRecord> answers = resolve(socket, fqdn, rootNameServer, IPV6Query, tracingOn);


        /* print the response */
        if (answers != null) {
            for (DNSRecord answer : answers) {
                if (answer.getName().equals(fqdn)) {
                    printResponse(answer.getName(), answer.getTTL(), IPV6Query, answer.getRDATA());
                }
            }
        }


        // close the socket
        socket.close();
    }


    /**
     * Resolve the domain name
     */
    private static ArrayList<DNSRecord> resolve(DatagramSocket socket, String fqdn, InetAddress server, boolean v6, boolean trace) throws Exception {
        // generate a 16-bit identifier
        byte[] id = new byte[2];
        new Random().nextBytes(id);
        // response
        byte[] buf = new byte[512];
        DatagramPacket responsePacket = new DatagramPacket(buf, buf.length);
        byte[] responseData = null;

        for (int i = 0; i < 2; i++) {
            /* sending a query */
            try {
                sendQuery(socket, id, fqdn, server, v6);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            // TODO: save the query

            // print the query trace
            if (trace) {
                printQueryTrace(id, fqdn, server);
            }


            /* getting a response */
            // get the response
            responseData = getResponse(socket, responsePacket, id);
            if (responseData != null) {
                break;
            } else if (i == 1) {
                printErrorResponse(fqdn, TIMEOUT_EXCEPTION_ERROR_TTL, ERROR_IP);
                return null;
            }
        }


        /* parse the response */
        DNSResponse response = new DNSResponse(responseData, responseData.length);
        int responseID = response.getID();
        int responseRCODE = response.getRCODE();
        boolean responseAA = response.getAA();
        int responseANCOUNT = response.getANCOUNT();
        int responseNSCOUNT = response.getNSCOUNT();
        int responseARCOUNT = response.getARCOUNT();
        ArrayList<DNSRecord> answers = response.getAnswers();
        ArrayList<DNSRecord> servers = response.getServers();
        ArrayList<DNSRecord> additionals = response.getAdditionals();

        // print the response trace
        if (trace) {
            printResponseTrace(responseID, responseAA, answers, servers, additionals);
        }

        // check for errors
        switch (responseRCODE) {
            case DNSResponse.RCODE_NAME_ERROR:
                printErrorResponse(fqdn, NAME_ERROR_TTL, ERROR_IP);
                return null;
            case DNSResponse.RCODE_REFUSED_ERROR:
            case DNSResponse.RCODE_SERVER_ERROR:
                printErrorResponse(fqdn, OTHER_ERROR_TTL, ERROR_IP);
                return null;
            case DNSResponse.RCODE_NO_ERROR:
                if (responseAA && responseANCOUNT == 0) {
                    printErrorResponse(fqdn, PSEUDO_ERROR_TTL, ERROR_IP);
                    return null;
                }
        }


        if (responseANCOUNT == 0 && responseNSCOUNT != 0) {
            // put the additional records into the cache
            for (DNSRecord additional : additionals) {
                if (additional.getTYPE() == DNSRecord.TYPE_A) {
                    additionalsCache.put(additional.getName(), additional);
                }
            }

            // get the next server to query
            String nextServerName = servers.get(0).getRDATA();
            String nextServerIP;
            if (additionalsCache.containsKey(nextServerName)) {
                nextServerIP = additionalsCache.get(nextServerName).getRDATA();
            } else {
                // resolve the next server's domain name
                ArrayList<DNSRecord> nextServerAnswers = resolve(socket, nextServerName, rootServer, false, trace);
                if (nextServerAnswers != null && !nextServerAnswers.isEmpty()) {
                    nextServerIP = nextServerAnswers.get(0).getRDATA();
                } else {
                    return null;
                }
            }
            InetAddress nextServer = InetAddress.getByName(nextServerIP);

            // resolve the domain name recursively
            return resolve(socket, fqdn, nextServer, v6, trace);
        }


        if (!answers.isEmpty()) {
            for (int i = 0; i < answers.size(); i++) {
                if (answers.get(i).getName().equals(fqdn)) {
                    if (answers.get(i).getTYPE() == DNSRecord.TYPE_CNAME) {
                        String cnameName = answers.get(i).getRDATA();
                        String cnameIP;
                        if (additionalsCache.containsKey(cnameName) && !v6) {
                            cnameIP = additionalsCache.get(cnameName).getRDATA();
                        } else {
                            // resolve the canonical name
                            ArrayList<DNSRecord> cnameAnswers = resolve(socket, cnameName, rootServer, v6, trace);
                            if (cnameAnswers != null && !cnameAnswers.isEmpty()) {
                                cnameIP = cnameAnswers.get(0).getRDATA();
                                // TODO: cache the canonical name's IP

                                // set the canonical name's IP
                                answers.get(i).setRDATA(cnameIP);
                            } else {
                                return null;
                            }
                        }
                    }
                }
            }
        }


        // return the answers
        return answers;
    }


    /**
     * Write the query
     */
    private static byte[] writeQuery(byte[] id, String fqdn, String mainFQDN, byte[] mainQuery, boolean v6) {
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
        if (v6) {
            queryOStream.write(28);
        } else {
            queryOStream.write(1);
        }
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
    private static byte[] sendQuery(DatagramSocket socket, byte[] id, String fqdn, InetAddress server, boolean v6) throws IOException {
        // write the query
        byte[] query = writeQuery(id, fqdn, fqdn, null, v6);

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


    /**
     * Print the query trace
     */
    private static void printQueryTrace(byte[] id, String fqdn, InetAddress server) {
        System.out.println("");
        System.out.println("");

        System.out.format("Query ID     %d %s --> %s\n", DNSResponse.parseByteToIntValue(id, 0, 2), fqdn, server.getHostAddress());
    }


    /**
     * Print the response trace
     */
    private static void printResponseTrace(int id, boolean aa, ArrayList<DNSRecord> answers, ArrayList<DNSRecord> servers, ArrayList<DNSRecord> additionals) {
        System.out.format("Response ID: %d Authoritative = %b\n", id, aa);

        if (!answers.isEmpty()) {
            System.out.format("  Answers (%d)\n", answers.size());
            for (DNSRecord answer : answers) {
                String name = answer.getName();
                int ttl = answer.getTTL();
                String type = answer.getTYPEString();
                String rdata = answer.getRDATA();
                System.out.format("       %-30s %-10d %-4s %s\n", name, ttl, type, rdata);
            }
        }

        if (!servers.isEmpty()) {
            System.out.format("  Nameservers (%d)\n", servers.size());
            for (DNSRecord server : servers) {
                String name = server.getName();
                int ttl = server.getTTL();
                String type = server.getTYPEString();
                String rdata = server.getRDATA();
                System.out.format("       %-30s %-10d %-4s %s\n", name, ttl, type, rdata);
            }
        }

        if (!additionals.isEmpty()) {
                System.out.format("  Additional Information (%d)\n", additionals.size());
            for (DNSRecord additional : additionals) {
                String name = additional.getName();
                int ttl = additional.getTTL();
                String type = additional.getTYPEString();
                String rdata = additional.getRDATA();
                System.out.format("       %-30s %-10d %-4s %s\n", name, ttl, type, rdata);
            }
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
