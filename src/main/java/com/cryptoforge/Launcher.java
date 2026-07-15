package com.cryptoforge;

public class Launcher {
    public static void main(String[] args) {
        if (args.length > 0 && "--cli".equals(args[0])) {
            CryptoCarverCli.main(java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        CryptoCalculatorModern.main(args);
    }
}
