package se.idsec.sigval.pdf.verify.impl;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.codec.binary.Base64;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.cms.SignedData;
import se.idsec.signservice.security.certificate.CertificateValidator;
import se.idsec.signservice.security.sign.SignatureValidationResult;
import se.idsec.signservice.security.sign.pdf.PDFSignatureValidator;
import se.idsec.sigval.commons.algorithms.JWSAlgorithmRegistry;
import se.idsec.sigval.commons.data.SigValIdentifiers;
import se.idsec.sigval.commons.data.SignedDocumentValidationResult;
import se.idsec.sigval.pdf.data.ExtendedPdfSigValResult;
import se.idsec.sigval.pdf.svt.PDFSVTValidator;
import se.idsec.sigval.pdf.timestamp.PDFDocTimeStamp;
import se.idsec.sigval.pdf.utils.CMSVerifyUtils;
import se.idsec.sigval.pdf.utils.PDFSVAUtils;
import se.idsec.sigval.pdf.verify.PdfSignatureVerifier;
import se.idsec.sigval.svt.algorithms.SVTAlgoRegistry;
import se.idsec.sigval.svt.claims.PolicyValidationClaims;
import se.idsec.sigval.svt.claims.SignatureClaims;
import se.idsec.sigval.svt.claims.TimeValidationClaims;
import se.idsec.sigval.svt.claims.ValidationConclusion;
import se.idsec.sigval.svt.validation.SignatureSVTValidationResult;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This class provides the functionality to validate signatures on a PDF where the signature validation process is enhanced with validation
 * based on SVA (Signature Validation Assertions). The latest valid SVA that can be verified given the provided trust validation resources is selected.
 * Signatures covered by this SVA is validated based on SVA. Any other signatures are validated through traditional signature validation methods.
 */
public class SVTenabledPDFDocumentSigVerifier implements PDFSignatureValidator {

  public static Logger LOG = Logger.getLogger(SVTenabledPDFDocumentSigVerifier.class.getName());
  /** SVT token validator **/
  private PDFSVTValidator pdfsvtValidator;
  /** Signature verifier for signatures not supported by SVT. This verifier is also performing validation of signature timestamps **/
  private PdfSignatureVerifier pdfSignatureVerifier;

  /**
   * Constructor
   *
   * @param pdfSignatureVerifier The verifier used to verify signatures not supported by SVA
   * @param pdfsvtValidator      Certificate verifier for the certificate used to sign SVA tokens
   */
  public SVTenabledPDFDocumentSigVerifier(PdfSignatureVerifier pdfSignatureVerifier, PDFSVTValidator pdfsvtValidator) {
    this.pdfSignatureVerifier = pdfSignatureVerifier;
    this.pdfsvtValidator = pdfsvtValidator;
  }

  /**
   * Verifies the signatures of a PDF document. Validation based on SVT is given preference over traditional signature validation.
   *
   * @param pdfDoc signed PDF document to verify
   * @return Validation result from PDF verification
   * @throws SignatureException on error
   */
  public List<SignatureValidationResult> validate(File pdfDoc) throws SignatureException {
    byte[] docBytes = null;
    try {
      docBytes = IOUtils.toByteArray(new FileInputStream(pdfDoc));
    } catch (IOException ex){
      throw new SignatureException("Unable to read signed file", ex);
    }
    return validate(docBytes);
  }

