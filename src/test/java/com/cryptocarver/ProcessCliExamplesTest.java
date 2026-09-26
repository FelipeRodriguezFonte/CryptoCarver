package com.cryptocarver;

import com.cryptocarver.model.process.SecretOutputPolicy;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcessCliExamplesTest {
    private static final String ROOT = "docs/examples/processes/";

    private static String run(String file, String... sets) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add("run-process"); args.add(ROOT + file + ".json"); args.add("--reveal-secrets");
        for (String set : sets) { args.add("--set"); args.add(set); }
        StringWriter output = new StringWriter(); StringWriter error = new StringWriter();
        int code = CryptoCarverCli.run(args.toArray(String[]::new), new PrintWriter(output), new PrintWriter(error));
        assertEquals(0, code, error.toString());
        return output.toString();
    }

    @Test void mastercardDataStorageExampleMatchesKnownVector() {
        String result = run("mc-data-storage", "summary.un=11223344");
        assertTrue(result.contains("partial.output: 66887C5600B47C5600B40CC2"), result);
        assertTrue(result.contains("digest.output: 659C8EBFAA816DB5"), result);
        assertTrue(result.contains("summary.output: FC96571A6E95FFA4"), result);
    }

    @Test void visaHceExampleMatchesKnownVector() {
        String result = run("visa-hce", "luk.smUdk=94E3194C02105E3B153438D562D5A49D", "msd.atc=0001");
        assertTrue(result.contains("luk.output: D144CA8CBB4BD463C8EDD5761BF1770E"), result);
        assertTrue(result.contains("msd.output: 634"), result);
        assertTrue(result.contains("qvsdc.output: 42A0254F47679C5A"), result);
    }

    @Test void secureMessagingExampleMatchesKnownVector() {
        String result = run("emv-secure-messaging", "card.smMk=862F13DF807A13B9D9AEAEC885FE7CA4", "session.smAc=51DB71A5DCC47F8A", "mac.smAc=51DB71A5DCC47F8A", "mac.atc=0010");
        assertTrue(result.contains("card.output: AEB0F198A498E067C4E63D94A770A80E"), result);
        assertTrue(result.contains("session.output: E46C87DD5AC1177FCCE8F7A1C56A40C6"), result);
        assertTrue(result.contains("mac.output: AC4E7EB35196E310"), result);
    }

    @Test void cliMasksSecretOutputsUnlessRevealRequested() {
        StringWriter masked = new StringWriter(); StringWriter error = new StringWriter();
        assertEquals(0, CryptoCarverCli.run(new String[] {"run-process", ROOT + "emv-secure-messaging.json",
                "--set", "card.smMk=862F13DF807A13B9D9AEAEC885FE7CA4", "--set", "session.smAc=51DB71A5DCC47F8A", "--set", "mac.smAc=51DB71A5DCC47F8A", "--set", "mac.atc=0010", "--json"},
                new PrintWriter(masked), new PrintWriter(error)), error.toString());
        assertTrue(masked.toString().contains("•••• (length 32)"), masked.toString());
        assertFalse(masked.toString().contains("AEB0F198A498E067C4E63D94A770A80E"));
        assertTrue(masked.toString().contains("AC4E7EB35196E310"));
        assertTrue(run("emv-secure-messaging", "card.smMk=862F13DF807A13B9D9AEAEC885FE7CA4", "session.smAc=51DB71A5DCC47F8A", "mac.smAc=51DB71A5DCC47F8A", "mac.atc=0010")
                .contains("AEB0F198A498E067C4E63D94A770A80E"));
    }

    @Test void secretPolicyKeepsDesignerClassification() {
        assertTrue(SecretOutputPolicy.isSecretMaterialOutput("AES_KEY_GENERATE"));
        assertTrue(SecretOutputPolicy.isSecretMaterialOutput("VISA_HCE_LUK"));
        assertTrue(SecretOutputPolicy.isSecretMaterialOutput("EMV_SM_CARD_KEY"));
        assertFalse(SecretOutputPolicy.isSecretMaterialOutput("VISA_HCE_MSD"));
        assertFalse(SecretOutputPolicy.isSecretMaterialOutput("EMV_SM_MAC"));
    }

    @Test void validationErrorIdentifiesNodePortAndCause() {
        StringWriter error = new StringWriter();
        assertEquals(CryptoCarverCli.EXIT_OPERATION_FAILED,
                CryptoCarverCli.run(new String[] {"run-process", ROOT + "emv-secure-messaging.json"},
                        new PrintWriter(new StringWriter()), new PrintWriter(error)));
        assertTrue(error.toString().contains("node card, port smMk"), error.toString());
        assertTrue(error.toString().contains("Missing required payment input"), error.toString());
    }

    @Test void fileOutputCannotWriteEvenToANewPath() throws Exception {
        var definition = new com.cryptocarver.model.process.ProcessDefinition();
        var node = new com.cryptocarver.model.process.ProcessDefinition.Node("write", "FILE_OUTPUT", "write", 0, 0);
        Path target = Files.createTempDirectory("process-cli-test-").resolve("result.txt");
        node.configuration.put("filePath", target.toString());
        definition.nodes.add(node);
        Path process = Files.createTempFile("process-cli-", ".json");
        try {
            Files.writeString(process, com.cryptocarver.model.process.ProcessDefinitionCodec.serialize(definition));
            StringWriter error = new StringWriter();
            assertEquals(CryptoCarverCli.EXIT_OPERATION_FAILED,
                    CryptoCarverCli.run(new String[] {"run-process", process.toString()},
                            new PrintWriter(new StringWriter()), new PrintWriter(error)));
            assertTrue(error.toString().contains("node write, port output"), error.toString());
            assertFalse(Files.exists(target));
        } finally {
            Files.deleteIfExists(process);
            Files.deleteIfExists(target.getParent());
        }
    }
}
