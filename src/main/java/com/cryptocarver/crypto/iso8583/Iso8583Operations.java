package com.cryptocarver.crypto.iso8583;

import com.cryptocarver.crypto.EmvTlv;
import com.cryptocarver.crypto.PaymentOperations;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ISO 8583 message codec.  This is deliberately a profile rather than a
 * scheme implementation: ISO 8583:1987/1993 define the message/field model,
 * while acquirers assign private fields and wire encodings in their profiles.
 * The public definitions below are the common data elements from ISO 8583
 * Tables 1 and 2 (1987, clauses 4-6; 1993, clauses 4-6); fields not verifiable
 * without a scheme profile are left undefined and produce a warning.
 */
public class Iso8583Operations {
    protected Iso8583Operations() { }

    public enum Version { ISO_1987, ISO_1993 }
    public enum BitmapEncoding { BINARY, ASCII_HEX }
    public enum LengthEncoding { ASCII, BCD }
    public enum DataType { N, AN, ANS, B, Z }

    /** Wire choices that are profile-specific and therefore explicit. */
    public record Profile(Version version, BitmapEncoding bitmapEncoding,
                          LengthEncoding lengthEncoding, boolean numericBcd) {
        public Profile { Objects.requireNonNull(version); Objects.requireNonNull(bitmapEncoding); Objects.requireNonNull(lengthEncoding); }
        public static Profile iso1987() { return new Profile(Version.ISO_1987, BitmapEncoding.BINARY, LengthEncoding.ASCII, false); }
        public static Profile iso1993() { return new Profile(Version.ISO_1993, BitmapEncoding.BINARY, LengthEncoding.ASCII, false); }
        public Profile withBitmap(BitmapEncoding v) { return new Profile(version, v, lengthEncoding, numericBcd); }
        public Profile withLength(LengthEncoding v) { return new Profile(version, bitmapEncoding, v, numericBcd); }
        public Profile withNumericBcd(boolean v) { return new Profile(version, bitmapEncoding, lengthEncoding, v); }
    }

    /** Field definition; length is digits/chars except for B, where it is bytes. */
    public record FieldDefinition(int number, String name, DataType type, int length,
                                  int maxLength, boolean variable, String source) {
        public FieldDefinition { if (number < 1 || number > 192 || length < 0 || maxLength < length) throw new IllegalArgumentException("invalid field definition"); }
        public boolean isFixed() { return !variable; }
        public String shape() { return variable ? (maxLength > 99 ? "LLLVAR" : "LLVAR") : "fixed"; }
    }

    public record Mti(String value, int version, int classCode, int function, int origin,
                      String versionName, String className, String functionName, String originName) {
        public static Mti parse(String value) {
            if (value == null || !value.matches("\\d{4}")) throw new IllegalArgumentException("MTI must be four digits");
            int v=value.charAt(0)-'0', c=value.charAt(1)-'0', f=value.charAt(2)-'0', o=value.charAt(3)-'0';
            return new Mti(value,v,c,f,o, versionName(v), className(c), functionName(f), originName(o));
        }
        public String prose() { return value+" — "+versionName+"; "+className+"; "+functionName+"; "+originName; }
        private static String versionName(int n) { return switch(n) { case 0 -> "ISO 8583:1987"; case 1 -> "ISO 8583:1993"; case 2 -> "ISO 8583:2003 (not profiled)"; default -> "reserved version "+n; }; }
        private static String className(int n) { return switch(n) { case 1 -> "authorization message"; case 2 -> "financial message"; case 3 -> "file action message"; case 4 -> "reversal/chargeback message"; case 5 -> "reconciliation message"; case 6 -> "administrative message"; case 7 -> "fee collection message"; case 8 -> "network management message"; default -> "reserved class "+n; }; }
        private static String functionName(int n) { return switch(n) { case 0 -> "request"; case 1 -> "request response"; case 2 -> "advice"; case 3 -> "advice response"; case 4 -> "notification"; case 8 -> "response acknowledgment"; case 9 -> "negative acknowledgment"; default -> "reserved function "+n; }; }
        private static String originName(int n) { return switch(n) { case 0 -> "acquirer"; case 1 -> "acquirer repeat"; case 2 -> "issuer"; case 3 -> "issuer repeat"; case 4 -> "other"; case 5 -> "other repeat"; case 6 -> "reserved"; case 7 -> "reserved"; default -> "reserved origin "+n; }; }
    }