  /**
   * Verifies the signatures of a PDF document. Validation based on SVA is given preference over traditional signature validation.
   *
   * @param pdfDocBytes signed PDF document to verify
   * @return Validation result from PDF verification
   * @throws SignatureException on error
   */
  @Override public List<SignatureValidationResult> validate(byte[] pdfDocBytes) throws SignatureException {
    try {
      PDDocument pdfDocument = PDDocument.load(pdfDocBytes);
      List<PDSignature> allSignatureList = pdfDocument.getSignatureDictionaries();
      pdfDocument.close();
      List<PDSignature> docTsSigList = new ArrayList<>();
      List<PDSignature> signatureList = new ArrayList<>();

      for (PDSignature signature : allSignatureList) {
        String type = PDFSVAUtils.getSignatureType(signature, signature.getContents(pdfDocBytes));
        switch (type) {
        case PDFSVAUtils.SVT_TYPE:
        case PDFSVAUtils.DOC_TIMESTAMP_TYPE:
          docTsSigList.add(signature);
          break;
        case PDFSVAUtils.SIGNATURE_TYPE:
          signatureList.add(signature);
        }
      }

      /**
       * 1) Check that signature is covered by a valid SVA token. If not - perform regular signature verification
       * 2) If signature is covered by SVA token, do validation based on SVA only.
       */

      // Do SVT validation
      List<SignatureSVTValidationResult> svtValidationResults = pdfsvtValidator.validate(pdfDocBytes);
      // Create empty result list
      List<SignatureValidationResult> sigVerifyResultList = new ArrayList<>();
      // This list starts empty. It is only filled with objects if there is a signature that is validated without SVT.
      List<PDFDocTimeStamp> docTimeStampList = new ArrayList<>();
      boolean docTsVerified = false;

      for (PDSignature signature : signatureList) {
        SignatureSVTValidationResult svtValResult = getMatchingSvtValidation(
          PDFSVAUtils.getSignatureValueBytes(signature, pdfDocBytes), svtValidationResults);
        if (svtValResult == null) {
          // This signature is not covered by the SVA. Perform normal signature verification
          try {
            //Get verified documentTimestamps if not previously loaded
            if (!docTsVerified) {
              docTimeStampList = pdfSignatureVerifier.verifyDocumentTimestamps(docTsSigList, pdfDocBytes);
              docTsVerified = true;
            }

            SignatureValidationResult directVerifyResult = pdfSignatureVerifier.verifySignature(signature, pdfDocBytes, docTimeStampList);
            sigVerifyResultList.add(directVerifyResult);
          }
          catch (Exception e) {
            LOG.warning("Error parsing the PDF signature: " + e.getMessage());
            sigVerifyResultList.add(getErrorResult(signature, e.getMessage()));
          }
          // Skip SVA validation
          continue;
        }
        sigVerifyResultList.add(compliePDFSigValResultsFromSvtValidation(svtValResult, signature, pdfDocBytes));
      }
      return sigVerifyResultList;
    } catch (Exception ex){
      throw new SignatureException("Error validating signatures on PDF document", ex);
    }
  }

  /** {@inheritDoc} */
  @Override public boolean isSigned(byte[] document) throws IllegalArgumentException {
    PDDocument pdfDocument = null;
    try {
      pdfDocument = PDDocument.load(document);
      return !pdfDocument.getSignatureDictionaries().isEmpty();
    }
    catch (IOException e) {
      throw new IllegalArgumentException("Invalid document", e);
    }
    finally {
      try {
        if (pdfDocument != null) {
          pdfDocument.close();
        }
      }
      catch (IOException e) {
      }
    }
  }

  /**
   * This implementation allways perform PKIX validation and returns an empty list for this function
   * @return empty list
   */
  @Override public List<X509Certificate> getRequiredSignerCertificates() {
    return new ArrayList<>();
  }

  @Override public CertificateValidator getCertificateValidator() {
    return pdfSignatureVerifier.getCertificateValidator();
  }

