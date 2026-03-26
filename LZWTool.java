import java.io.*;
import java.util.*;

public class LZWTool {

    public static void main(String[] args) {
        String mode = null, policy = "freeze", alphabetPath = null;
        int minW = 9, maxW = 16;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mode": mode = args[++i]; break;
                case "--minW": minW = Integer.parseInt(args[++i]); break;
                case "--maxW": maxW = Integer.parseInt(args[++i]); break;
                case "--policy": policy = args[++i]; break;
                case "--alphabet": alphabetPath = args[++i]; break;
            }
        }

        if (mode == null) {
            System.err.println("Missing --mode");
            return;
        }

        if (mode.equals("compress")) {
            List<String> alphabet = loadAlphabet(alphabetPath);
            compress(minW, maxW, policy, alphabet);
        } else {
            expand();
        }
    }

    private static List<String> loadAlphabet(String path) {
        List<String> alphabet = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty()) {
                    String s = "" + line.charAt(0);
                    if (seen.add(s)) alphabet.add(s);
                }
            }
        } catch (IOException e) {
            return null;
        }

        // add newline chars
        if (seen.add("" + (char)10)) alphabet.add("" + (char)10);
        if (seen.add("" + (char)13)) alphabet.add("" + (char)13);

        return alphabet;
    }

    private static class Header {
        int minW, maxW, alphabetSize;
        String policy;
        List<String> alphabet;
    }

    private static void writeHeader(int minW, int maxW, String policy, List<String> alphabet) {
        BinaryStdOut.write(minW, 8);
        BinaryStdOut.write(maxW, 8);

        int p = policy.equals("reset") ? 1 : 0;
        BinaryStdOut.write(p, 8);

        BinaryStdOut.write(alphabet.size(), 16);
        for (String s : alphabet)
            BinaryStdOut.write(s.charAt(0), 8);
    }

    private static Header readHeader() {
        Header h = new Header();
        h.minW = BinaryStdIn.readInt(8);
        h.maxW = BinaryStdIn.readInt(8);

        int p = BinaryStdIn.readInt(8);
        h.policy = (p == 1) ? "reset" : "freeze";

        h.alphabetSize = BinaryStdIn.readInt(16);
        h.alphabet = new ArrayList<>();

        for (int i = 0; i < h.alphabetSize; i++)
            h.alphabet.add("" + BinaryStdIn.readChar(8));

        return h;
    }

    private static void compress(int minW, int maxW, String policy, List<String> alphabet) {

        writeHeader(minW, maxW, policy, alphabet);

        TSTmod<Integer> st = new TSTmod<>();

        int alphabetSize = alphabet.size();
        int code = 0;

        for (String s : alphabet)
            st.put(new StringBuilder(s), code++);

        int EOF = code++;
        int RESET = policy.equals("reset") ? code++ : -1;
        int initialCode = code;

        int W = minW;
        int maxCode = 1 << maxW;

        if (BinaryStdIn.isEmpty()) {
            BinaryStdOut.close();
            return;
        }

        StringBuilder current = new StringBuilder();
        current.append(BinaryStdIn.readChar());

        while (!BinaryStdIn.isEmpty()) {
            char c = BinaryStdIn.readChar();

            StringBuilder next = new StringBuilder(current).append(c);

            if (st.contains(next)) {
                current.append(c);
            } else {
                BinaryStdOut.write(st.get(current), W);

                if (code < maxCode) {
                    if (code >= (1 << W) && W < maxW) W++;
                    st.put(next, code++);
                }
                else {
                    if (policy.equals("reset")) {
                        if (code >= (1 << W) && W < maxW) W++;

                        BinaryStdOut.write(RESET, W);

                        st = new TSTmod<>();
                        for (int i = 0; i < alphabetSize; i++)
                            st.put(new StringBuilder(alphabet.get(i)), i);

                        code = initialCode;
                        W = minW;
                    }
                    
                }

                current = new StringBuilder().append(c);
            }
        }

        BinaryStdOut.write(st.get(current), W);

        if (code >= (1 << W) && W < maxW) W++;

        BinaryStdOut.write(EOF, W);
        BinaryStdOut.close();
    }

    private static void expand() {

        Header h = readHeader();

        int alphabetSize = h.alphabetSize;
        int maxCode = 1 << h.maxW;

        String[] st = new String[maxCode];

        for (int i = 0; i < alphabetSize; i++)
            st[i] = h.alphabet.get(i);

        int EOF = alphabetSize;
        int RESET = h.policy.equals("reset") ? alphabetSize + 1 : -1;

        int code = h.policy.equals("reset") ? alphabetSize + 2 : alphabetSize + 1;

        int W = h.minW;

        int prev = BinaryStdIn.readInt(W);
        if (prev == EOF) return;

        String val = st[prev];
        BinaryStdOut.write(val);

        while (true) {

            if (code >= (1 << W) && W < h.maxW) W++;

            int curr = BinaryStdIn.readInt(W);
            if (curr == EOF) break;

            if (h.policy.equals("reset") && curr == RESET) {
                Arrays.fill(st, alphabetSize, st.length, null);
                code = alphabetSize + 2;
                W = h.minW;

                curr = BinaryStdIn.readInt(W);
                if (curr == EOF) break;

                val = st[curr];
                BinaryStdOut.write(val);
                continue;
            }

            String s = st[curr];
            if (s == null) s = val + val.charAt(0);

            BinaryStdOut.write(s);

            if (code < maxCode)
                st[code++] = val + s.charAt(0);

            val = s;
        }

        BinaryStdOut.close();
    }
}