    public record ParsedField(int number, FieldDefinition definition, String value,
                              int encodedLength, List<String> warnings, String enrichment) { }
    public record Message(Mti mti, Profile profile, byte[] bitmap, List<byte[]> bitmapParts,
                          Map<Integer, ParsedField> fields, List<String> warnings) {
        public Message { bitmap=bitmap.clone(); bitmapParts=List.copyOf(bitmapParts); fields=Map.copyOf(fields); warnings=List.copyOf(warnings); }
        public Optional<ParsedField> field(int number) { return Optional.ofNullable(fields.get(number)); }
        public Set<Integer> presentFields() { return fields.keySet(); }
        public String report() { return Iso8583Operations.report(this); }
    }

    private static final Map<Integer, FieldDefinition> COMMON = new LinkedHashMap<>();
    static {
        String n = "ISO 8583 data-element type/length table, public simulator concepts: https://iso8583sim.com/docs/getting-started/concepts/ (Field Types and Key Fields)";
        String a = "ISO 8583 parser worked authorization fixture: https://iso8583parser.com/en/articles/iso8583-message-examples (Example 1, lines 30-55)";
        String p = "ISO 8583 parser worked financial fixture: https://iso8583parser.com/en/articles/iso8583-message-examples (Example 2, lines 79-103)";
        String b = "ISO 8583 public data-element reference: https://en.wikipedia.org/wiki/ISO_8583 (data element table; used only for the field block)";
        add(2,"Primary account number",DataType.N,0,19,true,a); add(3,"Processing code",DataType.N,6,6,false,a);
        add(4,"Amount, transaction",DataType.N,12,12,false,a); add(5,"Amount, settlement",DataType.N,12,12,false,b);
        add(6,"Amount, cardholder billing",DataType.N,12,12,false,b); add(7,"Transmission date and time",DataType.N,10,10,false,a);
        add(8,"Amount, cardholder billing fee",DataType.N,8,8,false,b); add(9,"Conversion rate, settlement",DataType.N,8,8,false,b);
        add(10,"Conversion rate, cardholder billing",DataType.N,8,8,false,b); add(11,"Systems trace audit number",DataType.N,6,6,false,a);
        add(12,"Time, local transaction",DataType.N,6,6,false,a); add(13,"Date, local transaction",DataType.N,4,4,false,a);
        add(14,"Date, expiration",DataType.N,4,4,false,b); add(15,"Date, settlement",DataType.N,4,4,false,b);
        add(16,"Date, conversion",DataType.N,4,4,false,b); add(18,"Merchant type",DataType.N,4,4,false,b);
        add(19,"Acquiring institution country code",DataType.N,3,3,false,b); add(22,"Point of service entry mode",DataType.N,3,3,false,a);
        add(23,"Card sequence number",DataType.N,3,3,false,b); add(24,"Network international identifier",DataType.N,3,3,false,b);
        add(25,"Point of service condition code",DataType.N,2,2,false,a); add(26,"Point of service capture code",DataType.N,2,2,false,b);
        add(28,"Amount, transaction fee",DataType.AN,9,9,false,b); add(30,"Amount, processing fee",DataType.AN,9,9,false,b);
        add(31,"Acquirer reference data",DataType.ANS,0,99,true,b); add(32,"Acquiring institution identification code",DataType.N,0,11,true,p);
        add(33,"Forwarding institution identification code",DataType.N,0,11,true,b); add(35,"Track 2 data",DataType.Z,0,37,true,a);
        add(37,"Retrieval reference number",DataType.AN,12,12,false,a); add(38,"Authorization identification response",DataType.AN,6,6,false,b);
        add(39,"Response code",DataType.AN,2,2,false,b); add(41,"Card acceptor terminal identification",DataType.ANS,8,8,false,a);
        add(42,"Card acceptor identification code",DataType.ANS,15,15,false,a); add(43,"Card acceptor name/location",DataType.ANS,40,40,false,b);
        add(44,"Additional response data",DataType.ANS,0,25,true,b); add(45,"Track 1 data",DataType.ANS,0,76,true,b);
        add(48,"Additional data",DataType.ANS,0,999,true,b); add(49,"Currency code, transaction",DataType.N,3,3,false,a);
        add(50,"Currency code, settlement",DataType.N,3,3,false,b); add(51,"Currency code, cardholder billing",DataType.N,3,3,false,b);
        add(52,"Personal identification number data",DataType.B,8,8,false,b); add(53,"Security related control information",DataType.N,16,16,false,b);
        add(54,"Additional amounts",DataType.ANS,0,120,true,b); add(55,"ICC data",DataType.B,0,999,true,b);
        add(56,"Original data elements",DataType.N,0,35,true,b); add(57,"Authorization life cycle code",DataType.N,3,3,false,b);
        add(58,"Authorizing agent institution id",DataType.AN,0,11,true,b); add(59,"Transport data",DataType.ANS,0,999,true,b);
        add(60,"Reserved national",DataType.ANS,0,999,true,b); add(61,"Reserved national",DataType.ANS,0,999,true,b);
        add(62,"Reserved private",DataType.ANS,0,999,true,b); add(63,"Reserved private",DataType.ANS,0,999,true,b);
        add(64,"Message authentication code",DataType.B,8,8,false,b); add(90,"Original data elements",DataType.N,42,42,false,b);
        add(95,"Replacement amounts",DataType.N,42,42,false,b);
        add(70,"Network management information code",DataType.N,3,3,false,b);
        add(102,"Account identification 1",DataType.ANS,0,28,true,b); add(103,"Account identification 2",DataType.ANS,0,28,true,b);
        add(128,"Message authentication code",DataType.B,8,8,false,b);
    }
    private static void add(int n,String name,DataType t,int len,int max,boolean var,String source){ COMMON.put(n,new FieldDefinition(n,name,t,len,max,var,source)); }

