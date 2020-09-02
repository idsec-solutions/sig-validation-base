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

package se.idsec.sigval.pdf.verify.policy;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import se.idsec.sigval.pdf.data.PDFConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Examines a PDF document and gathers context data used to determine document revisions and if any of those
 * revisions may alter the document appearance with respect to document signatures.
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Slf4j
public class PdfSignatureContext {

  /** The characters indicating end of a PDF document revision */
  private final static String EOF = "%%EOF";
  /** The bytes of a PDF document */
  @Getter byte[] pdfBytes;
  /** Document revisions */
  @Getter List<PdfDocRevision> pdfDocRevisions;
  /** Document signatures */
  @Getter List<PDSignature> signatures = new ArrayList<>();

  /**
   * Constructor
   *
   * @param pdfBytes the bytes of a PDF document
   * @throws IOException if theis docuemnt is not a well formed PDF document
   */
  public PdfSignatureContext(byte[] pdfBytes) throws IOException {
    this.pdfBytes = pdfBytes;
    getDocRevisions();
  }

  /**
   * Extracts the bytes of the PDF document that was signed by the provided signature
   *
   * @param signature pdf signature
   * @return the byes signed by the provided signature
   * @throws IllegalArgumentException if the signature is not found or no signed data can be located
   */
  public byte[] getSignedDocument(PDSignature signature) throws IllegalArgumentException {
    try {
      int idx = getSignatureRevisionIndex(signature);
      if (idx == -1) {
        throw new IllegalArgumentException("Signature not found");
      }
      if (idx < 1) {
        throw new IllegalArgumentException("No revision found before the signature was added");
      }
      return Arrays.copyOf(pdfBytes, pdfDocRevisions.get(idx - 1).getLength());
    }
    catch (Exception ex) {
      throw new IllegalArgumentException("Error extracting signed version", ex);
    }
  }

  /**
   * Check if the pdf docuement was updated after this signature was added to the document, where the new update is not
   * a new signature or document timestamp or is a valid DSS store.
   *
   * <p>An update to a PDF docuemtn applied after the PDF document was signed invalidates any existing signture unless the
   * update is not a new signature, document timestamp or a DSS store</p>
   *
   * <p>Some validation policies may require that any new signatures or document timestamps must be trusted and verified
   * for it to be an acceptable update to a signed document</p>
   *
   * @param signature the PDF signature
   * @return true if the provided signature was updated by a non signature update
   * @throws IllegalArgumentException on failure to test if the signature was updated by a non signature update
   */
  public boolean isSignatureExtendedByNonSignatureUpdates(PDSignature signature) throws IllegalArgumentException {
    try {
      int idx = getSignatureRevisionIndex(signature);
      if (idx == -1) {
        throw new IllegalArgumentException("Signature not found");
      }
      for (int i = idx; i < pdfDocRevisions.size() - 1; i++) {
        // Loop as long as index indicates that there is a later revision (index < revisions -1)
        PdfDocRevision pdfDocRevision = pdfDocRevisions.get(i + 1);
        if (!pdfDocRevision.isSignature() && !pdfDocRevision.isValidDSS()) {
          //A later revsion exist that is NOT a signature, document timestamp or valid DSS (Digital Signature Store)
          return true;
        }
      }
      // We did not find any later revisions that are not a signature or document timestamp
      return false;
    }
    catch (Exception ex) {
      throw new IllegalArgumentException("Error examining signature extensions", ex);
    }
  }

  private int getSignatureRevisionIndex(PDSignature signature) throws IllegalArgumentException {
    try {
      int[] byteRange = signature.getByteRange();
      int len = byteRange[2] + byteRange[3];

      for (int i = 0; i < pdfDocRevisions.size(); i++) {
        PdfDocRevision revision = pdfDocRevisions.get(i);
        if (revision.getLength() == len) {
          // Get the bytes of the prior revision
          return i;
        }
      }
      return -1;
    }
    catch (Exception ex) {
      throw new IllegalArgumentException("Error examining signature revision", ex);
    }
  }

