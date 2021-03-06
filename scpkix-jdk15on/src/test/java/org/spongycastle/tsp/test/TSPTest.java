package org.spongycastle.tsp.test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.X509Certificate;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import junit.framework.TestCase;
import org.spongycastle.asn1.cmp.PKIFailureInfo;
import org.spongycastle.asn1.cms.AttributeTable;
import org.spongycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.spongycastle.tsp.GenTimeAccuracy;
import org.spongycastle.tsp.TSPAlgorithms;
import org.spongycastle.tsp.TSPValidationException;
import org.spongycastle.tsp.TimeStampRequest;
import org.spongycastle.tsp.TimeStampRequestGenerator;
import org.spongycastle.tsp.TimeStampResponse;
import org.spongycastle.tsp.TimeStampResponseGenerator;
import org.spongycastle.tsp.TimeStampToken;
import org.spongycastle.tsp.TimeStampTokenGenerator;
import org.spongycastle.tsp.TimeStampTokenInfo;
import org.spongycastle.util.Arrays;

public class TSPTest
    extends TestCase
{
    public void testGeneral()
        throws Exception
    {
            Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
            String signDN = "O=Bouncy Castle, C=AU";
            KeyPair signKP = TSPTestUtil.makeKeyPair();
            X509Certificate signCert = TSPTestUtil.makeCACertificate(signKP,
                    signDN, signKP, signDN);

            String origDN = "CN=Eric H. Echidna, E=eric@spongycastle.org, O=Bouncy Castle, C=AU";
            KeyPair origKP = TSPTestUtil.makeKeyPair();
            X509Certificate origCert = TSPTestUtil.makeCertificate(origKP,
                    origDN, signKP, signDN);


            
            List certList = new ArrayList();
            certList.add(origCert);
            certList.add(signCert);

            CertStore certs = CertStore.getInstance("Collection",
                    new CollectionCertStoreParameters(certList), "SC");
            
            basicTest(origKP.getPrivate(), origCert, certs);     
            responseValidationTest(origKP.getPrivate(), origCert, certs);
            incorrectHashTest(origKP.getPrivate(), origCert, certs);
            badAlgorithmTest(origKP.getPrivate(), origCert, certs);
            timeNotAvailableTest(origKP.getPrivate(), origCert, certs);
            badPolicyTest(origKP.getPrivate(), origCert, certs);
            tokenEncodingTest(origKP.getPrivate(), origCert, certs);
            certReqTest(origKP.getPrivate(), origCert, certs);
            testAccuracyZeroCerts(origKP.getPrivate(), origCert, certs);
            testAccuracyWithCertsAndOrdering(origKP.getPrivate(), origCert, certs);
            testNoNonse(origKP.getPrivate(), origCert, certs);
    }
    
    private void basicTest(
        PrivateKey      privateKey,
        X509Certificate cert,
        CertStore       certs)
        throws Exception
    {
        TimeStampTokenGenerator tsTokenGen = new TimeStampTokenGenerator(
                privateKey, cert, TSPAlgorithms.SHA1, "1.2");
        
        tsTokenGen.setCertificatesAndCRLs(certs);

        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        TimeStampRequest          request = reqGen.generate(TSPAlgorithms.SHA1, new byte[20], BigInteger.valueOf(100));

        TimeStampResponseGenerator tsRespGen = new TimeStampResponseGenerator(tsTokenGen, TSPAlgorithms.ALLOWED);

        TimeStampResponse tsResp = tsRespGen.generate(request, new BigInteger("23"), new Date(), "SC");

        tsResp = new TimeStampResponse(tsResp.getEncoded());

        TimeStampToken  tsToken = tsResp.getTimeStampToken();

        tsToken.validate(cert, "SC");

        AttributeTable  table = tsToken.getSignedAttributes();

        assertNotNull("no signingCertificate attribute found", table.get(PKCSObjectIdentifiers.id_aa_signingCertificate));
    }
    
    private void responseValidationTest(
        PrivateKey      privateKey,
        X509Certificate cert,
        CertStore       certs)
        throws Exception
    {
        TimeStampTokenGenerator tsTokenGen = new TimeStampTokenGenerator(
                privateKey, cert, TSPAlgorithms.MD5, "1.2");
        
        tsTokenGen.setCertificatesAndCRLs(certs);

        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        TimeStampRequest          request = reqGen.generate(TSPAlgorithms.SHA1, new byte[20], BigInteger.valueOf(100));

        TimeStampResponseGenerator tsRespGen = new TimeStampResponseGenerator(tsTokenGen, TSPAlgorithms.ALLOWED);

        TimeStampResponse tsResp = tsRespGen.generate(request, new BigInteger("23"), new Date(), "SC");

        tsResp = new TimeStampResponse(tsResp.getEncoded());

        TimeStampToken  tsToken = tsResp.getTimeStampToken();

        tsToken.validate(cert, "SC");
        
        //
        // check validation
        //
        tsResp.validate(request);
        
        try
        {
            request = reqGen.generate(TSPAlgorithms.SHA1, new byte[20], BigInteger.valueOf(101));
            
            tsResp.validate(request);
            
            fail("response validation failed on invalid nonce.");
        }
        catch (TSPValidationException e)
        {
            // ignore
        }

        try
        {
            request = reqGen.generate(TSPAlgorithms.SHA1, new byte[22], BigInteger.valueOf(100));
            
            tsResp.validate(request);
            
            fail("response validation failed on wrong digest.");
        }
        catch (TSPValidationException e)
        {
            // ignore
        }
        
        try
        {
            request = reqGen.generate(TSPAlgorithms.MD5, new byte[20], BigInteger.valueOf(100));
            
            tsResp.validate(request);
            
            fail("response validation failed on wrong digest.");
        }
        catch (TSPValidationException e)
        {
            // ignore
        }
    }
    
    private void incorrectHashTest(
        PrivateKey      privateKey,
        X509Certificate cert,
        CertStore       certs)
        throws Exception
    {
        TimeStampTokenGenerator tsTokenGen = new TimeStampTokenGenerator(
                privateKey, cert, TSPAlgorithms.SHA1, "1.2");
        
        tsTokenGen.setCertificatesAndCRLs(certs);

        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        TimeStampRequest            request = reqGen.generate(TSPAlgorithms.SHA1, new byte[16]);

        TimeStampResponseGenerator tsRespGen = new TimeStampResponseGenerator(tsTokenGen, TSPAlgorithms.ALLOWED);

        TimeStampResponse tsResp = tsRespGen.generate(request, new BigInteger("23"), new Date(), "SC");

        tsResp = new TimeStampResponse(tsResp.getEncoded());

        TimeStampToken  tsToken = tsResp.getTimeStampToken();

        if (tsToken != null)
        {
            fail("incorrectHash - token not null.");
        }
        
        PKIFailureInfo  failInfo = tsResp.getFailInfo();
        
        if (failInfo == null)
        {
            fail("incorrectHash - failInfo set to null.");
        }
        
        if (failInfo.intValue() != PKIFailureInfo.badDataFormat)
        {
            fail("incorrectHash - wrong failure info returned.");
        }
    }
    
    private void badAlgorithmTest(
        PrivateKey      privateKey,
        X509Certificate cert,
        CertStore       certs)
        throws Exception
    {
        TimeStampTokenGenerator tsTokenGen = new TimeStampTokenGenerator(
                privateKey, cert, TSPAlgorithms.SHA1, "1.2");
        
        tsTokenGen.setCertificatesAndCRLs(certs);

        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        TimeStampRequest            request = reqGen.generate("1.2.3.4.5", new byte[20]);

        TimeStampResponseGenerator tsRespGen = new TimeStampResponseGenerator(tsTokenGen, TSPAlgorithms.ALLOWED);

        TimeStampResponse tsResp = tsRespGen.generate(request, new BigInteger("23"), new Date(), "SC");

        tsResp = new TimeStampResponse(tsResp.getEncoded());

        TimeStampToken  tsToken = tsResp.getTimeStampToken();

        if (tsToken != null)
        {
            fail("badAlgorithm - token not null.");
        }

        PKIFailureInfo  failInfo = tsResp.getFailInfo();
        
        if (failInfo == null)
        {
            fail("badAlgorithm - failInfo set to null.");
        }
        
        if (failInfo.intValue() != PKIFailureInfo.badAlg)
        {
            fail("badAlgorithm - wrong failure info returned.");
        }
    }

    private void timeNotAvailableTest(
        PrivateKey      privateKey,
        X509Certificate cert,
        CertStore       certs)
        throws Exception
    {
        TimeStampTokenGenerator tsTokenGen = new TimeStampTokenGenerator(
                privateKey, cert, TSPAlgorithms.SHA1, "1.2");

        tsTokenGen.setCertificatesAndCRLs(certs);

        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        TimeStampRequest            request = reqGen.generate("1.2.3.4.5", new byte[20]);

        TimeStampResponseGenerator tsRespGen = new TimeStampResponseGenerator(tsTokenGen, TSPAlgorithms.ALLOWED);

        TimeStampResponse tsResp = tsRespGen.generate(request, new BigInteger("23"), null, "SC");

        tsResp = new TimeStampResponse(tsResp.getEncoded());

        TimeStampToken  tsToken = tsResp.getTimeStampToken();

        if (tsToken != null)
        {
            fail("timeNotAvailable - token not null.");
        }

        PKIFailureInfo  failInfo = tsResp.getFailInfo();

        if (failInfo == null)
        {
            fail("timeNotAvailable - failInfo set to null.");
        }

        if (failInfo.intValue() != PKIFailureInfo.timeNotAvailable)
        {
            fail("timeNotAvailable - wrong failure info returned.");
        }
    }

    private void badPolicyTest(
        PrivateKey      privateKey,
        X509Certificate cert,
        CertStore       certs)
        throws Exception
    {
        TimeStampTokenGenerator tsTokenGen = new TimeStampTokenGenerator(
                privateKey, cert, TSPAlgorithms.SHA1, "1.2");
        
        tsTokenGen.setCertificatesAndCRLs(certs);

        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        
        reqGen.setReqPolicy("1.1");
        
        TimeStampRequest            request = reqGen.generate(TSPAlgorithms.SHA1, new byte[20]);

        TimeStampResponseGenerator tsRespGen = new TimeStampResponseGenerator(tsTokenGen, TSPAlgorithms.ALLOWED, new HashSet());

        TimeStampResponse tsResp = tsRespGen.generate(request, new BigInteger("23"), new Date(), "SC");

        tsResp = new TimeStampResponse(tsResp.getEncoded());

        TimeStampToken  tsToken = tsResp.getTimeStampToken();

        if (tsToken != null)
        {
            fail("badPolicy - token not null.");
        }

        PKIFailureInfo  failInfo = tsResp.getFailInfo();
        
        if (failInfo == null)
        {
            fail("badPolicy - failInfo set to null.");
        }
        
        if (failInfo.intValue() != PKIFailureInfo.unacceptedPolicy)
        {
            fail("badPolicy - wrong failure info returned.");
        }
    }
    
    private void certReqTest(
        PrivateKey      privateKey,
        X509Certificate cert,
        CertStore       certs)
        throws Exception
    {
        TimeStampTokenGenerator tsTokenGen = new TimeStampTokenGenerator(
                privateKey, cert, TSPAlgorithms.MD5, "1.2");
        
        tsTokenGen.setCertificatesAndCRLs(certs);
        
        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        
        //
        // request with certReq false
        //
        reqGen.setCertReq(false);
        
        TimeStampRequest          request = reqGen.generate(TSPAlgorithms.SHA1, new byte[20], BigInteger.valueOf(100));

        TimeStampResponseGenerator tsRespGen = new TimeStampResponseGenerator(tsTokenGen, TSPAlgorithms.ALLOWED);

        TimeStampResponse tsResp = tsRespGen.generate(request, new BigInteger("23"), new Date(), "SC");
        
        tsResp = new TimeStampResponse(tsResp.getEncoded());

        TimeStampToken  tsToken = tsResp.getTimeStampToken();
        
        assertNull(tsToken.getTimeStampInfo().getGenTimeAccuracy());  // check for abscence of accuracy
        
        assertEquals("1.2", tsToken.getTimeStampInfo().getPolicy().getId());
        
        try
        {
            tsToken.validate(cert, "SC");
        }
        catch (TSPValidationException e)
        {
            fail("certReq(false) verification of token failed.");
        }

        CertStore   respCerts = tsToken.getCertificatesAndCRLs("Collection", "SC");
        
        Collection  certsColl = respCerts.getCertificates(null);
        
        if (!certsColl.isEmpty())
        {
            fail("certReq(false) found certificates in response.");
        }
    }
    
    
    private void tokenEncodingTest(
        PrivateKey      privateKey,
        X509Certificate cert,
        CertStore       certs)
        throws Exception
    {
        TimeStampTokenGenerator tsTokenGen = new TimeStampTokenGenerator(
                privateKey, cert, TSPAlgorithms.SHA1, "1.2.3.4.5.6");
        
        tsTokenGen.setCertificatesAndCRLs(certs);

        TimeStampRequestGenerator  reqGen = new TimeStampRequestGenerator();
        TimeStampRequest           request = reqGen.generate(TSPAlgorithms.SHA1, new byte[20], BigInteger.valueOf(100));
        TimeStampResponseGenerator tsRespGen = new TimeStampResponseGenerator(tsTokenGen, TSPAlgorithms.ALLOWED);
        TimeStampResponse          tsResp = tsRespGen.generate(request, new BigInteger("23"), new Date(), "SC");

        tsResp = new TimeStampResponse(tsResp.getEncoded());

        TimeStampResponse tsResponse = new TimeStampResponse(tsResp.getEncoded());

        if (!Arrays.areEqual(tsResponse.getEncoded(), tsResp.getEncoded())
            || !Arrays.areEqual(tsResponse.getTimeStampToken().getEncoded(),
                        tsResp.getTimeStampToken().getEncoded()))
        {
            fail();
        }
    }
    
    private void testAccuracyZeroCerts(
        PrivateKey      privateKey,
        X509Certificate cert,
        CertStore       certs)
        throws Exception
    {
        TimeStampTokenGenerator tsTokenGen = new TimeStampTokenGenerator(
                privateKey, cert, TSPAlgorithms.MD5, "1.2");
        
        tsTokenGen.setCertificatesAndCRLs(certs);

        tsTokenGen.setAccuracySeconds(1);
        tsTokenGen.setAccuracyMillis(2);
        tsTokenGen.setAccuracyMicros(3);
        
        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        TimeStampRequest          request = reqGen.generate(TSPAlgorithms.SHA1, new byte[20], BigInteger.valueOf(100));

        TimeStampResponseGenerator tsRespGen = new TimeStampResponseGenerator(tsTokenGen, TSPAlgorithms.ALLOWED);

        TimeStampResponse tsResp = tsRespGen.generate(request, new BigInteger("23"), new Date(), "SC");

        tsResp = new TimeStampResponse(tsResp.getEncoded());

        TimeStampToken  tsToken = tsResp.getTimeStampToken();

        tsToken.validate(cert, "SC");
        
        //
        // check validation
        //
        tsResp.validate(request);

        //
        // check tstInfo
        //
        TimeStampTokenInfo tstInfo = tsToken.getTimeStampInfo();
        
        //
        // check accuracy
        //
        GenTimeAccuracy accuracy = tstInfo.getGenTimeAccuracy();
        
        assertEquals(1, accuracy.getSeconds());
        assertEquals(2, accuracy.getMillis());
        assertEquals(3, accuracy.getMicros());
        
        assertEquals(new BigInteger("23"), tstInfo.getSerialNumber());
        
        assertEquals("1.2", tstInfo.getPolicy().getId());
        
        //
        // test certReq
        //
        CertStore store = tsToken.getCertificatesAndCRLs("Collection", "SC");
        
        Collection certificates = store.getCertificates(null);
        
        assertEquals(0, certificates.size());
    }
    
    private void testAccuracyWithCertsAndOrdering(
        PrivateKey      privateKey,
        X509Certificate cert,
        CertStore       certs)
        throws Exception
    {
        TimeStampTokenGenerator tsTokenGen = new TimeStampTokenGenerator(
                privateKey, cert, TSPAlgorithms.MD5, "1.2.3");
        
        tsTokenGen.setCertificatesAndCRLs(certs);

        tsTokenGen.setAccuracySeconds(3);
        tsTokenGen.setAccuracyMillis(1);
        tsTokenGen.setAccuracyMicros(2);
        
        tsTokenGen.setOrdering(true);
        
        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        
        reqGen.setCertReq(true);
        
        TimeStampRequest          request = reqGen.generate(TSPAlgorithms.SHA1, new byte[20], BigInteger.valueOf(100));

        assertTrue(request.getCertReq());
        
        TimeStampResponseGenerator tsRespGen = new TimeStampResponseGenerator(tsTokenGen, TSPAlgorithms.ALLOWED);

        TimeStampResponse tsResp = tsRespGen.generate(request, new BigInteger("23"), new Date(), "SC");

        tsResp = new TimeStampResponse(tsResp.getEncoded());

        TimeStampToken  tsToken = tsResp.getTimeStampToken();

        tsToken.validate(cert, "SC");
        
        //
        // check validation
        //
        tsResp.validate(request);

        //
        // check tstInfo
        //
        TimeStampTokenInfo tstInfo = tsToken.getTimeStampInfo();
        
        //
        // check accuracy
        //
        GenTimeAccuracy accuracy = tstInfo.getGenTimeAccuracy();
        
        assertEquals(3, accuracy.getSeconds());
        assertEquals(1, accuracy.getMillis());
        assertEquals(2, accuracy.getMicros());
        
        assertEquals(new BigInteger("23"), tstInfo.getSerialNumber());
        
        assertEquals("1.2.3", tstInfo.getPolicy().getId());
        
        assertEquals(true, tstInfo.isOrdered());
        
        assertEquals(tstInfo.getNonce(), BigInteger.valueOf(100));
        
        //
        // test certReq
        //
        CertStore store = tsToken.getCertificatesAndCRLs("Collection", "SC");
        
        Collection certificates = store.getCertificates(null);
        
        assertEquals(2, certificates.size());
    }   
    
    private void testNoNonse(
        PrivateKey      privateKey,
        X509Certificate cert,
        CertStore       certs)
        throws Exception
    {
        TimeStampTokenGenerator tsTokenGen = new TimeStampTokenGenerator(
                privateKey, cert, TSPAlgorithms.MD5, "1.2.3");
        
        tsTokenGen.setCertificatesAndCRLs(certs);
        
        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        TimeStampRequest          request = reqGen.generate(TSPAlgorithms.SHA1, new byte[20]);

        assertFalse(request.getCertReq());
        
        TimeStampResponseGenerator tsRespGen = new TimeStampResponseGenerator(tsTokenGen, TSPAlgorithms.ALLOWED);

        TimeStampResponse tsResp = tsRespGen.generate(request, new BigInteger("24"), new Date(), "SC");

        tsResp = new TimeStampResponse(tsResp.getEncoded());

        TimeStampToken  tsToken = tsResp.getTimeStampToken();

        tsToken.validate(cert, "SC");
        
        //
        // check validation
        //
        tsResp.validate(request);

        //
        // check tstInfo
        //
        TimeStampTokenInfo tstInfo = tsToken.getTimeStampInfo();
        
        //
        // check accuracy
        //
        GenTimeAccuracy accuracy = tstInfo.getGenTimeAccuracy();
        
        assertNull(accuracy);
        
        assertEquals(new BigInteger("24"), tstInfo.getSerialNumber());
        
        assertEquals("1.2.3", tstInfo.getPolicy().getId());
        
        assertEquals(false, tstInfo.isOrdered());
        
        assertNull(tstInfo.getNonce());
        
        //
        // test certReq
        //
        CertStore store = tsToken.getCertificatesAndCRLs("Collection", "SC");
        
        Collection certificates = store.getCertificates(null);
        
        assertEquals(0, certificates.size());
    } 
}