    /**
     * Returns only definitions in the documented common subset; private scheme
     * fields are deliberately not invented. ISO 8583:1993 assigns DE 24 as a
     * function code, whereas ISO 8583:1987 names it the network international
     * identifier (ISO 8583:1987, data-element table; ISO 8583:1993, clause 6).
     */
    public static Map<Integer,FieldDefinition> dictionary(Version version) {
        Map<Integer,FieldDefinition> out = new LinkedHashMap<>(COMMON);
        if (version == Version.ISO_1993) {
            FieldDefinition legacy = out.get(24);
            out.put(24, new FieldDefinition(24, "Function code", legacy.type(), legacy.length(),
                    legacy.maxLength(), legacy.variable(), "ISO 8583:1993 clause 6 data-element table"));
        }
        return Map.copyOf(out);
    }
    public static FieldDefinition fieldDefinition(int number, Version version) { return dictionary(version).get(number); }

    public static Message parse(String asciiMessage, Profile profile) { return parse(asciiMessage.getBytes(StandardCharsets.US_ASCII), profile, profile.bitmapEncoding()==BitmapEncoding.ASCII_HEX); }
    public static Message parse(byte[] message, Profile profile) { return parse(message, profile, profile.bitmapEncoding()==BitmapEncoding.ASCII_HEX); }
    public static Message parseBinary(byte[] message, Profile profile) { return parse(message, profile.withBitmap(BitmapEncoding.BINARY), false); }
    public static Message parseAsciiHex(String message, Profile profile) { return parse(message.getBytes(StandardCharsets.US_ASCII), profile.withBitmap(BitmapEncoding.ASCII_HEX), true); }
    private static Message parse(byte[] data, Profile profile, boolean asciiBitmap) {
        if (data.length<4) throw new IllegalArgumentException("message is shorter than MTI");
        String mti=new String(data,0,4,StandardCharsets.US_ASCII); Mti mt=Mti.parse(mti); requireProfileVersion(mt, profile); int p=4;
        List<byte[]> parts=new ArrayList<>(); byte[] first;
        if (asciiBitmap) { if(data.length<p+16) throw new IllegalArgumentException("missing primary ASCII bitmap"); first=hexBytes(new String(data,p,16,StandardCharsets.US_ASCII)); p+=16; }
        else { if(data.length<p+8) throw new IllegalArgumentException("missing primary bitmap"); first=Arrays.copyOfRange(data,p,p+8); p+=8; }
        parts.add(first); boolean secondBit=bit(first,1);
        while (secondBit) { byte[] b; int width=asciiBitmap?16:8; if(data.length<p+width) throw new IllegalArgumentException("truncated secondary/tertiary bitmap"); b=asciiBitmap?hexBytes(new String(data,p,width,StandardCharsets.US_ASCII)):Arrays.copyOfRange(data,p,p+8); p+=width; parts.add(b); secondBit=bit(b,1); if(parts.size()==3 && secondBit) throw new IllegalArgumentException("bitmap advertises unsupported fourth bitmap"); }
        Map<Integer,ParsedField> fields=new LinkedHashMap<>(); List<String> warnings=new ArrayList<>();
        for(int i=2;i<=parts.size()*64;i++) if(i!=65 && i!=129 && bit(parts.get((i-1)/64),(i-1)%64+1)) {
            FieldDefinition d=dictionary(profile.version()).get(i); int start=p; if(d==null){ warnings.add("field "+i+" is present but not in the common ISO dictionary; remaining bytes cannot be safely parsed"); break; }
            int chars=d.variable?readLength(data, p, profile.lengthEncoding(), d.maxLength):d.length; p+=d.variable?indicatorBytes(profile.lengthEncoding(), d.maxLength):0;
            if(chars<0 || p+encodedSize(chars,d,profile)>data.length) throw new IllegalArgumentException("truncated field "+i);
            int bytes=encodedSize(chars,d,profile); String value=decode(data,p,bytes,chars,d,profile); p+=bytes;
            List<String> fw=new ArrayList<>(); String enrichment=""; if(i==35) enrichment=PaymentOperations.parseTrack2(";"+value.replace('D','=')+"?"); else if(i==45) enrichment=PaymentOperations.parseTrack1(value); else if(i==55) { try { enrichment=EmvTlv.transactionSummary(EmvTlv.analyze(value)); } catch(RuntimeException e){fw.add("field 55 is not valid EMV TLV: "+e.getMessage());} } else if(i==52 && value.length()!=16) fw.add("PIN block normally contains 8 bytes");
            fields.put(i,new ParsedField(i,d,value, p-start, List.copyOf(fw), enrichment)); warnings.addAll(fw);
        }
        if(p<data.length) warnings.add((data.length-p)+" trailing byte(s) after the last declared field");
        return new Message(mt,profile,flatten(parts),parts,fields,warnings);
    }
    private static void requireProfileVersion(Mti mti, Profile profile) {
        Version actual = mti.version() == 0 ? Version.ISO_1987 : mti.version() == 1 ? Version.ISO_1993 : null;
        if (actual == null || actual != profile.version()) throw new IllegalArgumentException("MTI version " + mti.version() + " does not match profile " + profile.version());
    }
    private static int indicatorBytes(LengthEncoding e,int max){return e==LengthEncoding.ASCII?(max>99?3:2): (max>99?2:1);}
    private static int readLength(byte[] d,int p,LengthEncoding e,int max){int n=indicatorBytes(e,max); if(p+n>d.length) throw new IllegalArgumentException("truncated length indicator"); if(e==LengthEncoding.ASCII){String s=new String(d,p,n,StandardCharsets.US_ASCII); if(!s.matches("\\d{"+n+"}"))throw new IllegalArgumentException("invalid ASCII length indicator"); return Integer.parseInt(s);} if(n==1){int x=d[p]&255;if((x&15)>9||(x>>4)>9)throw new IllegalArgumentException("invalid BCD length indicator");return (x>>4)*10+(x&15);} int a=d[p]&255,b=d[p+1]&255;if((a>>4)!=0||(a&15)>9||(b>>4)>9||(b&15)>9)throw new IllegalArgumentException("invalid 3-digit BCD length indicator");return (a&15)*100+(b>>4)*10+(b&15);}
    private static int encodedSize(int chars,FieldDefinition d,Profile p){
        if (d.type==DataType.B && p.bitmapEncoding()==BitmapEncoding.ASCII_HEX) return chars*2;
        return d.type==DataType.B&&p.numericBcd? (chars+1)/2 : d.type==DataType.B? chars : p.numericBcd&&d.type==DataType.N?(chars+1)/2:chars;
    }
    private static String decode(byte[] d,int p,int bytes,int chars,FieldDefinition f,Profile profile){
        if(f.type==DataType.B) return profile.bitmapEncoding()==BitmapEncoding.ASCII_HEX
                ? new String(d,p,bytes,StandardCharsets.US_ASCII) : hex(d,p,bytes);
        if(profile.numericBcd&&f.type==DataType.N){StringBuilder s=new StringBuilder();for(int i=0;i<bytes;i++){int x=d[p+i]&255;s.append(x>>4).append(x&15);}return s.substring(0,chars);}
        return new String(d,p,bytes,StandardCharsets.US_ASCII);
    }
    public static byte[] build(String mti, Map<Integer,String> values, Profile profile){
        Mti m=Mti.parse(mti); requireProfileVersion(m, profile);
        Map<Integer,String> v=values==null?Map.of():values;
        if (v.containsKey(1) || v.containsKey(65) || v.containsKey(129)) throw new IllegalArgumentException("bitmap control fields 1, 65 and 129 are not data values");
        byte[] bm=bitmap(v.keySet()); List<byte[]> parts=new ArrayList<>(); parts.add(Arrays.copyOfRange(bm,0,8));
        if(bit(parts.get(0),1)){parts.add(Arrays.copyOfRange(bm,8,16));if(bm.length>16)parts.add(Arrays.copyOfRange(bm,16,24));}
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); out.writeBytes(mti.getBytes(StandardCharsets.US_ASCII));
        for(byte[] b:parts) out.writeBytes(profile.bitmapEncoding()==BitmapEncoding.ASCII_HEX?hex(b).getBytes(StandardCharsets.US_ASCII):b);
        Map<Integer,FieldDefinition> dictionary = dictionary(profile.version());
        for(int i=2;i<=192;i++) if(v.containsKey(i)){
            FieldDefinition d=dictionary.get(i); if(d==null)throw new IllegalArgumentException("field "+i+" has no definition for "+m.versionName());
            String value=v.get(i)==null?"":v.get(i); validate(value,d);
            if(d.variable)writeLength(out,valueLength(value,d,profile),profile.lengthEncoding(),d.maxLength);
            out.writeBytes(encode(value,d,profile));
        }
        return out.toByteArray();
    }
    private static int valueLength(String s,FieldDefinition d,Profile p){return d.type==DataType.B?s.length()/2:p.numericBcd&&d.type==DataType.N?s.length():s.length();}
    private static byte[] encode(String s,FieldDefinition d,Profile p){if(d.type==DataType.B)return p.bitmapEncoding()==BitmapEncoding.ASCII_HEX?s.getBytes(StandardCharsets.US_ASCII):hexBytes(s);if(p.numericBcd&&d.type==DataType.N){String x=s;if((x.length()&1)==1)x+="0";byte[] b=new byte[x.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)((x.charAt(2*i)-'0')*16+x.charAt(2*i+1)-'0');return b;}return s.getBytes(StandardCharsets.US_ASCII);}
    private static void validate(String s,FieldDefinition d){
        if(d.type==DataType.N&&!s.matches("\\d*"))throw new IllegalArgumentException("field "+d.number+" requires n digits");
        if(d.type==DataType.AN&&!s.matches("[A-Za-z0-9 ]*"))throw new IllegalArgumentException("field "+d.number+" requires an characters");
        if(d.type==DataType.ANS&&!s.matches("[\\x20-\\x7E]*"))throw new IllegalArgumentException("field "+d.number+" requires ans characters");
        if(d.type==DataType.Z&&!s.matches("[0-9D=]*"))throw new IllegalArgumentException("field "+d.number+" requires track-2 z characters");
        if(d.type==DataType.B&&(!s.matches("[0-9A-Fa-f]*")||(s.length()&1)==1))throw new IllegalArgumentException("field "+d.number+" requires even hexadecimal bytes");
        int l=d.type==DataType.B?s.length()/2:s.length();
        if(d.variable?l>d.maxLength:l!=d.length)throw new IllegalArgumentException("field "+d.number+" length "+l+" does not match "+d.shape());
    }
    private static void writeLength(java.io.ByteArrayOutputStream o,int n,LengthEncoding e,int max){int width=max>99?3:2;if(e==LengthEncoding.ASCII){o.writeBytes(String.format(Locale.ROOT,"%0"+width+"d",n).getBytes(StandardCharsets.US_ASCII));}else if(width==2){o.write((n/10)*16+n%10);}else{o.write(n/100);o.write(((n/10)%10)*16+n%10);}}
    public static String report(Message m){
        StringBuilder s = new StringBuilder("MTI ").append(m.mti().prose())
                .append("\nBitmap: ").append(hex(m.bitmap())).append(" (")
                .append(m.profile().bitmapEncoding()).append(")\n");
        for (ParsedField f : m.fields().values()) {
            s.append(String.format(Locale.ROOT, "F%03d %-40s %-3s %-6s = %s\n",
                    f.number(), f.definition().name(), f.definition().type(),
                    f.definition().shape(), f.value()));
            if (!f.enrichment().isBlank()) s.append("  -> ")
                    .append(f.enrichment().replace('\n', ' ')).append('\n');
        }
        for (String warning : m.warnings()) s.append("WARNING: ").append(warning).append('\n');
        return s.toString();
    }
    private static byte[] bitmap(Set<Integer> nums){int max=nums.stream().mapToInt(Integer::intValue).max().orElse(1);if(max>192)throw new IllegalArgumentException("ISO bitmap supports at most 192 fields");int n=max>128?24:max>64?16:8;byte[] b=new byte[n];for(int x:nums){if(x<1||x>192)throw new IllegalArgumentException("field out of range");int z=x-1;b[z/8]|=1<<(7-z%8);}if(n>8)b[0]|=0x80;if(n>16)b[8]|=0x80;return b;}
    private static boolean bit(byte[] b,int n){return (b[(n-1)/8]&(1<<(7-(n-1)%8)))!=0;}private static byte[] flatten(List<byte[]> p){byte[] b=new byte[p.size()*8];for(int i=0;i<p.size();i++)System.arraycopy(p.get(i),0,b,i*8,8);return b;}
    private static String hex(byte[] b){return hex(b,0,b.length);} private static String hex(byte[] b,int off,int len){StringBuilder s=new StringBuilder();for(int i=off;i<off+len;i++)s.append(String.format(Locale.ROOT,"%02X",b[i]&255));return s.toString();}private static byte[] hexBytes(String s){if(!s.matches("(?i)[0-9a-f]+")||(s.length()&1)==1)throw new IllegalArgumentException("invalid hexadecimal");byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16);return b;}
}
