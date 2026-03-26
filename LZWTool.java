import java.util.*;
import java.io.*;

public class LZWTool {

    public static void main(String[] args) throws Exception {

        String mode = "";
        int minW = 9;
        int maxW = 16;
        String policy = "freeze";
        String alphabetPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mode": mode = args[++i]; break;
                case "--minW": minW = Integer.parseInt(args[++i]); break;
                case "--maxW": maxW = Integer.parseInt(args[++i]); break;
                case "--policy": policy = args[++i]; break;
                case "--alphabet": alphabetPath = args[++i]; break;
            }
        }

        if (mode.equals("compress")) compress(minW, maxW, policy, alphabetPath);
        else expand();
    }

    // ================= COMPRESS =================
    private static void compress(int minW, int maxW, String policy, String alphabetPath) throws Exception {

        List<Character> alphabet = readAlphabet(alphabetPath);

        Map<String, Integer> st = new HashMap<>();

        int code = 0;
        for (char c : alphabet) {
            st.put("" + c, code++);
        }

        int EOF = code++;
        int W = minW;
        int L = 1 << W;

        // HEADER
        BinaryStdOut.write(minW, 8);
        BinaryStdOut.write(maxW, 8);
        BinaryStdOut.write(policy.equals("reset") ? 1 : 0, 1);
        BinaryStdOut.write(alphabet.size(), 16);

        for (char c : alphabet) {
            BinaryStdOut.write(c, 8);
        }

        // READ INPUT CORRECTLY
        StringBuilder sb = new StringBuilder();
        while (!BinaryStdIn.isEmpty()) {
            sb.append(BinaryStdIn.readChar());
        }
        String input = sb.toString();

        String w = "";

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (st.containsKey(w + c)) {
                w = w + c;
            } else {
                BinaryStdOut.write(st.get(w), W);

                // HANDLE WIDTH FIRST
                if (code == L) {
                    if (W < maxW) {
                        W++;
                        L = 1 << W;
                    } else if (policy.equals("reset")) {
                        st.clear();
                        code = 0;
                        for (char ch : alphabet) {
                            st.put("" + ch, code++);
                        }
                        EOF = code++;
                        W = minW;
                        L = 1 << W;
                    }
                }

                if (code < L) {
                    st.put(w + c, code++);
                }

                w = "" + c;
            }
        }

        if (!w.equals("")) {
            BinaryStdOut.write(st.get(w), W);
        }

        BinaryStdOut.write(EOF, W);
        BinaryStdOut.flush();
    }

    // ================= EXPAND =================
    private static void expand() {

        int minW = BinaryStdIn.readInt(8);
        int maxW = BinaryStdIn.readInt(8);
        boolean reset = BinaryStdIn.readInt(1) == 1;

        int alphaSize = BinaryStdIn.readInt(16);

        List<String> alphabet = new ArrayList<>();

        for (int i = 0; i < alphaSize; i++) {
            alphabet.add("" + BinaryStdIn.readChar(8));
        }

        List<String> st = new ArrayList<>(alphabet);

        int code = st.size();
        int EOF = code++;
        int W = minW;
        int L = 1 << W;

        int prev = BinaryStdIn.readInt(W);
        if (prev == EOF) return;

        String val = st.get(prev);
        BinaryStdOut.write(val);

        while (true) {

            int curr;
            try {
                curr = BinaryStdIn.readInt(W);
            } catch (Exception e) {
                break;
            }

            if (curr == EOF) break;

            String s;

            if (curr < st.size()) {
                s = st.get(curr);
            } else {
                s = val + val.charAt(0);
            }

            BinaryStdOut.write(s);

            // HANDLE WIDTH FIRST
            if (code == L) {
                if (W < maxW) {
                    W++;
                    L = 1 << W;
                } else if (reset) {
                    st.clear();
                    st.addAll(alphabet);
                    code = st.size();
                    EOF = code++;
                    W = minW;
                    L = 1 << W;
                }
            }

            if (code < L) {
                st.add(val + s.charAt(0));
                code++;
            }

            val = s;
        }

        BinaryStdOut.flush();
    }

    // ================= ALPHABET =================
    private static List<Character> readAlphabet(String path) throws Exception {

        List<Character> list = new ArrayList<>();
        Set<Character> seen = new HashSet<>();

        BufferedReader br = new BufferedReader(new FileReader(path));

        String line;
        while ((line = br.readLine()) != null) {
            if (!line.isEmpty()) {
                char c = line.charAt(0);
                if (!seen.contains(c)) {
                    seen.add(c);
                    list.add(c);
                }
            }
        }

        list.add('\n');
        list.add('\r');

        return list;
    }
}