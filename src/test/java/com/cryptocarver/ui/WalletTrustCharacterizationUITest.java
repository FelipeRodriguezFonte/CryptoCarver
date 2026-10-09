package com.cryptocarver.ui;

import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.crypto.EidasCertificateInspector;
import com.cryptocarver.crypto.TrustedEntityListJsonInspector;
import com.cryptocarver.crypto.TrustedListInspector;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class WalletTrustCharacterizationUITest extends WalletRemainingCharacterizationSupport {
    private static final String GOLD = "a8e749cfa04a0372d6b8af2762cddac2c56bc7b805a30fb7da787937e049fc07";

    @Test void inspectsCertificatesAndLocalTrustedLists() throws Exception {
        X509Certificate service = certificate("Invented Service CA 80");
        X509Certificate stranger = certificate("Invented Stranger 80");
        String servicePem = pem("CERTIFICATE", service.getEncoded());
        String xml = trustedList(service);
        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
        String entityList = WalletRemainingPrivacyAuditUITest.TRUSTED_ENTITY_LIST;

        withWallet(() -> {
            List<String> transcript = new ArrayList<>();
            forEachLanguageAndProfile(transcript, lines -> {
                var locale = I18nService.getInstance().getLocale();
                shell.navigateTo("eIDAS Certificate Profiles");
                replaceReporter();
                put("eidasCertArea", "");
                lines.add(observe("handleEidasCertInspect", "eidasCertOutputArea"));
                put("eidasCertArea", servicePem);
                lines.add(observe("handleEidasCertInspect", "eidasCertOutputArea"));
                assertEquals(EidasCertificateInspector.describe(service, locale), text("eidasCertOutputArea"));
                put("eidasCertArea", "not a certificate");
                lines.add(observe("handleEidasCertInspect", "eidasCertOutputArea"));

                shell.navigateTo("Trusted List");
                replaceReporter();
                put("trustedListXmlArea", "");
                lines.add(observe("handleTrustedListInspect", "trustedListOutputArea"));
                lines.add(observe("handleTrustedListVerify", "trustedListOutputArea"));
                lines.add(observe("handleTrustedListFind", "trustedListOutputArea"));
                put("trustedListXmlArea", xml);
                lines.add(observe("handleTrustedListInspect", "trustedListOutputArea"));
                assertEquals(TrustedListInspector.describe(xmlBytes, locale), text("trustedListOutputArea"));
                lines.add(observe("handleTrustedListVerify", "trustedListOutputArea"));
                put("trustedListCertArea", "");
                lines.add(observe("handleTrustedListFind", "trustedListOutputArea"));
                put("trustedListCertArea", servicePem);
                lines.add(observe("handleTrustedListFind", "trustedListOutputArea"));
                String found = text("trustedListOutputArea");
                lines.add("find|provider=" + found.startsWith("TSP de Pruebas / Servicio de certificados cualificados")
                        + "|status-line=" + found.contains("\n  status   : ")
                        + "|matched-line=" + found.contains("\n  matched  : ")
                        + "|qualifiers=" + (found.split("\n  qualifier: ", -1).length - 1));
                put("trustedListCertArea", pem("CERTIFICATE", stranger.getEncoded()));
                lines.add(observe("handleTrustedListFind", "trustedListOutputArea"));
                lines.add("find-none|" + text("trustedListOutputArea"));
                put("trustedListXmlArea", "<not-a-trusted-list/>");
                lines.add(observe("handleTrustedListInspect", "trustedListOutputArea"));

                shell.navigateTo("Trusted Entity List JSON");
                replaceReporter();
                put("trustedEntityListJsonArea", "");
                lines.add(observe("handleTrustedEntityListJsonInspect", "trustedListOutputArea"));
                put("trustedEntityListJsonArea", entityList);
                put("trustedEntityListSignerCertArea", "");
                put("trustedEntityListSearchCertArea", "");
                lines.add(observe("handleTrustedEntityListJsonInspect", "trustedListOutputArea"));
                assertEquals(TrustedEntityListJsonInspector.describe(entityList.getBytes(StandardCharsets.UTF_8),
                        locale, null, null), text("trustedListOutputArea"));
                assertTrue(text("trustedListOutputArea").contains("Wallet service"));
                put("trustedEntityListJsonArea", "{\"LoTE\":{}}");
                lines.add(observe("handleTrustedEntityListJsonInspect", "trustedListOutputArea"));
            });
            pinTranscript("wallet-4", GOLD, transcript);
        });
    }

    private static X509Certificate certificate(String commonName) throws Exception {
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = commonName;
        return CertificateGenerator.generateSelfSignedCertificate(
                KeyPairGenerator.getInstance("RSA").generateKeyPair(), config);
    }

    /** Unsigned TS 119 612 list with one granted qualified service; same shape as the crypto-level fixture. */
    private static String trustedList(X509Certificate serviceCertificate) throws Exception {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#" Id="TSL-lab" TSLTag="http://uri.etsi.org/19612/TSLTag">
                  <SchemeInformation>
                    <TSLVersionIdentifier>5</TSLVersionIdentifier>
                    <TSLSequenceNumber>42</TSLSequenceNumber>
                    <TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric</TSLType>
                    <SchemeOperatorName><Name xml:lang="en">Laboratorio de Pruebas</Name></SchemeOperatorName>
                    <SchemeName><Name xml:lang="en">Lista de pruebas</Name></SchemeName>
                    <StatusDeterminationApproach>http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUappropriate</StatusDeterminationApproach>
                    <SchemeTerritory>ES</SchemeTerritory>
                    <ListIssueDateTime>2026-09-01T00:00:00Z</ListIssueDateTime>
                    <NextUpdate><dateTime>2027-03-01T00:00:00Z</dateTime></NextUpdate>
                  </SchemeInformation>
                  <TrustServiceProviderList>
                    <TrustServiceProvider>
                      <TSPInformation>
                        <TSPName><Name xml:lang="en">TSP de Pruebas</Name></TSPName>
                      </TSPInformation>
                      <TSPServices>
                        <TSPService>
                          <ServiceInformation>
                            <ServiceTypeIdentifier>http://uri.etsi.org/TrstSvc/Svctype/CA/QC</ServiceTypeIdentifier>
                            <ServiceName><Name xml:lang="en">Servicio de certificados cualificados</Name></ServiceName>
                            <ServiceDigitalIdentity>
                              <DigitalId><X509Certificate>%s</X509Certificate></DigitalId>
                            </ServiceDigitalIdentity>
                            <ServiceStatus>http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted</ServiceStatus>
                            <StatusStartingTime>2024-05-20T00:00:00Z</StatusStartingTime>
                            <ServiceInformationExtensions><Extension Critical="true">
                              <Qualifications xmlns="http://uri.etsi.org/TrstSvc/SvcInfoExt/eSigDir-1999-93-EC-TrustedList/#">
                                <QualificationElement><Qualifiers>
                                  <Qualifier uri="http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCForESig"/>
                                  <Qualifier uri="http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCWithQSCD"/>
                                </Qualifiers><CriteriaList assert="all"/></QualificationElement>
                              </Qualifications>
                            </Extension></ServiceInformationExtensions>
                          </ServiceInformation>
                        </TSPService>
                      </TSPServices>
                    </TrustServiceProvider>
                  </TrustServiceProviderList>
                </TrustServiceStatusList>
                """.formatted(Base64.getEncoder().encodeToString(serviceCertificate.getEncoded()));
    }
}
