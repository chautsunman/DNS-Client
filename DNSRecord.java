public class DNSRecord {
    // indexes
    public static final int TYPE_NAMELENGTH_OFFSET = 0;
    public static final int CLASS_NAMELENGTH_OFFSET = 2;
    public static final int TTL_NAMELENGTH_OFFSET = 4;
    public static final int RDLENGTH_NAMELENGTH_OFFSET = 8;
    public static final int RDATA_NAMELENGTH_OFFSET = 10;
    public static final int TYPE_LENGTH = 2;
    public static final int CLASS_LENGTH = 2;
    public static final int TTL_LENGTH = 4;
    public static final int RDLENGTH_LENGTH = 2;

    // TYPE
    static final int TYPE_A = 1;
    static final int TYPE_NS = 2;

    // CLASS
    static final int CLASS_IP = 1;

    private String name;
    private int type;
    private int cl;
    private int ttl;
    private int rdlength;
    private String rdata = "";
    private int recordLength;


    public DNSRecord(String name, int type, int cl, int ttl, int rdlength, String rdata, int recordLength) {
        this.name = name;
        this.type = type;
        this.cl = cl;
        this.ttl = ttl;
        this.rdlength = rdlength;
        this.rdata = rdata;
        this.recordLength = recordLength;
    }


    public String getName() {
        return name;
    }

    public int getTYPE() {
        return type;
    }

    public int getTTL() {
        return ttl;
    }

    public String getRDATA() {
        return rdata;
    }

    public int getRecordLength() {
        return recordLength;
    }
}
