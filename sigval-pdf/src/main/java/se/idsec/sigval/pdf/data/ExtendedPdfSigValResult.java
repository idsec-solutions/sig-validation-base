/*
 * Copyright (c) 2020. IDsec Solutions AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.idsec.sigval.pdf.data;

import lombok.Getter;
import lombok.Setter;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import se.idsec.signservice.security.sign.pdf.PDFSignatureValidationResult;
import se.idsec.sigval.commons.data.ExtendedSigValResult;
import se.idsec.sigval.commons.data.TimeValidationResult;
import se.idsec.sigval.pdf.timestamp.PDFTimeStamp;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
public class ExtendedPdfSigValResult extends ExtendedSigValResult implements PDFSignatureValidationResult {

  //Data required by interfaces
  @Setter private PDSignature pdfSignature;
  @Setter private boolean cmsAlgorithmProtection;

  /** {@inheritDoc} */
  @Override public PDSignature getPdfSignature() {
    return pdfSignature;
  }

  /** {@inheritDoc} */
  @Override public boolean isCmsAlgorithmProtection() {
    return cmsAlgorithmProtection;
  }

  /** Signature algorithm declared CMS SignerInfo **/
  @Setter @Getter private ASN1ObjectIdentifier cmsSignatureAlgo;
  /** Digest algorithm declared in embedded CMS SignerInfo **/
  @Setter @Getter private ASN1ObjectIdentifier cmsDigestAlgo;
  /** Signature algorithm declared in embedded CMS algorithm protection signed attribute **/
  @Setter @Getter private ASN1ObjectIdentifier cmsAlgoProtectionSigAlgo;
  /** Digest algorithm declared in embedded CMS algorithm protection signed attribute **/
  @Setter @Getter private ASN1ObjectIdentifier cmsAlgoProtectionDigestAlgo;
  /** The bytes of content info of this signature (The bytes of the PDSignature oject **/
  @Setter @Getter private byte[] signedData;

  @Override public List<PdfTimeValidationResult> getTimeValidationResults() {
    return (List<PdfTimeValidationResult>) super.getTimeValidationResults();
  }

  @Override public void setTimeValidationResults(List<? extends TimeValidationResult> timeValidationResults) {
    super.setTimeValidationResults(
      timeValidationResults.stream()
        .map(timeValidationResult -> getPdfTimeResults(timeValidationResult))
        .collect(Collectors.toList())
    );
  }

  private PdfTimeValidationResult getPdfTimeResults(TimeValidationResult timeValidationResult) {
    if (timeValidationResult instanceof PdfTimeValidationResult) return (PdfTimeValidationResult) timeValidationResult;
    return new PdfTimeValidationResult(
      timeValidationResult.getTimeValidationClaims(),
      timeValidationResult.getCertificateValidationResult()
      ,null
    );
  }
}
