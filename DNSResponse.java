import java.util.Arrays;
import java.util.ArrayList;
import java.net.InetAddress;

// Lots of the action associated with handling a DNS query is processing
// the response. Although not required you might find the following skeleton of
// a DNSreponse helpful. The class below has bunch of instance data that typically needs to be
// parsed from the response. If you decide to use this class keep in mind that it is just a
// suggestion and feel free to add or delete methods to better suit your implementation as
// well as instance variables.

public class DNSResponse {
    // indexes and lengths
    static final int HEADER_LENGTH = 12;
    static final int ANCOUNT_START_INDEX = 6;
    static final int NSCOUNT_START_INDEX = 8;
    static final int ARCOUNT_START_INDEX = 10;
    static final int QUERY_RESOURCE_COUNT_LENGTH = 2;
    static final int QUESTION_START_INDEX = HEADER_LENGTH;

    // AA
    static final byte[] AA_BIT = {1};

    // RCODE
    public static final int RCODE_NO_ERROR = 0;
    public static final int RCODE_FORMAT_ERROR = 1;
    public static final int RCODE_SERVER_ERROR = 2;
    public static final int RCODE_NAME_ERROR = 3;
    public static final int RCODE_NOT_IMPLEMENTED_ERROR = 4;
    public static final int RCODE_REFUSED_ERROR = 5;

    // message compression
    static final byte[] MESSAGE_COMPRESSION_2_MSB = {1, 1};

    private int queryID;                  // this is for the response it must match the one in the request
    private int rcode;
    private int ancount = 0;          // number of answers
    private boolean decoded = false;      // Was this response successfully decoded
    private int nscount = 0;              // number of nscount response records
    private int arcount = 0;      // number of additional (alternate) response records
    private boolean authoritative = false;// Is this an authoritative record

    // Note you will almost certainly need some additional instance variables.
    // answers
    ArrayList<DNSRecord> answers = new ArrayList();
    ArrayList<DNSRecord> servers = new ArrayList();
    ArrayList<DNSRecord> additionals = new ArrayList();


    // When in trace mode you probably want to dump out all the relevant information in a response
    void dumpResponse() {

    }


    // The constructor: you may want to add additional parameters, but the two shown are
    // probably the minimum that you need.
    public DNSResponse(byte[] responseData, int len) {
        // The following are probably some of the things
        // you will need to do.
        // Extract the query ID
        queryID = parseByteToIntValue(responseData, 0, 2);

        // Make sure the message is a query response and determine
        // if it is an authoritative response or note
        authoritative = (checkBit(responseData[2], 5, AA_BIT)) ? true : false;

        // check the response code (RCODE)
        rcode = parseBitsToIntValue(responseData[3], 4, 4);

        // determine answer count
        ancount = parseByteToIntValue(responseData, ANCOUNT_START_INDEX, QUERY_RESOURCE_COUNT_LENGTH);

        // determine NS Count
        nscount = parseByteToIntValue(responseData, NSCOUNT_START_INDEX, QUERY_RESOURCE_COUNT_LENGTH);

        // determine additional record count
        arcount = parseByteToIntValue(responseData, ARCOUNT_START_INDEX, QUERY_RESOURCE_COUNT_LENGTH);

        // Extract list of answers, name server, and additional information response
        // records
        // determine the start index of the answer section
        int questionEndIndex = QUESTION_START_INDEX;
        while (true) {
            int labelLength = parseByteToIntValue(responseData, questionEndIndex, 1);

            if (labelLength == 0) {
                break;
            }

            questionEndIndex += 1 + labelLength;
        }
        // TODO: determine the question end index if the question's name is compressed

        // get the answers
        int answerStartIndex = questionEndIndex + 5;
        for (int i = 0; i < ancount; i++) {
            DNSRecord record = getRecord(responseData, answerStartIndex);
            // TODO: cache the name

            answers.add(record);

            answerStartIndex += record.getRecordLength();
        }

        // get the name servers
        int serverStartIndex = answerStartIndex;
        for (int i = 0; i < nscount; i++) {
            DNSRecord record = getRecord(responseData, serverStartIndex);

            servers.add(record);

            serverStartIndex += record.getRecordLength();
        }

        // get the additional records
        int additionalStartIndex = serverStartIndex;
        for (int i = 0; i < arcount; i++) {
            DNSRecord record = getRecord(responseData, additionalStartIndex);

            additionals.add(record);

            additionalStartIndex += record.getRecordLength();
        }
    }


    // You will probably want a methods to extract a compressed FQDN, IP address
    // cname, authoritative DNS servers and other values like the query ID etc.
    /**
     * Get the FQDN starting at i
     */
    public static String parseName(byte[] responseData, int i) {
        ArrayList<String> labels = new ArrayList<String>();

        int j = i;
        while (responseData[j] != 0) {
            if (checkBit(responseData[j], 0, MESSAGE_COMPRESSION_2_MSB)) {
                int offset = parseByteToIntValue(responseData, j, 2) - 49152;
                labels.add(parseName(responseData, offset));
                break;
            } else {
                int labelLength = parseByteToIntValue(responseData, j, 1);
                labels.add(new String(responseData, j+1, labelLength));
                j += 1 + labelLength;
            }
        }
        // TODO: get name with multi-level compression

        return joinStringArrayList(labels, ".");
    }


