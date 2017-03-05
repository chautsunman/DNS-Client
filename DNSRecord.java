public class DNSRecord {
    private int nameOffset;
    private String name;
    private int type;
    private int cl;
    private int ttl;
    private int rdlength;
    private String ip = "";


    public DNSRecord(byte[] recordData, String name) {
        // name
        this.name = name;

        // TYPE
        type = DNSResponse.parseByteToIntValue(recordData, 2, 2);

        // CLASS
        cl = DNSResponse.parseByteToIntValue(recordData, 4, 2);

        // TTL
        ttl = DNSResponse.parseByteToIntValue(recordData, 6, 4);

        // RDLENGTH
        rdlength = DNSResponse.parseByteToIntValue(recordData, 10, 2);

        // RDATA, IP
        if (type == 1 && cl == 1) {
            ip = parseIPv4(recordData, 12);
        }
        System.out.println(ip);
    }


    public String getName() {
        return name;
    }

    public int getTTL() {
        return ttl;
    }

    public String getIP() {
        return ip;
    }


    /**
     * Parse the bytes from i as an IPv4 address
     */
    private static String parseIPv4(byte[] bytes, int i) {
        String ip = Integer.toString(DNSResponse.parseByteToUnsignedInt(bytes[i]));

        for (int j = 1; j < 4; j++) {
            ip += "." + Integer.toString(DNSResponse.parseByteToUnsignedInt(bytes[i+j]));
        }

        return ip;
    }
}
