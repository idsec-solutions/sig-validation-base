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

import lombok.*;
import se.idsec.signservice.security.certificate.CertificateValidationResult;
import se.idsec.sigval.commons.data.TimeValidationResult;
import se.idsec.sigval.pdf.timestamp.PDFTimeStamp;
import se.idsec.sigval.svt.claims.TimeValidationClaims;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PdfTimeValidationResult extends TimeValidationResult {

  public PdfTimeValidationResult(TimeValidationClaims timeValidationClaims,
    CertificateValidationResult certificateValidationResult,
    PDFTimeStamp timeStamp) {
    super(timeValidationClaims, certificateValidationResult);
    this.timeStamp = timeStamp;
  }

  /** Signature timestamps obtained through PKI validation of the signature **/
  private PDFTimeStamp timeStamp;

}