    /**
     * Get NAME length
     */
    private static int getNAMELength(byte[] responseData, int i) {
        int length = 0;
        int j = i;
        while (true) {
            int labelLength = parseByteToIntValue(responseData, j, 1);

            if (labelLength == 0) {
                return length + 1;
            }

            if (checkBit(responseData[j], 0, MESSAGE_COMPRESSION_2_MSB)) {
                return length + 2;
            }

            length += 1 + labelLength;
            j += 1 + labelLength;
        }
    }


    /**
     * Parse the bytes from i as an IPv4 address
     */
    private static String parseIPv4(byte[] bytes, int i) {
        ArrayList<String> fields = new ArrayList<String>();

        for (int j = 0; j < 4; j++) {
            fields.add(Integer.toString(parseByteToIntValue(bytes, i+j, 1)));
        }

        return joinStringArrayList(fields, ".");
    }


    /**
     * Parse the bytes from i as an IPv6 address
     */
    private static String parseIPv6(byte[] bytes, int i) {
        ArrayList<String> fields = new ArrayList<String>();

        for (int j = 0; j < 8; j++) {
            fields.add(Integer.toString(parseByteToIntValue(bytes, i+(j*2), 2), 16));
        }

        return joinStringArrayList(fields, ":");
    }


    /**
     * Parse RDATA
     */
    private static String parseRDATA(byte[] responseData, int i, int type, int cl) {
        if (type == DNSRecord.TYPE_A && cl == DNSRecord.CLASS_IP) {
            return parseIPv4(responseData, i);
        } else if (type == DNSRecord.TYPE_NS && cl == DNSRecord.CLASS_IP) {
            return parseName(responseData, i);
        } else if (type == DNSRecord.TYPE_AAAA && cl == DNSRecord.CLASS_IP) {
            return parseIPv6(responseData, i);
        }

        return "";
    }


    /**
     * Get the whole resource record
     */
    private static DNSRecord getRecord(byte[] responseData, int recordStartIndex) {
        String name = parseName(responseData, recordStartIndex);
        int nameLength = getNAMELength(responseData, recordStartIndex);
        int type = parseByteToIntValue(responseData, recordStartIndex+nameLength+DNSRecord.TYPE_NAMELENGTH_OFFSET, DNSRecord.TYPE_LENGTH);
        int cl = parseByteToIntValue(responseData, recordStartIndex+nameLength+DNSRecord.CLASS_NAMELENGTH_OFFSET, DNSRecord.CLASS_LENGTH);
        int ttl = parseByteToIntValue(responseData, recordStartIndex+nameLength+DNSRecord.TTL_NAMELENGTH_OFFSET, DNSRecord.TTL_LENGTH);
        int rdlength = parseByteToIntValue(responseData, recordStartIndex+nameLength+DNSRecord.RDLENGTH_NAMELENGTH_OFFSET, DNSRecord.RDLENGTH_LENGTH);
        String rdata = parseRDATA(responseData, recordStartIndex+nameLength+DNSRecord.RDATA_NAMELENGTH_OFFSET, type, cl);
        int recordLength = nameLength + 10 + rdlength;

        return new DNSRecord(name, type, cl, ttl, rdlength, rdata, recordLength);
    }


    // You will also want methods to extract the response records and record
    // the important values they are returning. Note that an IPV6 reponse record
    // is of type 28. It probably wouldn't hurt to have a response record class to hold
    // these records.


    public int getID() {
        return queryID;
    }

    public boolean getAA() {
        return authoritative;
    }

    public int getRCODE() {
        return rcode;
    }

    public int getANCOUNT() {
        return ancount;
    }

    public int getNSCOUNT() {
        return nscount;
    }

    public int getARCOUNT() {
        return arcount;
    }

    public ArrayList<DNSRecord> getAnswers() {
        return answers;
    }

    public ArrayList<DNSRecord> getServers() {
        return servers;
    }

    public ArrayList<DNSRecord> getAdditionals() {
        return additionals;
    }


    /**
     * Parse l bytes from i as an integer
     */
    public static int parseByteToIntValue(byte[] bytes, int i, int l) {
        int value = 0;

        for (int j = 0; j < l; j++) {
            value += parseByteToUnsignedInt(bytes[i+j]) * Math.pow(256, l-j-1);
        }

        return value;
    }


    /**
     * Parse a byte to an unsigned integer
     */
    public static int parseByteToUnsignedInt(byte b) {
        return b & 0xFF;
    }


    /**
     * Check bits match in a byte
     */
    private static boolean checkBit(byte b, int i, byte[] bits) {
        for (int j = 0; j < bits.length; j++) {
            if (getBit(b, i+j) != bits[j]) {
                return false;
            }
        }

        return true;
    }


    /**
     * Parse l bits from i as an integer
     */
    private static int parseBitsToIntValue(byte b, int i, int l) {
        int value = 0;

        for (int j = 0; j < l; j++) {
            value += getBit(b, (i+j)) * Math.pow(2, l-j-1);
        }

        return value;
    }


    /**
     * Get the (i+1)th most-significant bit in a byte
     */
    private static int getBit(byte b, int i) {
        return (b >> (7-i)) & 1;
    }


    /**
     * Join String arrays as 1 String
     */
    public static String joinStringArrayList(ArrayList<String> strings, String delimeter) {
        String s = strings.get(0);

        for (int i = 1; i < strings.size(); i++) {
            s += delimeter + strings.get(i);
        }

        return s;
    }
}
