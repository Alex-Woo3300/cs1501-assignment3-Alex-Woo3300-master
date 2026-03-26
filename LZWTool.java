import java.io.*;
import java.util.*;
 
public class LZWTool {
 
    // =========================================================================
    // TST (Ternary Search Trie) — used as encoder codebook
    // =========================================================================
    private static class TST {
        private Node root;
 
        private static class Node {
            char c;
            Node left, mid, right;
            int val = -1;
        }
 
        public void put(String key, int val) {
            root = put(root, key, val, 0);
        }
 
        private Node put(Node x, String key, int val, int d) {
            char c = key.charAt(d);
            if (x == null) { x = new Node(); x.c = c; }
            if      (c < x.c)              x.left  = put(x.left,  key, val, d);
            else if (c > x.c)              x.right = put(x.right, key, val, d);
            else if (d < key.length() - 1) x.mid   = put(x.mid,   key, val, d + 1);
            else                           x.val   = val;
            return x;
        }
 
        public int get(String key) {
            Node x = get(root, key, 0);
            return (x == null) ? -1 : x.val;
        }
 
        private Node get(Node x, String key, int d) {
            if (x == null) return null;
            char c = key.charAt(d);
            if      (c < x.c)              return get(x.left,  key, d);
            else if (c > x.c)              return get(x.right, key, d);
            else if (d < key.length() - 1) return get(x.mid,   key, d + 1);
            else                           return x;
        }
 
        public boolean contains(String key) { return get(key) != -1; }
    }
 
    // =========================================================================
    // Bit-level I/O
    // =========================================================================
    private static final class BitIn {
        private int  buffer   = 0;
        private int  bitsLeft = 0;
        private boolean eof   = false;
        private final InputStream in;
 
        BitIn(InputStream in) { this.in = in; }
 
        private void refill() {
            try {
                int b = in.read();
                if (b == -1) { eof = true; return; }
                buffer   = b;
                bitsLeft = 8;
            } catch (IOException e) { throw new RuntimeException(e); }
        }
 
        public boolean isEmpty() {
            if (bitsLeft == 0) refill();
            return eof;
        }
 
        public int readBits(int width) {
            int val = 0;
            for (int i = 0; i < width; i++) {
                if (bitsLeft == 0) refill();
                if (eof) throw new RuntimeException("Unexpected EOF");
                val      = (val << 1) | ((buffer >> (bitsLeft - 1)) & 1);
                bitsLeft--;
            }
            return val;
        }
 
        public int  readInt()  { return readBits(32); }
        public byte readByte() { return (byte) readBits(8); }
    }
 
    private static final class BitOut {
        private int  buffer   = 0;
        private int  bitsLeft = 8;
        private final OutputStream out;
 
        BitOut(OutputStream out) { this.out = out; }
 
        public void writeBits(int val, int width) {
            for (int i = width - 1; i >= 0; i--) {
                buffer |= (((val >> i) & 1) << (bitsLeft - 1));
                if (--bitsLeft == 0) flush8();
            }
        }
 
        private void flush8() {
            try { out.write(buffer); } catch (IOException e) { throw new RuntimeException(e); }
            buffer   = 0;
            bitsLeft = 8;
        }
 
        public void writeByte(int b) { writeBits(b & 0xFF, 8); }
        public void writeInt(int v)  { writeBits(v, 32); }
 
        public void flush() {
            if (bitsLeft < 8) flush8();
            try { out.flush(); } catch (IOException e) { throw new RuntimeException(e); }
        }
    }
 
