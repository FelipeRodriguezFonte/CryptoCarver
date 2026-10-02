package com.cryptocarver.crypto;

/** Public TR-31:2018 Annex A vectors already exercised by TR31OperationsTest. */
public final class Tr31TestVectors {
    public static final String[] protectionKeys = {"89E88CF7931444F334BD7547FC3F380C", "DD7515F2BFC17F85CE48F3CA25CB21F6",
                "B8ED59E0A279A295E9F5ED7944FD06B9", "88E1AB2A2E3DD38C1FA039A536500CC8A87AB9D62DC92C01058FA79F44657DE6"};
    public static final String[] blocks = {
                "A0072P0TE00E0000F5161ED902807AF26F1D62263644BD24192FDB3193C730301CEE8701",
                "B0080P0TE00E000094B420079CC80BA3461F86FE26EFC4A3B8E4FA4C5F5341176EED7B727B8A248E",
                "C0096B0TX12S0100KS1800604B120F9292800000BFB9B689CB567E66FC3FEE5AD5F52161FC6545B9D60989015D02155C",
                "D0112P0AE00E0000B82679114F470F540165EDFBF7E250FCEA43F810D215F8D207E2E417C07156A27E8E31DA05F7425509593D03A457DC34"};
    public static final String[] keys = {"F039121BEC83D26B169BDCD5B22AAF8F", "3F419E1CB7079442AA37474C2EFBF8B8",
                "EDB380DD340BC2620247D445F5B8D678", "3F419E1CB7079442AA37474C2EFBF8B8"};
    public static final String[] usages = {"P0", "P0", "B0", "P0"};
    public static final char[] versions = {'A', 'B', 'C', 'D'};
    public static final char[] algorithms = {'T', 'T', 'T', 'A'};
    public static final char[] modes = {'E', 'E', 'X', 'E'};
    public static final char[] exports = {'E', 'E', 'S', 'E'};
    public static final String[] options = {"", "", "0100KS1800604B120F9292800000", ""};

    private Tr31TestVectors() { }
}
