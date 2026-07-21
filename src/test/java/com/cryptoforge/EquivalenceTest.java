package com.cryptoforge;

import com.cryptoforge.model.SafeTransformations;
import com.cryptoforge.model.batch.BatchInputCodec;
import com.cryptoforge.model.batch.BatchOutputCodec;
import com.cryptoforge.model.batch.BatchRunner;
import com.cryptoforge.ui.ModernMainController;
import org.junit.jupiter.api.Test;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EquivalenceTest {

    @Test
    void testEquivalenceAcrossSurfaces() throws Exception {
        String input = "Hello CryptoCarver!";
        
        // 1. Core Model
        String modelOutput = SafeTransformations.encodeBase64Url(input);
        
        // 2. CLI
        StringWriter cliOut = new StringWriter();
        StringWriter cliErr = new StringWriter();
        int code = CryptoCarverCli.run(new String[]{"base64url-encode", input}, new PrintWriter(cliOut, true), new PrintWriter(cliErr, true));
        assertEquals(CryptoCarverCli.EXIT_SUCCESS, code);
        assertEquals(modelOutput, cliOut.toString().trim());
        
        // 3. Batch
        BatchRunner.Report report = BatchRunner.run(List.of(Map.of("data", input)), 
            row -> Map.of("result", SafeTransformations.encodeBase64Url(row.get("data"))), () -> false);
        assertEquals(1, report.results().size());
        assertEquals(modelOutput, report.results().get(0).output().get("result"));
    }
}
