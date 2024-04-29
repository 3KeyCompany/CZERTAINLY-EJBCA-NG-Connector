package com.czertainly.ca.connector.ejbca.request;

import com.czertainly.api.model.core.enums.CertificateRequestFormat;
import com.czertainly.ca.connector.ejbca.util.CertificateRequestUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.crmf.CRMFException;
import org.bouncycastle.cert.crmf.jcajce.JcaCertificateRequestMessageBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.RSAKeyGenParameterSpec;

@SpringBootTest
public class CrmfCertificateRequestTest {

    @BeforeEach
    public void setUp() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testRequestWithPOPSig() throws GeneralSecurityException, CRMFException, IOException, OperatorCreationException {
        KeyPair keyPair = generateRSAKeyPair();

        byte[] request = generateRequestWithPOPSig(
                BigInteger.valueOf(1),
                keyPair,
                "SHA256withRSA");

        CertificateRequest certificateRequest = CertificateRequestUtils.createCertificateRequest(request, CertificateRequestFormat.CRMF);

        Assertions.assertInstanceOf(CrmfCertificateRequest.class, certificateRequest);
        Assertions.assertEquals(new X500Name("CN=Example"), certificateRequest.getSubject());

    }

    public static KeyPair generateRSAKeyPair()
            throws GeneralSecurityException
    {
        KeyPairGenerator  kpGen = KeyPairGenerator.getInstance("RSA", "BC");
        kpGen.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
        return kpGen.generateKeyPair();
    }

    private static byte[] generateRequestWithPOPSig(
            BigInteger certReqID, KeyPair kp, String sigAlg) throws OperatorCreationException, CRMFException, IOException {
        X500Name subject = new X500Name("CN=Example");

        JcaCertificateRequestMessageBuilder certReqBuild
                = new JcaCertificateRequestMessageBuilder(certReqID);

        certReqBuild
                .setPublicKey(kp.getPublic())
                .setSubject(subject)
                .setProofOfPossessionSigningKeySigner(
                        new JcaContentSignerBuilder(sigAlg)
                                .setProvider("BC")
                                .build(kp.getPrivate()));

        return certReqBuild.build().getEncoded();
    }
}
