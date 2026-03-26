import java.util.*;
import java.io.*;

public class LZWTool {

    public static void main(String[] args) {

        String mode = null;
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

        if (mode.equals("compress")) {
            compress(minW, maxW, policy, alphabetPath);
        } else {
            expand();
        }
    }

    // ================= LOAD ALPHABET =================
    private static ArrayList<String> loadAlphabet(String path) {
        ArrayList<String> alphabet = new ArrayList<>();
        try {
            Scanner sc = new Scanner(new File(path));
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (!line.isEmpty() && !alphabet.contains(line)) {
                    alphabet.add(line);
                }
            }
            sc.close();
        } catch (Exception e) {
            System.err.println("Error reading alphabet");
            System.exit(1);
        }

        alphabet.add("" + (char)10);
        alphabet.add("" + (char)13);

        return alphabet;
    }

    // ================= COMPRESS =================
    private static void compress(int minW, int maxW, String policy, String alphabetPath) {

        ArrayList<String> alphabet = loadAlphabet(alphabetPath);

        // HEADER (fixed)
        BinaryStdOut.write(minW, 8);
        BinaryStdOut.write(maxW, 8);
        BinaryStdOut.write(policy.equals("reset") ? 1 : 0, 1);
        BinaryStdOut.write(alphabet.size(), 16);
        for (String s : alphabet) {
            BinaryStdOut.write(s.charAt(0), 8);
        }

        TSTmod<Integer> st = new TSTmod<>();

        int R = alphabet.size(); // EOF
        int code = 0;

        for (String s : alphabet) {
            st.put(new StringBuilder(s), code++);
        }

        code = R + 1;

        int W = minW;
        int L = 1 << W;

        String input = BinaryStdIn.readString();

        int i = 0;

        while (i < input.length()) {

            StringBuilder s = new StringBuilder("" + input.charAt(i));
            int j = i + 1;

            while (j <= input.length()) {
                StringBuilder temp = new StringBuilder(s);
                if (j < input.length()) temp.append(input.charAt(j));

                if (j < input.length() && st.contains(temp)) {
                    s = temp;
                    j++;
                } else break;
            }

            BinaryStdOut.write(st.get(s), W);

            if (j < input.length()) {
                StringBuilder newEntry = new StringBuilder(s);
                newEntry.append(input.charAt(j));

                if (code < (1 << maxW)) {

                    if (code == L && W < maxW) {
                        W++;
                        L = 1 << W;
                    }

                    if (code < L) {
                        st.put(newEntry, code++);
                    }

                } else if (policy.equals("reset")) {

                    st = new TSTmod<>();
                    code = 0;
                    for (String str : alphabet) {
                        st.put(new StringBuilder(str), code++);
                    }

                    code = R + 1;
                    W = minW;
                    L = 1 << W;
                }
            }

            i += s.length();
        }

        // WRITE EOF
        BinaryStdOut.write(R, W);

        BinaryStdOut.close();
    }

    // ================= EXPAND =================
    private static void expand() {

        int minW = BinaryStdIn.readInt(8);
        int maxW = BinaryStdIn.readInt(8);
        int policyFlag = BinaryStdIn.readInt(1);
        String policy = (policyFlag == 1) ? "reset" : "freeze";

        int alphaSize = BinaryStdIn.readInt(16);

        ArrayList<String> alphabet = new ArrayList<>();
        for (int i = 0; i < alphaSize; i++) {
            alphabet.add("" + BinaryStdIn.readChar(8));
        }

        int R = alphabet.size();

        ArrayList<String> st = new ArrayList<>();
        for (String s : alphabet) {
            st.add(s);
        }

        int code = R + 1;

        int W = minW;
        int L = 1 << W;

        int prevCode = BinaryStdIn.readInt(W);
        if (prevCode == R) return;

        String val = st.get(prevCode);
        BinaryStdOut.write(val);

        while (true) {

            int currCode = BinaryStdIn.readInt(W);

            if (currCode == R) break;

            String s;
            if (currCode < st.size()) {
                s = st.get(currCode);
            } else {
                s = val + val.charAt(0);
            }

            BinaryStdOut.write(s);

            if (code < (1 << maxW)) {

                if (code == L && W < maxW) {
                    W++;
                    L = 1 << W;
                }

                if (code < L) {
                    st.add(val + s.charAt(0));
                    code++;
                }

            } else if (policy.equals("reset")) {

                st = new ArrayList<>(alphabet);
                code = R + 1;
                W = minW;
                L = 1 << W;
            }

            val = s;
        }

        BinaryStdOut.close();
    }
}