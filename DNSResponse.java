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


    // When in trace mode you probably want to dump out all the relevant information in a response
    void dumpResponse() {

    }


    // The constructor: you may want to add additional parameters, but the two shown are
    // probably the minimum that you need.
    public DNSResponse(byte[] responseData, int len) {
        // The following are probably some of the things
        // you will need to do.
        // Extract the query ID

        // Make sure the message is a query response and determine
        // if it is an authoritative response or note

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
        int answerStartIndex = questionEndIndex + 5;
        System.out.println(answerStartIndex);

        for (int i = 0; i < ancount; i++) {
            String name = getName(responseData, answerStartIndex);
            // TODO: cache the name
            answers.add(new DNSRecord(Arrays.copyOfRange(responseData, answerStartIndex, answerStartIndex+16), name));

            answerStartIndex += getRecordLength(responseData, answerStartIndex);
        }
    }


    // You will probably want a methods to extract a compressed FQDN, IP address
    // cname, authoritative DNS servers and other values like the query ID etc.
    /**
     * Get the FQDN starting at i
     */
    private static String getName(byte[] responseData, int i) {
        ArrayList<String> labels = new ArrayList<String>();

        if (checkBit(responseData[i], 0, MESSAGE_COMPRESSION_2_MSB)) {
            int offset = parseByteToIntValue(responseData, i, 2) - 49152;
            labels.add(getName(responseData, offset));
        } else {
            int j = i;
            while (responseData[j] != 0) {
                int labelLength = parseByteToIntValue(responseData, j, 1);
                labels.add(new String(Arrays.copyOfRange(responseData, j+1, j+1+labelLength)));
                j += 1 + labelLength;
            }
        }
        // TODO: get name with multi-level compression

        return joinStringArrayList(labels, ".");
    }


    /**
     * Get the record length
     */
    private static int getRecordLength(byte[] responseData, int i) {
        int length = 0;
        int j = i;
        while (true) {
            int labelLength = parseByteToIntValue(responseData, j, 1);

            if (labelLength == 0) {
                return length + 15;
            }

            if (checkBit(responseData[j], 0, MESSAGE_COMPRESSION_2_MSB)) {
                return length + 16;
            }

            length += 1 + labelLength;
            j += 1 + labelLength;
        }
    }


    // You will also want methods to extract the response records and record
    // the important values they are returning. Note that an IPV6 reponse record
    // is of type 28. It probably wouldn't hurt to have a response record class to hold
    // these records.


    public boolean getAA() {
        return authoritative;
    }


    public int getRCODE() {
        return rcode;
    }


    public int getANCOUNT() {
        return ancount;
    }


    public ArrayList<DNSRecord> getAnswers() {
        return answers;
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
