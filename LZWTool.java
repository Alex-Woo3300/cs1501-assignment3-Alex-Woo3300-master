import java.io.*;
import java.util.*;

public class LZWTool {

    // -------------------------------------------------------------------------
    // TST (Ternary Search Trie) for compression codebook
    // -------------------------------------------------------------------------
    private static class TST {
        private Node root;
        private int size;

        private static class Node {
            char c;
            Node left, mid, right;
            int val = -1;
        }

        public int size() { return size; }

        public void put(String key, int val) {
            root = put(root, key, val, 0);
        }

        private Node put(Node x, String key, int val, int d) {
            char c = key.charAt(d);
            if (x == null) { x = new Node(); x.c = c; }
            if      (c < x.c)               x.left  = put(x.left,  key, val, d);
            else if (c > x.c)               x.right = put(x.right, key, val, d);
            else if (d < key.length() - 1)  x.mid   = put(x.mid,   key, val, d + 1);
            else {
                if (x.val == -1) size++;
                x.val = val;
            }
            return x;
        }

        public int get(String key) {
            Node x = get(root, key, 0);
            if (x == null) return -1;
            return x.val;
        }

        private Node get(Node x, String key, int d) {
            if (x == null) return null;
            char c = key.charAt(d);
            if      (c < x.c) return get(x.left,  key, d);
            else if (c > x.c) return get(x.right, key, d);
            else if (d < key.length() - 1) return get(x.mid, key, d + 1);
            else return x;
        }

        public boolean contains(String key) {
            return get(key) != -1;
        }
    }

    // -------------------------------------------------------------------------
    // BinaryStdIn / BinaryStdOut wrappers using System.in / System.out
    // These replicate the textbook classes without requiring separate files.
    // -------------------------------------------------------------------------
    private static final class BitIn {
        private int buffer = 0;
        private int bitsLeft = 0;
        private boolean eof = false;
        private final InputStream in;

        BitIn(InputStream in) { this.in = in; }

        private void refill() {
            try {
                int b = in.read();
                if (b == -1) { eof = true; return; }
                buffer = b;
                bitsLeft = 8;
            } catch (IOException e) { throw new RuntimeException(e); }
        }

        public boolean isEmpty() {
            if (bitsLeft == 0) refill();
            return eof;
        }

        public int readInt(int width) {
            int val = 0;
            for (int i = 0; i < width; i++) {
                if (bitsLeft == 0) refill();
                if (eof) throw new RuntimeException("Unexpected EOF");
                val = (val << 1) | ((buffer >> (bitsLeft - 1)) & 1);
                bitsLeft--;
            }
            return val;
        }

        public byte readByte() { return (byte) readInt(8); }
        public int readInt()   { return readInt(32); }
        public char readChar() { return (char)(readInt(8) & 0xFF); }

