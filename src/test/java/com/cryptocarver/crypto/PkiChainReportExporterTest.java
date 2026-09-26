package com.cryptocarver.crypto;

import com.cryptocarver.CryptoCarverCli;
import com.cryptocarver.service.PkiChainReportExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PkiChainReportExporterTest {
    @TempDir Path temp;

    @Test void reportsTrustRevocationAndStableCertificateDetailsOffline() throws Exception {
        try(LocalPkiFixture pki=new LocalPkiFixture(temp.resolve("fixture"))) {
            KeyStore trust=KeyStore.getInstance("PKCS12"); trust.load(null,LocalPkiFixture.PASSWORD); trust.setCertificateEntry("root",pki.root);
            var chain=List.of(pki.signer,pki.intermediate,pki.root);
            String good=PkiChainReportExporter.export(chain,trust,List.of(pki.goodCrl,pki.rootCrl),List.of(),false);
            String goodAgain=PkiChainReportExporter.export(chain,trust,List.of(pki.goodCrl,pki.rootCrl),List.of(),false);
            assertEquals(withoutHeaderDate(good),withoutHeaderDate(goodAgain));
            assertTrue(good.contains("Anchored in truststore: **yes**"));
            assertTrue(good.contains("**GOOD** (source: local CRL)"));
            assertTrue(good.contains("SHA-256:"));
            String revoked=PkiChainReportExporter.export(chain,trust,List.of(pki.revokedCrl,pki.rootCrl),List.of(),false);
            assertTrue(revoked.contains("**REVOKED** (source: local CRL)"));
            assertTrue(PkiChainReportExporter.export(chain,null,List.of(),List.of(),false).contains("Anchored in truststore: **no**"));
            String expired=PkiChainReportExporter.export(List.of(pki.expiredCertificate(),pki.intermediate,pki.root),trust,List.of(),List.of(),false);
            assertTrue(expired.contains("Certificate has expired."));
            pki.assertNoRevocationDownloads();
        }
    }

    @Test void chainReportCliWritesMarkdownToStdout() throws Exception {
        try(LocalPkiFixture pki=new LocalPkiFixture(temp.resolve("cli-fixture"))) {
            Path pem=temp.resolve("chain.pem");
            StringBuilder value=new StringBuilder();
            for(X509Certificate c:List.of(pki.signer,pki.intermediate,pki.root)) value.append("-----BEGIN CERTIFICATE-----\n")
                    .append(java.util.Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(c.getEncoded())).append("\n-----END CERTIFICATE-----\n");
            Files.writeString(pem,value);
            StringWriter stdout=new StringWriter(),stderr=new StringWriter();
            int code=CryptoCarverCli.run(new String[]{"chain-report",pem.toString()},new PrintWriter(stdout,true),new PrintWriter(stderr,true));
            assertEquals(CryptoCarverCli.EXIT_SUCCESS,code,stderr.toString());
            assertTrue(stdout.toString().contains("# PKI chain report"));
            stdout.getBuffer().setLength(0);
            code=CryptoCarverCli.run(new String[]{"chain-report",pem.toString(),"--crl",pki.goodCrlFile.toString(),
                    "--crl",pki.rootCrlFile.toString()},new PrintWriter(stdout,true),new PrintWriter(stderr,true));
            assertEquals(CryptoCarverCli.EXIT_SUCCESS,code,stderr.toString());
            assertTrue(stdout.toString().contains("**GOOD** (source: local CRL)"));
            assertEquals(CryptoCarverCli.EXIT_INVALID_ARGS,CryptoCarverCli.run(
                    new String[]{"chain-report",pem.toString(),"--password","secret"},new PrintWriter(new StringWriter()),new PrintWriter(new StringWriter())));
            pki.assertNoRevocationDownloads();
        }
    }

    private static String withoutHeaderDate(String report) { return report.replaceFirst("Generated: .*\\R", "Generated: <ignored>\n"); }
}
