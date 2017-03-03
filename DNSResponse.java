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
    static final int HEADER_LENGTH = 12;

    private int queryID;                  // this is for the response it must match the one in the request
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
    public DNSResponse(byte[] responseData, int len, int questionLength) {
        // The following are probably some of the things
        // you will need to do.
        // Extract the query ID

        // Make sure the message is a query response and determine
        // if it is an authoritative response or note

        // determine answer count
        ancount = parseByteToUnsignedInt(responseData[6]) * 256 + parseByteToUnsignedInt(responseData[7]);
        System.out.println(ancount);

        // determine NS Count
        nscount = parseByteToUnsignedInt(responseData[8]) * 256 + parseByteToUnsignedInt(responseData[9]);
        System.out.println(nscount);

        // determine additional record count
        arcount = parseByteToUnsignedInt(responseData[10]) * 256 + parseByteToUnsignedInt(responseData[11]);
        System.out.println(arcount);

        // Extract list of answers, name server, and additional information response
        // records
        // TODO: read question section and determine questionLength
        int answerStartIndex = HEADER_LENGTH + questionLength;
        for (int i = 0; i < ancount; i++) {
            answers.add(new DNSRecord(Arrays.copyOfRange(responseData, answerStartIndex, answerStartIndex+16)));

            answerStartIndex += 16;
        }
    }


    // You will probably want a methods to extract a compressed FQDN, IP address
    // cname, authoritative DNS servers and other values like the query ID etc.


    // You will also want methods to extract the response records and record
    // the important values they are returning. Note that an IPV6 reponse record
    // is of type 28. It probably wouldn't hurt to have a response record class to hold
    // these records.


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
}
