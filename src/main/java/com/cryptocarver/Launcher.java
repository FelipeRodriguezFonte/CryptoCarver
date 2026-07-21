package com.cryptocarver;

public class Launcher {
    public static void main(String[] args) {
        String[] cliArguments = cliArguments(args);
        if (cliArguments != null) {
            CryptoCarverCli.main(cliArguments);
            return;
        }
        CryptoCalculatorModern.main(args);
    }

    /** Allows a distributed JAR to answer version/help requests without starting JavaFX. */
    static String[] cliArguments(String[] args) {
        if (args == null || args.length == 0) return null;
        if ("--cli".equals(args[0])) return java.util.Arrays.copyOfRange(args, 1, args.length);
        if (args.length == 1 && ("--version".equals(args[0]) || "--help".equals(args[0]) || "help".equals(args[0]))) {
            return args.clone();
        }
        return null;
    }
}