        public String readString(int bytes) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes; i++) sb.append((char)(readByte() & 0xFF));
            return sb.toString();
        }
    }

    private static final class BitOut {
        private int buffer = 0;
        private int bitsLeft = 8;
        private final OutputStream out;

        BitOut(OutputStream out) { this.out = out; }

        public void write(int val, int width) {
            for (int i = width - 1; i >= 0; i--) {
                int bit = (val >> i) & 1;
                buffer |= (bit << (bitsLeft - 1));
                bitsLeft--;
                if (bitsLeft == 0) flush8();
            }
        }

        private void flush8() {
            try { out.write(buffer); } catch (IOException e) { throw new RuntimeException(e); }
            buffer = 0;
            bitsLeft = 8;
        }

        public void writeByte(int b) { write(b & 0xFF, 8); }
        public void writeInt(int v)  { write(v, 32); }

        public void writeString(String s) {
            for (int i = 0; i < s.length(); i++) writeByte(s.charAt(i));
        }

        public void flush() {
            if (bitsLeft < 8) flush8();
            try { out.flush(); } catch (IOException e) { throw new RuntimeException(e); }
        }
    }

    // -------------------------------------------------------------------------
    // Main
    // -------------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        // Defaults
        String mode        = null;
        int    minW        = 9;
        int    maxW        = 16;
        String policy      = "freeze";
        String alphabetPath = null;

        // Parse arguments
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

        // Validate
        if (mode == null) { System.err.println("--mode is required"); System.exit(1); }
        if (!mode.equals("compress") && !mode.equals("expand")) {
            System.err.println("--mode must be 'compress' or 'expand'"); System.exit(1);
        }

        if (mode.equals("compress")) {
            if (minW > maxW) { System.err.println("minW must be <= maxW"); System.exit(1); }
            if (alphabetPath == null) { System.err.println("--alphabet is required for compress"); System.exit(1); }

            File af = new File(alphabetPath);
            if (!af.exists()) { System.err.println("Alphabet file not found: " + alphabetPath); System.exit(1); }

            List<String> alphabet = readAlphabet(alphabetPath);
            compress(alphabet, minW, maxW, policy);
        } else {
            expand();
        }
    }

    // -------------------------------------------------------------------------
    // Read alphabet file
    // -------------------------------------------------------------------------
    private static List<String> readAlphabet(String path) throws IOException {
        List<String> symbols = new ArrayList<>();
        Set<Byte> seen = new LinkedHashSet<>();

        Scanner sc = new Scanner(new File(path), "UTF-8");
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.isEmpty()) continue;
            byte b = (byte) line.charAt(0);
            if (seen.add(b)) {
                symbols.add("" + (char)(b & 0xFF));
            }
        }
        sc.close();

        // Add newline characters as per assignment hint
        byte lf = (byte) 10;
        byte cr = (byte) 13;
        if (seen.add(lf)) symbols.add("" + (char) 10);
        if (seen.add(cr)) symbols.add("" + (char) 13);

        return symbols;
    }

    // -------------------------------------------------------------------------
    // COMPRESS
    // -------------------------------------------------------------------------
    private static void compress(List<String> alphabet, int minW, int maxW, String policy) throws IOException {
        BitOut bout = new BitOut(System.out);

        // ----- Write header -----
        // Header format:
        //   1 byte : minW
        //   1 byte : maxW
        //   1 byte : policy  (0 = freeze, 1 = reset)
        //   4 bytes: alphabet size (number of symbols, as int)
        //   N bytes: each symbol written as 1 byte (first byte of the symbol string)
        bout.writeByte(minW);
        bout.writeByte(maxW);
        bout.writeByte(policy.equals("reset") ? 1 : 0);
        bout.writeInt(alphabet.size());
        for (String sym : alphabet) {
            bout.writeByte(sym.charAt(0));
        }

        // ----- Build initial codebook (TST) -----
        TST st = new TST();
        int code = 0;
        for (String sym : alphabet) {
            st.put(sym, code++);
        }
        // 'code' is now the next available code index

        int W = minW; // current codeword width

        // Read all input bytes
        byte[] inputBytes;
        try { inputBytes = System.in.readAllBytes(); }
        catch (IOException e) { throw new RuntimeException(e); }

        if (inputBytes.length == 0) {
            bout.flush();
            return;
        }

        // Convert to string (treat each byte as a character in range 0-255)
        // We use ISO-8859-1 mapping: byte value == char value
        StringBuilder input = new StringBuilder(inputBytes.length);
        for (byte b : inputBytes) input.append((char)(b & 0xFF));

        int i = 0;
        String current = "" + input.charAt(i++);

        while (i <= input.length()) {
            // Find longest match in codebook
            String next = (i < input.length()) ? current + input.charAt(i) : null;

            if (next != null && st.contains(next)) {
                current = next;
                i++;
            } else {
                // Output code for current
                int codeVal = st.get(current);
                bout.write(codeVal, W);
                System.err.println("Wrote code=" + codeVal + " W=" + W + " for \"" + escape(current) + "\"");

                // Try to add new entry
                if (next != null) {
                    int capacity = 1 << W;
                    if (code < capacity) {
                        // Room in current width
                        st.put(next, code++);
                        System.err.println("Added code=" + (code-1) + " -> \"" + escape(next) + "\"");
                    } else if (W < maxW) {
                        // Grow width first, then add
                        W++;
                        st.put(next, code++);
                        System.err.println("Width grew to W=" + W + ", added code=" + (code-1) + " -> \"" + escape(next) + "\"");
                    } else {
                        // Codebook full — apply eviction policy
                        if (policy.equals("reset")) {
                            System.err.println("Codebook full — RESET");
                            st = new TST();
                            code = 0;
                            for (String sym : alphabet) st.put(sym, code++);
                            W = minW;
                            // Add the new phrase after reset
                            if (code < (1 << W)) {
                                st.put(next, code++);
                                System.err.println("After reset, added code=" + (code-1) + " -> \"" + escape(next) + "\"");
                            }
                        } else {
                            // freeze: do nothing, just keep encoding with existing codebook
                            System.err.println("Codebook full — FREEZE");
                        }
                    }
                    current = "" + input.charAt(i);
                    i++;
                } else {
                    break; // end of input
                }
            }
        }

        // Output the last code
        if (!current.isEmpty()) {
            int codeVal = st.get(current);
            if (codeVal != -1) {
                bout.write(codeVal, W);
                System.err.println("Wrote final code=" + codeVal + " W=" + W + " for \"" + escape(current) + "\"");
            }
        }

        bout.flush();
    }

    // -------------------------------------------------------------------------
    // EXPAND
    // -------------------------------------------------------------------------
    private static void expand() throws IOException {
        BitIn bin = new BitIn(System.in);
        BufferedOutputStream bout = new BufferedOutputStream(System.out);

        // ----- Read header -----
        int minW   = bin.readInt(8);
        int maxW   = bin.readInt(8);
        int polByte = bin.readInt(8);
        String policy = (polByte == 1) ? "reset" : "freeze";
        int alphabetSize = bin.readInt(32);

        List<String> alphabet = new ArrayList<>();
        for (int i = 0; i < alphabetSize; i++) {
            int b = bin.readInt(8);
            alphabet.add("" + (char) b);
        }

        System.err.println("Header: minW=" + minW + " maxW=" + maxW + " policy=" + policy + " alphabetSize=" + alphabetSize);

        // ----- Build initial decode table -----
        // table[code] = string
        String[] table = new String[1 << maxW];
        int code = 0;
        for (String sym : alphabet) {
            table[code++] = sym;
        }
        // 'code' is next available

        int W = minW;

        if (bin.isEmpty()) {
            bout.flush();
            return;
        }

        // Read first code
        int codeVal = bin.readInt(W);
        String val = table[codeVal];
        if (val == null) throw new RuntimeException("Bad first code: " + codeVal);

        bout.write(val.charAt(0) & 0xFF);
        System.err.println("Read code=" + codeVal + " W=" + W + " -> \"" + escape(val) + "\"");

        String prev = val;

        while (!bin.isEmpty()) {
            codeVal = bin.readInt(W);
            System.err.println("Read code=" + codeVal + " W=" + W);

            String entry;
            if (table[codeVal] != null) {
                entry = table[codeVal];
            } else if (codeVal == code) {
                // Special case: code not yet in table
                entry = prev + prev.charAt(0);
            } else {
                throw new RuntimeException("Unexpected code: " + codeVal + " next=" + code);
            }

            // Output decoded string
            for (int i = 0; i < entry.length(); i++) {
                bout.write(entry.charAt(i) & 0xFF);
            }
            System.err.println("Decoded \"" + escape(entry) + "\"");

            // Add new entry to table
            int capacity = 1 << W;
            if (code < capacity) {
                table[code++] = prev + entry.charAt(0);
                System.err.println("Added code=" + (code-1) + " -> \"" + escape(prev + entry.charAt(0)) + "\"");
            } else if (W < maxW) {
                W++;
                table[code++] = prev + entry.charAt(0);
                System.err.println("Width grew to W=" + W + ", added code=" + (code-1));
            } else {
                // Codebook full
                if (policy.equals("reset")) {
                    System.err.println("Codebook full — RESET");
                    table = new String[1 << maxW];
                    code = 0;
                    for (String sym : alphabet) table[code++] = sym;
                    W = minW;
                } else {
                    System.err.println("Codebook full — FREEZE");
                }
            }

            prev = entry;
        }

        bout.flush();
    }

    // -------------------------------------------------------------------------
    // Debug helper
    // -------------------------------------------------------------------------
    private static String escape(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r");
    }
}