  /**
   * Test if this signature covers the whole document.
   *
   * <p>Signature is considered to cover the whole document if it is the last update to the PDF document (byte range covers the whole document) or:</p>
   * <ul>
   *   <li>All new updates are signature, doc timstamp or DSS updates, and</li>
   *   <li>Updates to existing objects is limited to the root object, and</li>
   *   <li>Root objects contains no changes but allows added items, and</li>
   *   <li>Where added items to the root object is limited to "DSS" and "AcroForm</li>
   * </ul>
   *
   * @param signature The signature tested if it covers the whole document
   * @return true if the signature covers the whole document
   */
  public boolean isCoversWholeDocument(PDSignature signature) throws IllegalArgumentException {
    int revisionIndex = getSignatureRevisionIndex(signature);
    if (revisionIndex == -1){
      throw new IllegalArgumentException("The specified signature was not found in the document");
    }
    if (revisionIndex == pdfDocRevisions.size() -1){
      // The signature is the last revision
      return true;
    }

    for (int i = revisionIndex + 1 ; i < pdfDocRevisions.size() ;i++){
      PdfDocRevision nextRevision = pdfDocRevisions.get(i);
      if (!nextRevision.isSafeUpdate()){
        return false;
      }
    }
    return true;
  }

  /**
   * Internal function used to extract data about all document revisions of the current PDF document
   *
   * @throws IOException on error loading PDF document data
   */
  private void getDocRevisions() throws IOException {

    // Get all pdf document signatures and document timestamps
    PDDocument pdfDoc = PDDocument.load(pdfBytes);
    signatures = pdfDoc.getSignatureDictionaries();
    pdfDoc.close();
    pdfDocRevisions = new ArrayList<>();
    PdfDocRevision lastRevision = getRevision(null);
    while (lastRevision != null) {
      PdfDocRevision lastRevisionClone = new PdfDocRevision(lastRevision);
      pdfDocRevisions.add(lastRevisionClone);
      lastRevision = getRevision(lastRevisionClone);
    }

    List<PDDocument> pdDocumentList = new ArrayList<>();

    List<PdfDocRevision> consolidatedList = new ArrayList<>();
    for (PdfDocRevision rev : pdfDocRevisions) {
      byte[] revBytes = Arrays.copyOf(pdfBytes, rev.getLength());
      try {
        PDDocument revDoc = PDDocument.load(revBytes);
        pdDocumentList.add(revDoc);
        COSDocument cosDocument = revDoc.getDocument();
        List<COSObject> objects = cosDocument.getObjects();
        COSDictionary trailer = cosDocument.getTrailer();
        long rootObjectId = getRootObjectId(trailer);
        COSObject rootObject = objects.stream()
          .filter(cosObject -> cosObject.getObjectNumber() == rootObjectId)
          .findFirst().get();
        Map<COSObjectKey, Long> xrefTable = cosDocument.getXrefTable();

        rev.setXrefTable(xrefTable);
        rev.setRootObjectId(rootObjectId);
        rev.setRootObject(rootObject);
        rev.setTrailer(trailer);

        consolidatedList.add(rev);
      }
      catch (Exception ignored) {
        // This means that this was not a valid PDF revision segment and is therefore skipped
      }
    }

    // Get consolidated and sorted list of PDF revisions
    pdfDocRevisions = consolidatedList.stream()
      .sorted(Comparator.comparingInt(value -> value.getLength()))
      .collect(Collectors.toList());

    PdfDocRevision lastRevData = null;
    for (PdfDocRevision revData : pdfDocRevisions) {
      getXrefUpdates(revData, lastRevData);
      lastRevData = revData;
    }

    // Close documents
    pdDocumentList.stream().forEach(pdDocument -> {
      try {
        pdDocument.close();
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    });

  }

  /**
   * Internal method for obtaining basic revision data for a document revision. Revision data is collected in reverse order starting with
   * the most recent revision. This is a natural con
   *
   * @param priorRevision Data obtained from the revision after this revision.
   * @return
   */
  private PdfDocRevision getRevision(PdfDocRevision priorRevision) {
    PdfDocRevision docRevision = new PdfDocRevision();
    int len = priorRevision == null ? pdfBytes.length : priorRevision.length - 5;

    String pdfString = new String(Arrays.copyOf(pdfBytes, len), StandardCharsets.ISO_8859_1);
    int lastIndexOfEoF = pdfString.lastIndexOf(EOF);
    if (lastIndexOfEoF == -1) {
      // There are no prior revisions. Return null;
      return null;
    }

    int revisionLen = lastIndexOfEoF + 5;
    byte firstNl = pdfBytes.length > revisionLen ? pdfBytes[revisionLen] : 0x00;
    byte secondNl = pdfBytes.length > revisionLen + 1 ? pdfBytes[revisionLen + 1] : 0x00;

    revisionLen = firstNl == 0x0a
      ? revisionLen + 1
      : firstNl == 0x0d && secondNl == 0x0a
      ? revisionLen + 2
      : revisionLen;

    boolean revIsSignature = false;
    boolean revIsDocTs = false;
    for (PDSignature signature : signatures) {
      int[] byteRange = signature.getByteRange();
      if (byteRange[2] + byteRange[3] == revisionLen) {
        revIsSignature = true;
        revIsDocTs = PDFConstants.SUBFILTER_ETSI_RFC3161.equals(signature.getSubFilter());
      }
    }

    return PdfDocRevision.builder()
      .length(revisionLen)
      .signature(revIsSignature)
      .documentTimestamp(revIsDocTs)
      .build();
  }

  private static long getRootObjectId(COSDictionary trailer) throws Exception {
    COSObject root = trailer.getCOSObject(COSName.ROOT);
    return root.getObjectNumber();
  }

  private static void getXrefUpdates(PdfDocRevision revData, PdfDocRevision lastRevData) {
    revData.setLegalRootObject(true);
    revData.setRootUpdate(false);
    revData.setNonRootUpdate(false);
    Map<COSObjectKey, Long> lastTable = lastRevData == null ? new HashMap<>() : lastRevData.getXrefTable();
    Map<COSObjectKey, Long[]> changedXref = new HashMap<>();
    Map<COSObjectKey, Long> addedXref = new HashMap<>();
    Map<COSObjectKey, Long> xrefTable = revData.getXrefTable();

    // Find new and changed xref values
    xrefTable.keySet().forEach(cosObjectKey -> {
      Long newValue = xrefTable.get(cosObjectKey);
      if (lastTable.containsKey(cosObjectKey)) {
        Long lastValue = lastTable.get(cosObjectKey);
        if (lastValue.longValue() != newValue.longValue()) {
          changedXref.put(cosObjectKey, new Long[] { lastValue, newValue });
        }
      }
      else {
        addedXref.put(cosObjectKey, newValue);
      }
    });
    revData.setChangedXref(changedXref);
    revData.setAddedXref(addedXref);

    changedXref.keySet().stream().forEach(cosObjectKey -> {
      if (cosObjectKey.getNumber() == revData.getRootObjectId()) {
        revData.setRootUpdate(true);
      }
      if (cosObjectKey.getNumber() != revData.getRootObjectId()) {
        revData.setNonRootUpdate(true);
      }
    });

    List<COSName> changedRootItems = new ArrayList<>();
    List<COSName> addedRootItems = new ArrayList<>();
    if (revData.isRootUpdate()) {
      COSBase baseObject = revData.getRootObject().getObject();
      if (baseObject instanceof COSDictionary) {
        revData.setLegalRootObject(true);
        COSObject lastRoot = lastRevData.getRootObject();
        COSDictionary rootDic = (COSDictionary) baseObject;
        rootDic.entrySet().stream().forEach(cosNameCOSBaseEntry -> {
          COSName key = cosNameCOSBaseEntry.getKey();
          DictionaryBaseValue value = new DictionaryBaseValue(cosNameCOSBaseEntry.getValue());
          DictionaryBaseValue lastValue = new DictionaryBaseValue(lastRoot.getItem(key));
          if (lastValue.getValueType() != null) {
            if (lastValue.getValueType().equals(ValueType.Other)) {
              revData.setLegalRootObject(false);
            }
            else {
              if (!value.matches(lastValue)) {
                changedRootItems.add(key);
              }
            }
          }
          else {
            addedRootItems.add(key);
          }

        });
      }
      else {
        revData.setLegalRootObject(false);
      }
    }
    revData.setChangedRootItems(changedRootItems);
    revData.setAddedRootItems(addedRootItems);

    revData.setValidDSS(
      revData.isRootUpdate()
        && !revData.isNonRootUpdate()
        && revData.isLegalRootObject()
        && revData.getChangedRootItems().size() == 0
        && revData.getAddedRootItems().size() == 1
        && revData.getAddedRootItems().get(0).getName().equals("DSS")
    );

    boolean nonDssOrAcroformUpdate = revData.getAddedRootItems().stream()
      .filter(name -> !name.equals(COSName.ACRO_FORM) && !name.getName().equals("DSS"))
      .findFirst().isPresent();

    revData.setSafeUpdate(
      !revData.isNonRootUpdate()
        && revData.isLegalRootObject()
        && revData.getChangedRootItems().size() == 0
        && (revData.isSignature() || revData.isDocumentTimestamp() || revData.isValidDSS())
        && !nonDssOrAcroformUpdate
    );

  }

  @Data
  public static class DictionaryBaseValue {
    private ValueType valueType;
    private Object value;

    public DictionaryBaseValue(COSBase baseObject) {
      if (baseObject == null) {
        valueType = null;
        value = null;
        return;
      }
      if (baseObject instanceof COSObject) {
        valueType = ValueType.COSObject;
        value = ((COSObject) baseObject).getObjectNumber();
        return;
      }
      if (baseObject instanceof COSDictionary) {
        valueType = ValueType.COSDictionary;
        value = null;
        return;
      }
      if (baseObject instanceof COSString) {
        valueType = ValueType.COSString;
        value = ((COSString)baseObject).getString();
        return;
      }
      if (baseObject instanceof COSArray) {

        List<DictionaryBaseValue> baseValues = ((COSArray) baseObject).toList().stream()
          .map(cosBase -> new DictionaryBaseValue(cosBase))
          .collect(Collectors.toList());
        boolean nonSupportedValue = baseValues.stream()
          .filter(dictionaryBaseValue -> dictionaryBaseValue.getValueType().equals(ValueType.Other))
          .findFirst().isPresent();
        if (nonSupportedValue){
          valueType = ValueType.Other;
          value = null;
          return;
        }
        valueType = ValueType.COSArray;
        value = baseValues;
        return;
      }
      if (baseObject instanceof COSName) {
        valueType = ValueType.COSDictionary;
        value = baseObject;
        return;
      }
      valueType = ValueType.Other;
      value = null;
    }

    public boolean matches(DictionaryBaseValue compareValue) {
      if (!valueType.equals(compareValue.getValueType())) {
        return false;
      }
      try {
        switch (valueType) {
        case COSObject:
          return (long) compareValue.getValue() == (long) value;
        case COSDictionary:
          return true;
        case COSName:
          return compareValue.getValue().equals(value);
        case COSString:
          return ((String)compareValue.getValue()).equalsIgnoreCase((String) value);
        case COSArray:
          boolean match=true;
          List<DictionaryBaseValue> valueObjList = (List<DictionaryBaseValue>) value;
          List<DictionaryBaseValue> compareObjectLIst = (List<DictionaryBaseValue>) compareValue.getValue();

          for (int i = 0 ; i<valueObjList.size(); i++){
            if (!valueObjList.get(i).matches(compareObjectLIst.get(i))){
              match = false;
            }
          }
          return match;
        case Other:
          return false;
        }
      }
      catch (Exception ex) {
        return false;
      }
      return false;
    }
  }

  public static enum ValueType {
    COSObject, COSDictionary, COSName, COSString, COSArray, Other
  }

  @NoArgsConstructor
  @AllArgsConstructor
  @Data
  @Builder
  public static class PdfDocRevision {
    private int length;
    private boolean signature;
    private boolean documentTimestamp;
    private boolean validDSS;
    private boolean safeUpdate;
    private long rootObjectId;
    private COSObject rootObject;
    private COSDictionary trailer;
    private Map<COSObjectKey, Long> xrefTable;
    private Map<COSObjectKey, Long[]> changedXref;
    private Map<COSObjectKey, Long> addedXref;
    private boolean rootUpdate;
    private boolean nonRootUpdate;
    private boolean legalRootObject;
    private List<COSName> changedRootItems;
    private List<COSName> addedRootItems;

    public PdfDocRevision(PdfDocRevision pdfDocRevision) {
      this.length = pdfDocRevision.getLength();
      this.signature = pdfDocRevision.isSignature();
      this.documentTimestamp = pdfDocRevision.isDocumentTimestamp();
    }
  }

}
