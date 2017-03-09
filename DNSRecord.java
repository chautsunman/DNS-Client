import java.util.HashMap;

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
    public static final int TYPE_A = 1;
    public static final int TYPE_NS = 2;
    public static final int TYPE_CNAME = 5;
    public static final int TYPE_AAAA = 28;

    // CLASS
    public static final int CLASS_IP = 1;

    private String name;
    private int type;
    private int cl;
    private int ttl;
    private int rdlength;
    private String rdata = "";
    private String cnameIP = "";
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

    public String getTYPEString() {
        switch (type) {
            case TYPE_A:
                return "A";
            case TYPE_NS:
                return "NS";
            case TYPE_AAAA:
                return "AAAA";
            case TYPE_CNAME:
                return "CN";
        }

        return "";
    }

    public int getTTL() {
        return ttl;
    }

    public String getRDATA() {
        return rdata;
    }

    public String getCNAMEIP() {
        return cnameIP;
    }

    public int getRecordLength() {
        return recordLength;
    }

    public void setCNAMEIP(String cnameIP) {
        this.cnameIP = cnameIP;
    }
}
