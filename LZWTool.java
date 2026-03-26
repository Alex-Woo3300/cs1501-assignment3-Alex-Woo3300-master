import java.io.*;
import java.util.*;

public class LZWTool {

    public static void main(String[] args) throws Exception {

        String mode = "";
        int minW = 9;
        int maxW = 16;
        String policy = "freeze";
        String alphabetPath = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--mode")) mode = args[++i];
            else if (args[i].equals("--minW")) minW = Integer.parseInt(args[++i]);
            else if (args[i].equals("--maxW")) maxW = Integer.parseInt(args[++i]);
            else if (args[i].equals("--policy")) policy = args[++i];
            else if (args[i].equals("--alphabet")) alphabetPath = args[++i];
        }

        if (mode.equals("compress")) {
            compress(minW, maxW, policy, alphabetPath);
        } else {
            expand();
        }
    }

    // ================= COMPRESS =================
    private static void compress(int minW, int maxW, String policy, String alphabetPath) throws Exception {

        List<Character> alphabet = readAlphabet(alphabetPath);

        Map<String, Integer> st = new HashMap<>();

        int code = 0;
        for (char c : alphabet) {
            st.put("" + c, code++);
        }

        int W = minW;
        int L = 1 << W;

        // WRITE HEADER
        BinaryStdOut.write(minW);
        BinaryStdOut.write(maxW);
        BinaryStdOut.write(policy.equals("reset") ? 1 : 0);
        BinaryStdOut.write(alphabet.size());

        for (char c : alphabet) {
            BinaryStdOut.write(c);
        }

        String input = BinaryStdIn.readString();

        String w = "";

        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);

            if (st.containsKey(w + c)) {
                w = w + c;
            } else {

                BinaryStdOut.write(st.get(w), W);

                if (code < L) {
                    st.put(w + c, code++);
                } else {

                    if (W < maxW) {
                        W++;
                        L = 1 << W;
                        st.put(w + c, code++);
                    } else if (policy.equals("reset")) {

                        st.clear();
                        code = 0;
                        for (char ch : alphabet) {
                            st.put("" + ch, code++);
                        }

                        W = minW;
                        L = 1 << W;

                        st.put(w + c, code++);
                    }
                }

                w = "" + c;
            }

            i++;
        }

        if (!w.equals("")) {
            BinaryStdOut.write(st.get(w), W);
        }

        BinaryStdOut.flush();
    }

    // ================= EXPAND =================
    private static void expand() {

        int minW = BinaryStdIn.readInt();
        int maxW = BinaryStdIn.readInt();
        int policyFlag = BinaryStdIn.readInt();
        boolean reset = (policyFlag == 1);

        int alphaSize = BinaryStdIn.readInt();

        List<String> alphabet = new ArrayList<>();

        for (int i = 0; i < alphaSize; i++) {
            alphabet.add("" + BinaryStdIn.readChar());
        }

        List<String> st = new ArrayList<>();

        for (String s : alphabet) {
            st.add(s);
        }

        int W = minW;
        int L = 1 << W;

        int code = st.size();

        int prevCode = BinaryStdIn.readInt(W);
        String val = st.get(prevCode);

        BinaryStdOut.write(val);

        while (!BinaryStdIn.isEmpty()) {

            int currCode = BinaryStdIn.readInt(W);

            String s;

            if (currCode < st.size()) {
                s = st.get(currCode);
            } else {
                s = val + val.charAt(0);
            }

            BinaryStdOut.write(s);

            if (code < L) {
                st.add(val + s.charAt(0));
                code++;
            } else {

                if (W < maxW) {
                    W++;
                    L = 1 << W;
                    st.add(val + s.charAt(0));
                    code++;
                } else if (reset) {

                    st.clear();
                    st.addAll(alphabet);

                    W = minW;
                    L = 1 << W;
                    code = st.size();

                    st.add(val + s.charAt(0));
                    code++;
                }
            }

            val = s;
        }

        BinaryStdOut.flush();
    }

    // ================= READ ALPHABET =================
    private static List<Character> readAlphabet(String path) throws Exception {

        List<Character> list = new ArrayList<>();
        Set<Character> seen = new HashSet<>();

        BufferedReader br = new BufferedReader(new FileReader(path));

        String line;
        while ((line = br.readLine()) != null) {
            if (line.length() > 0) {
                char c = line.charAt(0);
                if (!seen.contains(c)) {
                    seen.add(c);
                    list.add(c);
                }
            }
        }

        // add newline chars
        list.add('\n');
        list.add('\r');

        return list;
    }
}