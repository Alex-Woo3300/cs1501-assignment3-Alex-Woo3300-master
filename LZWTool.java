
Copy

import java.io.*;
import java.util.*;
 
public class LZWTool {
 
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
 
        if (mode == null) { System.err.println("--mode required"); System.exit(1); }
 
        if (mode.equals("compress")) {
            if (minW > maxW)          { System.err.println("minW must be <= maxW"); System.exit(1); }
            if (alphabetPath == null) { System.err.println("--alphabet required"); System.exit(1); }
            File af = new File(alphabetPath);
            if (!af.exists())         { System.err.println("Alphabet not found: " + alphabetPath); System.exit(1); }
            compress(readAlphabet(alphabetPath), minW, maxW, policy);
        } else if (mode.equals("expand")) {
            expand();
        } else {
            System.err.println("--mode must be compress or expand"); System.exit(1);
        }
    }
 
    // -------------------------------------------------------------------------
    // Read alphabet: use Scanner.nextLine() with UTF-8, take charAt(0) cast to byte,
    // then append LF and CR if not already present.
    // -------------------------------------------------------------------------
    private static List<Integer> readAlphabet(String path) throws IOException {
        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        Scanner sc = new Scanner(new File(path), "UTF-8");
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.isEmpty()) continue;
            int b = line.charAt(0) & 0xFF;
            seen.add(b);
        }
        sc.close();
        // Append LF (10) and CR (13) if not already present
        seen.add(10);
        seen.add(13);
        return new ArrayList<>(seen);
    }
 
    // -------------------------------------------------------------------------
    // COMPRESS
    //
    // Width grows when nextCode == (1 << W) and W < maxW.
    // Eviction fires when nextCode == (1 << maxW).
    // -------------------------------------------------------------------------
    private static void compress(List<Integer> alphabet, int minW, int maxW, String policy) {
        // Write header: minW(1) maxW(1) policy(1) alphabetSize(4) symbols(N bytes)
        BinaryStdOut.write((char) minW, 8);
        BinaryStdOut.write((char) maxW, 8);
        BinaryStdOut.write((char)(policy.equals("reset") ? 1 : 0), 8);
        BinaryStdOut.write(alphabet.size());
        for (int b : alphabet) BinaryStdOut.write((char) b, 8);
 
        // Mutable state
        TSTmod<Integer>[] st     = new TSTmod[1];
        int[]             nextCode = { 0 };
        int[]             W        = { minW };
        int               maxCodes = 1 << maxW;
 
        Runnable init = () -> {
            st[0]       = new TSTmod<>();
            nextCode[0] = 0;
            W[0]        = minW;
            for (int b : alphabet) {
                st[0].put(new StringBuilder("" + (char) b), nextCode[0]++);
            }
        };
        init.run();
 
        if (BinaryStdIn.isEmpty()) { BinaryStdOut.flush(); return; }
 
        // Read all input bytes
        StringBuilder input = new StringBuilder();
        while (!BinaryStdIn.isEmpty()) input.append(BinaryStdIn.readChar());
 
        if (input.length() == 0) { BinaryStdOut.flush(); return; }
 
        int pos = 0;
        StringBuilder cur = new StringBuilder();
        cur.append(input.charAt(pos++));
 
        while (true) {
            // Try to extend cur
            if (pos < input.length()) {
                StringBuilder extended = new StringBuilder(cur);
                extended.append(input.charAt(pos));
                if (st[0].contains(extended)) {
                    cur = extended;
                    pos++;
                    continue;
                }
            }
 
            // Output code for cur
            int codeVal = st[0].get(cur);
            BinaryStdOut.write(codeVal, W[0]);
            System.err.println("Wrote code=" + codeVal + " W=" + W[0] + " str=\"" + escape(cur.toString()) + "\"");
 
            if (pos >= input.length()) break;
 
            // Build new entry = cur + input[pos]
            StringBuilder newEntry = new StringBuilder(cur);
            newEntry.append(input.charAt(pos));
 
            if (nextCode[0] < maxCodes) {
                st[0].put(newEntry, nextCode[0]);
                System.err.println("Added code=" + nextCode[0] + " str=\"" + escape(newEntry.toString()) + "\"");
                nextCode[0]++;
                // Grow width if we just filled current width's slots
                if (nextCode[0] == (1 << W[0]) && W[0] < maxW) {
                    W[0]++;
                    System.err.println("Width grew to W=" + W[0]);
                }
            } else {
                // Table full
                if (policy.equals("reset")) {
                    System.err.println("Codebook full — RESET");
                    init.run();
                    st[0].put(newEntry, nextCode[0]);
                    System.err.println("After reset, added code=" + nextCode[0]);
                    nextCode[0]++;
                } else {
                    System.err.println("Codebook full — FREEZE");
                }
            }
 
            cur = new StringBuilder();
            cur.append(input.charAt(pos));
            pos++;
        }
 
        BinaryStdOut.flush();
    }
 
    // -------------------------------------------------------------------------
    // EXPAND
    // -------------------------------------------------------------------------
    private static void expand() {
        // Read header
        int minW         = BinaryStdIn.readChar(8);
        int maxW         = BinaryStdIn.readChar(8);
        int polByte      = BinaryStdIn.readChar(8);
        String policy    = (polByte == 1) ? "reset" : "freeze";
        int alphabetSize = BinaryStdIn.readInt();
 
        List<Integer> alphabet = new ArrayList<>();
        for (int i = 0; i < alphabetSize; i++) {
            alphabet.add((int) BinaryStdIn.readChar(8));
        }
 
        System.err.println("Header: minW=" + minW + " maxW=" + maxW
                + " policy=" + policy + " alphabetSize=" + alphabetSize);
 
        int maxCodes    = 1 << maxW;
        String[] table  = new String[maxCodes];
        int[]  nextCode = { 0 };
        int[]  W        = { minW };
 
        Runnable initTable = () -> {
            Arrays.fill(table, null);
            nextCode[0] = 0;
            W[0]        = minW;
            for (int b : alphabet) table[nextCode[0]++] = "" + (char)(b & 0xFF);
        };
        initTable.run();
 
        if (BinaryStdIn.isEmpty()) { BinaryStdOut.flush(); return; }
 
        // First code
        int    codeVal = BinaryStdIn.readInt(W[0]);
        String entry   = table[codeVal];
        if (entry == null) throw new RuntimeException("Bad first code: " + codeVal);
 
        BinaryStdOut.write(entry);
        System.err.println("Read code=" + codeVal + " W=" + W[0] + " -> \"" + escape(entry) + "\"");
        String prev = entry;
 
        while (!BinaryStdIn.isEmpty()) {
            codeVal = BinaryStdIn.readInt(W[0]);
            System.err.println("Read code=" + codeVal + " W=" + W[0]);
 
            if (table[codeVal] != null) {
                entry = table[codeVal];
            } else if (codeVal == nextCode[0]) {
                entry = prev + prev.charAt(0);
            } else {
                throw new RuntimeException("Unexpected code: " + codeVal + " next=" + nextCode[0]);
            }
 
            BinaryStdOut.write(entry);
            System.err.println("Decoded \"" + escape(entry) + "\"");
 
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
 
        BinaryStdOut.flush();
    }
 
    private static String escape(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r");
    }
}