  private ExtendedPdfSigValResult compliePDFSigValResultsFromSvtValidation(SignatureSVTValidationResult svtValResult,
    PDSignature signature, byte[] pdfDocBytes) {
    ExtendedPdfSigValResult cmsSVResult = new ExtendedPdfSigValResult();
    cmsSVResult.setPdfSignature(signature);

    try {
      byte[] sigBytes = signature.getContents(pdfDocBytes);
      cmsSVResult.setSignedData(sigBytes);

      //Reaching this point means that the signature is valid and verified through the SVA.
      SignedData signedData = PDFSVAUtils.getSignedDataFromSignature(sigBytes);
      cmsSVResult.setPades(signature.getSubFilter().equalsIgnoreCase(PDFSVAUtils.CADES_SIG_SUBFILETER_LC));
      cmsSVResult.setInvalidSignCert(false);
      cmsSVResult.setClaimedSigningTime(PDFSVAUtils.getClaimedSigningTime(signature.getSignDate(), signedData).getTime());

      //Get algorithms and public key type. Note that the source of these values is the SVA signature which is regarded as the algorithm
      //That is effectively protecting the integrity of the signature, superseding the use of the original algorithms.
      SignedJWT signedJWT = svtValResult.getSignedJWT();
      JWSAlgorithm svtJwsAlgo = signedJWT.getHeader().getAlgorithm();

      String algoUri = JWSAlgorithmRegistry.getUri(svtJwsAlgo);
      cmsSVResult.setSignatureAlgorithm(algoUri);
      CMSVerifyUtils.getPkParams(getCert(svtValResult.getSignerCertificate()).getPublicKey(), cmsSVResult);

      //Set signed SVT JWT
      cmsSVResult.setSvtJWT(signedJWT);

      // Finalize
      SignatureClaims signatureClaims = svtValResult.getSignatureClaims();
      cmsSVResult.setSignerCertificate(getCert(svtValResult.getSignerCertificate()));
      cmsSVResult.setSignatureCertificateChain(getCertList(svtValResult.getCertificateChain()));
      cmsSVResult.setSuccess(svtValResult.isSvtValidationSuccess());
      if (cmsSVResult.isSuccess()){
        cmsSVResult.setStatus(SignatureValidationResult.Status.SUCCESS);
      } else {
        cmsSVResult.setStatus(SignatureValidationResult.Status.ERROR_INVALID_SIGNATURE);
        cmsSVResult.setStatusMessage("Unable to verify SVT signature");
      }
      cmsSVResult.setSignatureClaims(signatureClaims);
      cmsSVResult.setValidationPolicyResultList(signatureClaims.getSig_val());
      // Since we verify with SVA. We ignore any present signature timestamps.
      cmsSVResult.setSignatureTimeStampList(new ArrayList<>());

      //Add SVT document timestamp that was used to perform this SVT validation to verified times
      //This ensures that this time stamp gets added when SVT issuance is based on a previous SVT.
      JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
      List<TimeValidationClaims> timeValidationClaimsList = signatureClaims.getTime_val();
      timeValidationClaimsList.add(TimeValidationClaims.builder()
        .iss(jwtClaimsSet.getIssuer())
        .time(jwtClaimsSet.getIssueTime().getTime() / 1000)
        .type(SigValIdentifiers.TIME_VERIFICATION_TYPE_SVT)
        .id(jwtClaimsSet.getJWTID())
        .val(Arrays.asList(PolicyValidationClaims.builder()
          // TODO Get policy from certificate validator
          .pol(SigValIdentifiers.SIG_VALIDATION_POLICY_PKIX_VALIDATION)
          .res(ValidationConclusion.PASSED)
          .build()))
        .build());
      cmsSVResult.setTimeValidationClaimsList(timeValidationClaimsList);

    }
    catch (Exception ex) {
      cmsSVResult.setSuccess(false);
      cmsSVResult.setStatus(SignatureValidationResult.Status.ERROR_INVALID_SIGNATURE);
      cmsSVResult.setStatusMessage("Unable to process SVA token or signature data");
      return cmsSVResult;
    }
    return cmsSVResult;
  }

  /**
   * This verifies and returns the validated document timestamp holding the current SVT token used to validate signatures
   *
   * @param svtTsSigList list of signatures containing SVT tokens
   * @param jwtClaimsSet the claims set from the current SVT used to validate signatures
   * @param pdfDocBytes  the bytes of the validated pdf document
   * @return PDFDocument timestamp object
   * @throws IOException
   * @throws ParseException
   */
  private PDFDocTimeStamp getCurrentSvtTimestamp(List<PDSignature> svtTsSigList, JWTClaimsSet jwtClaimsSet, byte[] pdfDocBytes)
    throws IOException, ParseException {
    List<PDFDocTimeStamp> docTimeStampList = pdfSignatureVerifier.verifyDocumentTimestamps(svtTsSigList, pdfDocBytes);
    for (PDFDocTimeStamp docTimeStamp : docTimeStampList) {
      String svajwt = PDFSVAUtils.getSVAJWT(docTimeStamp.getTstInfo());
      SignedJWT signedJWT = SignedJWT.parse(svajwt);
      if (signedJWT.getJWTClaimsSet().getJWTID().equalsIgnoreCase(jwtClaimsSet.getJWTID())) {
        return docTimeStamp;
      }
    }
    throw new IOException("No matching SVT timestamp is available to support the SVT results");
  }

