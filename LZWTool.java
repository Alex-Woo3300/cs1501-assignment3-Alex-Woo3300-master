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

        // write header
        BinaryStdOut.write(minW);
        BinaryStdOut.write(maxW);
        BinaryStdOut.write(policy.equals("reset") ? 1 : 0);
        BinaryStdOut.write(alphabet.size());
        for (String s : alphabet) {
            BinaryStdOut.write(s.charAt(0));
        }

        TSTmod<Integer> st = new TSTmod<>();

        int code = 0;
        for (String s : alphabet) {
            st.put(new StringBuilder(s), code++);
        }

        int W = minW;
        int L = 1 << W;

        StringBuilder input = new StringBuilder();
        while (!BinaryStdIn.isEmpty()) {
            input.append(BinaryStdIn.readChar());
        }

        int i = 0;
        while (i < input.length()) {

            StringBuilder current = new StringBuilder("" + input.charAt(i));
            int j = i + 1;

            while (j <= input.length()) {
                StringBuilder temp = new StringBuilder(current);
                if (j < input.length()) temp.append(input.charAt(j));

                if (j < input.length() && st.contains(temp)) {
                    current = temp;
                    j++;
                } else break;
            }

            BinaryStdOut.write(st.get(current), W);

            if (j < input.length()) {
                StringBuilder newEntry = new StringBuilder(current);
                newEntry.append(input.charAt(j));

                if (code < (1 << maxW)) {
                    if (code == L && W < maxW) {
                        W++;
                        L = 1 << W;
                    }

                    if (code < L) {
                        st.put(newEntry, code++);
                    }
                } else {
                    if (policy.equals("reset")) {
                        st = new TSTmod<>();
                        code = 0;
                        for (String s : alphabet) {
                            st.put(new StringBuilder(s), code++);
                        }
                        W = minW;
                        L = 1 << W;
                    }
                }
            }

            i += current.length();
        }

        BinaryStdOut.close();
    }

    // ================= EXPAND =================
    private static void expand() {

        int minW = BinaryStdIn.readInt();
        int maxW = BinaryStdIn.readInt();
        int policyFlag = BinaryStdIn.readInt();
        String policy = (policyFlag == 1) ? "reset" : "freeze";

        int alphaSize = BinaryStdIn.readInt();
        ArrayList<String> alphabet = new ArrayList<>();

        for (int i = 0; i < alphaSize; i++) {
            alphabet.add("" + BinaryStdIn.readChar());
        }

        ArrayList<String> st = new ArrayList<>();

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

            if (code < (1 << maxW)) {

                if (code == L && W < maxW) {
                    W++;
                    L = 1 << W;
                }

                if (code < L) {
                    st.add(val + s.charAt(0));
                    code++;
                }

            } else {
                if (policy.equals("reset")) {
                    st = new ArrayList<>(alphabet);
                    code = st.size();
                    W = minW;
                    L = 1 << W;
                }
            }

            val = s;
        }

        BinaryStdOut.close();
    }
}