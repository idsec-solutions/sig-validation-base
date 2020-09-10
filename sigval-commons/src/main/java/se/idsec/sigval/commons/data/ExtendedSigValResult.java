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

package se.idsec.sigval.commons.data;

import com.nimbusds.jwt.SignedJWT;
import lombok.Getter;
import lombok.Setter;
import se.idsec.signservice.security.sign.impl.DefaultSignatureValidationResult;
import se.idsec.sigval.commons.algorithms.NamedCurve;
import se.idsec.sigval.commons.algorithms.PublicKeyType;
import se.idsec.sigval.svt.claims.PolicyValidationClaims;
import se.idsec.sigval.svt.claims.SignatureClaims;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
public class ExtendedSigValResult extends DefaultSignatureValidationResult {

  //Aditional data
  /** The overall concluding indicator whether the signature validation process was successful */
  @Setter @Getter boolean success;
  /** The complete list of certificates provided with the signature which may differ from the constructed path to a trust anchor */
  @Setter @Getter private List<X509Certificate> signatureCertificateChain;
  /** Indicator if the signature covers the visible PDF document. False value indicates that there may be visual content changes added after signing **/
  @Setter @Getter private boolean coversDocument = false;
  /** The pdf document in the form it was before being signed by this signature */
  @Setter @Getter private byte[] signedDocument;
  /** Legacy indicator if the signing certificate matches a present ESSSigningCertificate signed attribute **/
  @Setter @Getter private boolean invalidSignCert = false;
  /** Public key type **/
  @Setter @Getter private PublicKeyType pkType;
  /** The ECC curve if the signature is signed with ECDSA **/
  @Setter @Getter private NamedCurve namedEcCurve;
  /** Length of the signature key used for the  sig algorithm **/
  @Setter @Getter private int keyLength;
  /** List of validation policies applied to the validation process and if they succeeded or failed **/
  @Setter @Getter private List<PolicyValidationClaims> validationPolicyResultList = new ArrayList<>();
  /** List of verified times and information about how time verification was done **/
  @Setter @Getter private List<? extends TimeValidationResult> timeValidationResults = new ArrayList<>();
  /** The signature SVA claims of this signature **/
  @Setter @Getter private SignatureClaims signatureClaims;
  /** The signed SVT JWT. Null content if the verification is not SVT verified **/
  @Setter @Getter private SignedJWT svtJWT;

}