    // =========================================================================
    // Main
    // =========================================================================
    public static void main(String[] args) throws IOException {
        String mode         = null;
        int    minW         = 9;
        int    maxW         = 16;
        String policy       = "freeze";
        String alphabetPath = null;
 
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mode":     mode         = args[++i]; break;
                case "--minW":     minW         = Integer.parseInt(args[++i]); break;
                case "--maxW":     maxW         = Integer.parseInt(args[++i]); break;
                case "--policy":   policy       = args[++i]; break;
                case "--alphabet": alphabetPath = args[++i]; break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(2);
            }
        }
 
        if (mode == null) {
            System.err.println("--mode is required"); System.exit(1);
        }
 
        if (mode.equals("compress")) {
            if (minW > maxW)          { System.err.println("minW must be <= maxW"); System.exit(1); }
            if (alphabetPath == null) { System.err.println("--alphabet required");  System.exit(1); }
            File af = new File(alphabetPath);
            if (!af.exists())         { System.err.println("Alphabet file not found: " + alphabetPath); System.exit(1); }
 
            List<String> alphabet = readAlphabet(alphabetPath);
            compress(alphabet, minW, maxW, policy);
 
        } else if (mode.equals("expand")) {
            expand();
        } else {
            System.err.println("--mode must be compress or expand"); System.exit(1);
        }
    }
 
    // =========================================================================
    // Read alphabet file
    // =========================================================================
    private static List<String> readAlphabet(String path) throws IOException {
        LinkedHashSet<Byte> seen = new LinkedHashSet<>();
 
        Scanner sc = new Scanner(new File(path), "UTF-8");
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.isEmpty()) continue;
            seen.add((byte) line.charAt(0));
        }
        sc.close();
 
        // Always add LF and CR (assignment hint)
        seen.add((byte) 10);
        seen.add((byte) 13);
 
        List<String> symbols = new ArrayList<>();
        for (byte b : seen) symbols.add("" + (char)(b & 0xFF));
        return symbols;
    }
 
    // =========================================================================
    // COMPRESS
    //
    // Width-growth rule (encoder and decoder must agree exactly):
    //   After assigning a new code at index N (nextCode becomes N+1),
    //   if nextCode == (1 << W)  AND  W < maxW  =>  W++
    //   The NEXT code written uses the new W.
    //
    // Eviction fires when nextCode == (1 << maxW), i.e. the table is totally full.
    // =========================================================================
    private static void compress(List<String> alphabet, int minW, int maxW, String policy)
            throws IOException {
 
        BitOut bout = new BitOut(System.out);
 
        // Header: minW(1) maxW(1) policy(1) alphabetSize(4) symbols(N)
        bout.writeByte(minW);
        bout.writeByte(maxW);
        bout.writeByte(policy.equals("reset") ? 1 : 0);
        bout.writeInt(alphabet.size());
        for (String sym : alphabet) bout.writeByte(sym.charAt(0));
 
        // State held in arrays so the lambda can capture them
        TST[]  st       = { null };
        int[]  nextCode = { 0 };
        int[]  W        = { minW };
        int    maxCodes = 1 << maxW;
 
        Runnable initCodebook = () -> {
            st[0]       = new TST();
            nextCode[0] = 0;
            for (String sym : alphabet) st[0].put(sym, nextCode[0]++);
            W[0] = minW;
        };
        initCodebook.run();
 
        // Read all input; treat each byte as char 0..255
        byte[] raw = System.in.readAllBytes();
        if (raw.length == 0) { bout.flush(); return; }
 
        char[] input = new char[raw.length];
        for (int i = 0; i < raw.length; i++) input[i] = (char)(raw[i] & 0xFF);
 
        // Classic LZW greedy-match loop
        int pos     = 0;
        String cur  = String.valueOf(input[pos++]);
 
        while (true) {
            // Try to extend cur by one more character
            if (pos < input.length) {
                String extended = cur + input[pos];
                if (st[0].contains(extended)) {
                    cur = extended;
                    pos++;
                    continue;
                }
            }
 
            // Can't extend (or end of input): output code for cur
            int codeVal = st[0].get(cur);
            bout.writeBits(codeVal, W[0]);
            System.err.println("Wrote code=" + codeVal + " W=" + W[0] + " str=\"" + escape(cur) + "\"");
 
            if (pos >= input.length) break; // nothing left to do
 
            // Attempt to add cur+input[pos] as a new codebook entry
            String newEntry = cur + input[pos];
 
            if (nextCode[0] < maxCodes) {
                // Room available — assign and possibly grow W
                st[0].put(newEntry, nextCode[0]);
                System.err.println("Added code=" + nextCode[0] + " str=\"" + escape(newEntry) + "\"");
                nextCode[0]++;
 
                // Grow width if we just filled the slots representable at W[0]
                if (nextCode[0] == (1 << W[0]) && W[0] < maxW) {
                    W[0]++;
                    System.err.println("Width grew to W=" + W[0]);
                }
            } else {
                // Table completely full
                if (policy.equals("reset")) {
                    System.err.println("Codebook full — RESET");
                    initCodebook.run();
                    // Re-add with fresh table
                    st[0].put(newEntry, nextCode[0]);
                    System.err.println("After reset, added code=" + nextCode[0] + " str=\"" + escape(newEntry) + "\"");
                    nextCode[0]++;
                } else {
                    System.err.println("Codebook full — FREEZE");
                }
            }
 
            // Continue from the character we couldn't extend past
            cur = String.valueOf(input[pos]);
            pos++;
        }
 
        bout.flush();
    }
 
    // =========================================================================
    // EXPAND
    //
    // Mirrors the encoder's codebook state exactly.
    // Width grows at the same moment: after nextCode becomes (1 << W) and W < maxW.
    // =========================================================================
    private static void expand() throws IOException {
        BitIn  bin  = new BitIn(System.in);
        BufferedOutputStream bout = new BufferedOutputStream(System.out);
 
        // Read header
        int minW         = bin.readBits(8);
        int maxW         = bin.readBits(8);
        int polByte      = bin.readBits(8);
        String policy    = (polByte == 1) ? "reset" : "freeze";
        int alphabetSize = bin.readInt();
 
        List<String> alphabet = new ArrayList<>();
        for (int i = 0; i < alphabetSize; i++) {
            alphabet.add("" + (char)(bin.readByte() & 0xFF));
        }
 
        System.err.println("Header: minW=" + minW + " maxW=" + maxW
                + " policy=" + policy + " alphabetSize=" + alphabetSize);
 
        int maxCodes  = 1 << maxW;
        String[] table = new String[maxCodes];
        int[]  nextCode = { 0 };
        int[]  W        = { minW };
 
        Runnable initTable = () -> {
            Arrays.fill(table, null);
            nextCode[0] = 0;
            for (String sym : alphabet) table[nextCode[0]++] = sym;
            W[0] = minW;
        };
        initTable.run();
 
        if (bin.isEmpty()) { bout.flush(); return; }
 
        // First code
        int    codeVal = bin.readBits(W[0]);
        String entry   = table[codeVal];
        if (entry == null) throw new RuntimeException("Bad first code: " + codeVal);
 
        writeString(bout, entry);
        System.err.println("Read code=" + codeVal + " W=" + W[0] + " -> \"" + escape(entry) + "\"");
 
        String prev = entry;
 
        while (!bin.isEmpty()) {
            codeVal = bin.readBits(W[0]);
            System.err.println("Read code=" + codeVal + " W=" + W[0]);
 
            // Resolve code
            if (table[codeVal] != null) {
                entry = table[codeVal];
            } else if (codeVal == nextCode[0]) {
                // Classic look-ahead case: encoder added this entry but
                // decoder hasn't yet — it must be prev + prev[0]
                entry = prev + prev.charAt(0);
            } else {
                throw new RuntimeException("Unexpected code: " + codeVal + " next=" + nextCode[0]);
            }
 
            writeString(bout, entry);
            System.err.println("Decoded \"" + escape(entry) + "\"");
 
            // Add new entry — same logic as encoder
            if (nextCode[0] < maxCodes) {
                String newEntry = prev + entry.charAt(0);
                table[nextCode[0]] = newEntry;
                System.err.println("Added code=" + nextCode[0] + " -> \"" + escape(newEntry) + "\"");
                nextCode[0]++;
 
                if (nextCode[0] == (1 << W[0]) && W[0] < maxW) {
                    W[0]++;
                    System.err.println("Width grew to W=" + W[0]);
                }
            } else {
                if (policy.equals("reset")) {
                    System.err.println("Codebook full — RESET");
                    initTable.run();
                } else {
                    System.err.println("Codebook full — FREEZE");
                }
            }
 
            prev = entry;
        }
 
        bout.flush();
    }
 
    // =========================================================================
    // Helpers
    // =========================================================================
    private static void writeString(OutputStream out, String s) throws IOException {
        for (int i = 0; i < s.length(); i++) out.write(s.charAt(i) & 0xFF);
    }
 
    private static String escape(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r");
    }
}
 