  private SignatureSVTValidationResult getMatchingSvtValidation(byte[] sigValueBytes,
    List<SignatureSVTValidationResult> svtValidationResults) {
    for (SignatureSVTValidationResult svtValResult : svtValidationResults) {
      try {
        MessageDigest md = SVTAlgoRegistry.getMessageDigestInstance(svtValResult.getSignedJWT().getHeader().getAlgorithm());
        String sigValueHashStr = Base64.encodeBase64String(md.digest(sigValueBytes));
        if (sigValueHashStr.equals(svtValResult.getSignatureClaims().getSig_ref().getSig_hash())) {
          return svtValResult;
        }
      }
      catch (NoSuchAlgorithmException e) {
        continue;
      }
    }
    return null;
  }

  private ExtendedPdfSigValResult getErrorResult(PDSignature signature, String message) {
    ExtendedPdfSigValResult sigResult = new ExtendedPdfSigValResult();
    sigResult.setSuccess(false);
    sigResult.setPdfSignature(signature);
    sigResult.setStatus(SignatureValidationResult.Status.ERROR_INVALID_SIGNATURE);
    sigResult.setStatusMessage("Failed to process signature: " + message);
    return sigResult;
  }

  private X509Certificate getCert(byte[] certBytes) throws CertificateException {
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
  }

  private List<X509Certificate> getCertList(List<byte[]> certificateChain) throws CertificateException {
    List<X509Certificate> certList = new ArrayList<>();
    for (byte[] certBytes : certificateChain) {
      certList.add(getCert(certBytes));
    }
    return certList;
  }

  /**
   * Compile a complete PDF signature verification result object from the list of individual signature results
   *
   * @param sigVerifyResultList list of individual signature validation results. Each result must be of type {@link ExtendedPdfSigValResult}
   * @return PDF signature validation result objects
   */
  public static SignedDocumentValidationResult<ExtendedPdfSigValResult> getConcludingSigVerifyResult(List<SignatureValidationResult> sigVerifyResultList) {
    SignedDocumentValidationResult<ExtendedPdfSigValResult> sigVerifyResult = new SignedDocumentValidationResult<>();
    List<ExtendedPdfSigValResult> extendedPdfSigValResults = new ArrayList<>();
    try {
      extendedPdfSigValResults = sigVerifyResultList.stream()
        .map(signatureValidationResult -> (ExtendedPdfSigValResult) signatureValidationResult)
        .collect(Collectors.toList());
      sigVerifyResult.setSignatureValidationResults(extendedPdfSigValResults);
    } catch (Exception ex){
      throw new IllegalArgumentException("Provided results are not instances of ExtendedPdfSigValResult");
    }
    //sigVerifyResult.setDocTimeStampList(docTimeStampList);
    // Test if there are no signatures
    if (sigVerifyResultList.isEmpty()) {
      sigVerifyResult.setSignatureCount(0);
      sigVerifyResult.setStatusMessage("No signatures");
      sigVerifyResult.setValidSignatureCount(0);
      sigVerifyResult.setCompleteSuccess(false);
      sigVerifyResult.setSigned(false);
      return sigVerifyResult;
    }

    //Get valid signatures
    sigVerifyResult.setSigned(true);
    sigVerifyResult.setSignatureCount(sigVerifyResultList.size());
    List<ExtendedPdfSigValResult> validSignatureResultList = extendedPdfSigValResults.stream()
      .filter(cmsSigVerifyResult -> cmsSigVerifyResult.isSuccess())
      .collect(Collectors.toList());

    sigVerifyResult.setValidSignatureCount(validSignatureResultList.size());
    if (validSignatureResultList.isEmpty()) {
      //No valid signatures
      sigVerifyResult.setCompleteSuccess(false);
      sigVerifyResult.setStatusMessage("No valid signatures");
      return sigVerifyResult;
    }

    //Reaching this point means that there are valid signatures.
    if (sigVerifyResult.getSignatureCount() == validSignatureResultList.size()){
      sigVerifyResult.setStatusMessage("OK");
      sigVerifyResult.setCompleteSuccess(true);
    } else {
      sigVerifyResult.setStatusMessage("Some signatures are valid and some are invalid");
      sigVerifyResult.setCompleteSuccess(false);
    }
    return sigVerifyResult;
  }

}