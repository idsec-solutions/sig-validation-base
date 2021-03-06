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

package se.idsec.x509cert.extensions;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.qualified.QCStatement;
import se.idsec.x509cert.extensions.data.MonetaryValue;
import se.idsec.x509cert.extensions.data.PDSLocation;
import se.idsec.x509cert.extensions.data.SemanticsInformation;
import se.idsec.x509cert.extensions.utils.ExtensionUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * QCStatements X.509 extension implementation for extending Bouncycastle
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@NoArgsConstructor
@Slf4j
public class QCStatements extends ASN1Object {

    public static final ASN1ObjectIdentifier OID = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.3");
    public static final ASN1ObjectIdentifier PKIX_SYNTAX_V1 = QCStatement.id_qcs_pkixQCSyntax_v1;
    public static final ASN1ObjectIdentifier PKIX_SYNTAX_V2 = QCStatement.id_qcs_pkixQCSyntax_v2;
    public static final ASN1ObjectIdentifier QC_COMPLIANCE = QCStatement.id_etsi_qcs_QcCompliance;
    public static final ASN1ObjectIdentifier QC_SSCD = QCStatement.id_etsi_qcs_QcSSCD;
    public static final ASN1ObjectIdentifier LIMITVAL = QCStatement.id_etsi_qcs_LimiteValue;
    public static final ASN1ObjectIdentifier RETENTION_PERIOD = QCStatement.id_etsi_qcs_RetentionPeriod;
    public static final ASN1ObjectIdentifier PKI_DISCLOSURE = new ASN1ObjectIdentifier("0.4.0.1862.1.5");
    public static final ASN1ObjectIdentifier QC_TYPE = new ASN1ObjectIdentifier("0.4.0.1862.1.6");
    public static final ASN1ObjectIdentifier QC_TYPE_ELECTRONIC_SIGNATURE = new ASN1ObjectIdentifier("0.4.0.1862.1.6.1");
    public static final ASN1ObjectIdentifier QC_TYPE_ELECTRONIC_SEAL = new ASN1ObjectIdentifier("0.4.0.1862.1.6.2");
    public static final ASN1ObjectIdentifier QC_TYPE_WEBSITE_AUTH = new ASN1ObjectIdentifier("0.4.0.1862.1.6.3");
    public static final ASN1ObjectIdentifier QC_CC_LEGISLATION = new ASN1ObjectIdentifier("0.4.0.1862.1.7");
    public static final ASN1ObjectIdentifier ETSI_SEMANTICS_NATURAL = new ASN1ObjectIdentifier("0.4.0.194121.1.1");
    public static final ASN1ObjectIdentifier ETSI_SEMANTICS_LEGAL = new ASN1ObjectIdentifier("0.4.0.194121.1.2");
    public static final ASN1ObjectIdentifier ETSI_SEMANTICS_EIDAS_NATURAL = new ASN1ObjectIdentifier("0.4.0.194121.1.3");
    public static final ASN1ObjectIdentifier ETSI_SEMANTICS_EIDAS_LEGAL = new ASN1ObjectIdentifier("0.4.0.194121.1.4");


    @Getter @Setter private boolean pkixSyntaxV1;
    @Getter @Setter private boolean pkixSyntaxV2;
    @Getter @Setter private boolean qcCompliance;
    @Getter @Setter private boolean pdsStatement;
    @Getter @Setter private boolean qcSscd;
    @Getter @Setter private boolean qcType;
    @Getter @Setter private boolean retentionPeriod;
    @Getter @Setter private boolean limitValue;
    @Getter @Setter private boolean qcCClegislation;
    @Getter @Setter private MonetaryValue monetaryValue;
    @Getter @Setter private List<ASN1ObjectIdentifier> qcTypeIdList = new ArrayList<>();
    @Getter @Setter private BigInteger retentionPeriodVal;
    @Getter @Setter private List<PDSLocation> locationList = new ArrayList<>();
    @Getter @Setter private SemanticsInformation semanticsInfo;
    @Getter @Setter private List<String> legislationCountryList;

    public static QCStatements getInstance(ASN1TaggedObject obj, boolean explicit) {
        return getInstance(ASN1Sequence.getInstance(obj, explicit));
    }

    /**
     * Creates an instance of the QCStatements extension object
     *
     * @param obj a representation of the extension
     * @return QCStatements extension or null if no extension could be created from the provided object
     */
    public static QCStatements getInstance(Object obj) {
        if (obj instanceof QCStatements) {
            return (QCStatements) obj;
        }
        if (obj != null) {
            return new QCStatements(ASN1Sequence.getInstance(obj));
        }
        log.error("A null object was provided");
        return null;
    }

    /**
     * Creates an instance of the QCStatements extension object
     *
     * @param extensions Extension
     * @return QCStatemnts extension
     */
    public static QCStatements fromExtensions(Extensions extensions) {
        return QCStatements.getInstance(extensions.getExtensionParsedValue(OID));
    }


    /**
     * Internal constructor
     *
     * Parse the content of ASN1 sequence to populate set values
     *
     * @param seq
     */
    private QCStatements(ASN1Sequence seq) {

        try {
            for (int i = 0; i < seq.size(); i++) {
                ASN1Sequence statementSeq = ASN1Sequence.getInstance(seq.getObjectAt(i));
                setStatementVals(statementSeq);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad extension content");
        }
    }

    /**
     * Produce an object suitable for an ASN1OutputStream.
     * <pre>
     * AuthenticationContexts ::= SEQUENCE SIZE (1..MAX) OF
     *                            AuthenticationContext
     *
     * AuthenticationContext ::= SEQUENCE {
     *     contextType     UTF8String,
     *     contextInfo     UTF8String OPTIONAL
     * }
     * </pre>
     *
     * @return ASN.1 object of the extension
     */
    @Override
    public ASN1Primitive toASN1Primitive() {
        ASN1EncodableVector qcStatements = new ASN1EncodableVector();

        if (pkixSyntaxV1) {
            setSemanticsInfo(qcStatements, PKIX_SYNTAX_V1);
        }
        if (pkixSyntaxV2) {
            setSemanticsInfo(qcStatements, PKIX_SYNTAX_V2);
        }
        if (qcCompliance) {
            setStatementVal(qcStatements, QC_COMPLIANCE);
        }
        if (qcSscd) {
            setStatementVal(qcStatements, QC_SSCD);
        }
        if (qcType) {
            ASN1EncodableVector typeSeq = new ASN1EncodableVector();
            for (ASN1ObjectIdentifier type : qcTypeIdList) {
                typeSeq.add(type);
            }
            setStatementVal(qcStatements, QC_TYPE, new DERSequence(typeSeq));
        }
        if (limitValue) {
            ASN1EncodableVector limitSeq = new ASN1EncodableVector();
            limitSeq.add(new DERPrintableString(monetaryValue.getCurrency()));
            limitSeq.add(new ASN1Integer(monetaryValue.getAmount()));
            limitSeq.add(new ASN1Integer(monetaryValue.getExponent()));
            setStatementVal(qcStatements, LIMITVAL, new DERSequence(limitSeq));
        }
        if (retentionPeriod) {
            setStatementVal(qcStatements, RETENTION_PERIOD, new ASN1Integer(retentionPeriodVal));
        }
        if (pdsStatement) {
            ASN1EncodableVector pdsSeq = new ASN1EncodableVector();
            for (PDSLocation pdsLoc : locationList) {
                ASN1EncodableVector pdsLocSeq = new ASN1EncodableVector();
                pdsLocSeq.add(new DERIA5String(pdsLoc.getUrl()));
                pdsLocSeq.add(new DERPrintableString(pdsLoc.getLang()));
                pdsSeq.add(new DERSequence(pdsLocSeq));
            }
            setStatementVal(qcStatements, PKI_DISCLOSURE, new DERSequence(pdsSeq));
        }
        if (qcCClegislation) {
            ASN1EncodableVector countrySequence = new ASN1EncodableVector();
            for (String country: legislationCountryList){
                countrySequence.add(new DERPrintableString(country));
            }
            setStatementVal(qcStatements, QC_CC_LEGISLATION, new DERSequence(countrySequence));
        }

        return new DERSequence(qcStatements);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        //b.append("QCStatements [\n");
        if (pkixSyntaxV1) {
            b.append("  QC Syntax V1\n");
        }
        if (pkixSyntaxV2) {
            b.append("  QC Syntax V2\n");
        }
        if (pkixSyntaxV1 || pkixSyntaxV2) {
            if (semanticsInfo != null) {
                if (semanticsInfo.getSemanticsIdentifier() != null) {
                    b.append("    - SemanticsID: ").append(semanticsInfo.getSemanticsIdentifier().getId()).append("\n");
                }
                if (!semanticsInfo.getNameRegistrationAuthorityList().isEmpty()) {
                    semanticsInfo.getNameRegistrationAuthorityList().forEach((name) -> {
                        b.append("    - NameRegistrationAuthority: ").append(ExtensionUtils.getGeneralNameStr(name)).append("\n");
                    });
                }
            }
        }

        if (qcCompliance) {
            b.append("  QC Compliance\n");
        }
        if (qcSscd) {
            b.append("  QC SSCD\n");
        }
        if (qcType) {
            b.append("  QC Types\n");
            for (ASN1ObjectIdentifier type : qcTypeIdList) {
                if (type.getId().equalsIgnoreCase(QC_TYPE_ELECTRONIC_SIGNATURE.getId())) {
                    b.append("    - Electronic Signature\n");
                }
                if (type.getId().equalsIgnoreCase(QC_TYPE_ELECTRONIC_SEAL.getId())) {
                    b.append("    - Electronic Seal\n");
                }
                if (type.getId().equalsIgnoreCase(QC_TYPE_WEBSITE_AUTH.getId())) {
                    b.append("    - Website Authentication\n");
                }
            }
        }
        if (limitValue) {
            b.append("  Reliance Limit\n");
            b.append("    - Currency: ").append(monetaryValue.getCurrency()).append("\n");
            b.append("    - Amount: ").append(monetaryValue.getAmount()).append("\n");
            b.append("    - Exponent: ").append(monetaryValue.getExponent()).append("\n");
        }
        if (retentionPeriod) {
            b.append("  Retention Period\n");
            b.append("    - Years after cert expiry: ").append(retentionPeriodVal).append("\n");
        }
        if (pdsStatement) {
            b.append("  PKI Disclosure Statements\n");
            for (PDSLocation pdsLoc : locationList) {
                b.append("    Location\n");
                b.append("     - URL: ").append(pdsLoc.getUrl()).append("\n");
                b.append("     - Lang: ").append(pdsLoc.getLang()).append("\n");
            }
        }
        if (qcCClegislation) {
            b.append("  QC Legislation Countries\n");
            for (String country: legislationCountryList){
                b.append("    ").append(country).append("\n");
            }
        }
        //b.append("]\n");

        return b.toString();
    }

    /**
     * Clear all values
     */
    @SuppressWarnings("unused")
    private void clearAll() {
        setPkixSyntaxV1(false);
        setPkixSyntaxV2(false);
        setQcCompliance(false);
        setQcSscd(false);
        setQcType(false);
        setLimitValue(false);
        setRetentionPeriod(false);
        setPdsStatement(false);

    }

    private void setStatementVals(ASN1Sequence statementSeq) {
        try {
            String statementIdStr = ASN1ObjectIdentifier.getInstance(statementSeq.getObjectAt(0)).getId();
            if (statementIdStr.equals(PKIX_SYNTAX_V1.getId())) {
                setPkixSyntaxV1(true);
            }
            if (statementIdStr.equals(PKIX_SYNTAX_V2.getId())) {
                setPkixSyntaxV2(true);
            }
            if (statementIdStr.equals(PKIX_SYNTAX_V2.getId()) || statementIdStr.equals(PKIX_SYNTAX_V1.getId())) {
                if (statementSeq.size() > 1) {
                    ASN1Sequence siSeq = ASN1Sequence.getInstance(statementSeq.getObjectAt(1));
                    semanticsInfo = new SemanticsInformation();
                    for (int i = 0; i < siSeq.size(); i++) {
                        getSemanticsInfoVals(siSeq.getObjectAt(i));
                    }
                }
            }
            if (statementIdStr.equals(QC_COMPLIANCE.getId())) {
                setQcCompliance(true);
            }
            if (statementIdStr.equals(QC_SSCD.getId())) {
                setQcSscd(true);
            }
            if (statementIdStr.equals(QC_TYPE.getId())) {
                setQcType(true);
                ASN1Sequence qcTypeSequence = ASN1Sequence.getInstance(statementSeq.getObjectAt(1));
                qcTypeIdList = new ArrayList<>();
                for (int i = 0; i < qcTypeSequence.size(); i++) {
                    ASN1ObjectIdentifier type = ASN1ObjectIdentifier.getInstance(qcTypeSequence.getObjectAt(i));
                    qcTypeIdList.add(type);
                }
            }
            if (statementIdStr.equals(LIMITVAL.getId())) {
                setLimitValue(true);
                ASN1Sequence lvSequence = ASN1Sequence.getInstance(statementSeq.getObjectAt(1));
                ASN1Encodable currencyEnc = lvSequence.getObjectAt(0);
                String currency = currencyEnc instanceof DERPrintableString ? DERPrintableString.getInstance(currencyEnc).getString() : ASN1Integer
                  .getInstance(currencyEnc).getValue().toString();
                BigInteger amount = ASN1Integer.getInstance(lvSequence.getObjectAt(1)).getValue();
                BigInteger exp = ASN1Integer.getInstance(lvSequence.getObjectAt(2)).getValue();
                monetaryValue = new MonetaryValue(currency, amount, exp);
            }
            if (statementIdStr.equals(RETENTION_PERIOD.getId())) {
                setRetentionPeriod(true);
                retentionPeriodVal = ASN1Integer.getInstance(statementSeq.getObjectAt(1)).getValue();
            }
            if (statementIdStr.equals(PKI_DISCLOSURE.getId())) {
                setPdsStatement(true);
                ASN1Sequence pdsSequence = ASN1Sequence.getInstance(statementSeq.getObjectAt(1));
                locationList = new ArrayList<>();
                for (int i = 0; i < pdsSequence.size(); i++) {
                    ASN1Sequence locationSeq = ASN1Sequence.getInstance(pdsSequence.getObjectAt(i));
                    String url = DERIA5String.getInstance(locationSeq.getObjectAt(0)).getString();
                    String lang = DERPrintableString.getInstance(locationSeq.getObjectAt(1)).getString();
                    locationList.add(new PDSLocation(lang, url));
                }
            }
            if (statementIdStr.equals(QC_CC_LEGISLATION.getId())) {
                setQcCClegislation(true);
                ASN1Sequence qcLegislationSeq = ASN1Sequence.getInstance(statementSeq.getObjectAt(1));
                legislationCountryList = new ArrayList<>();
                for (int i = 0; i < qcLegislationSeq.size(); i++) {
                    String country = DERPrintableString.getInstance(qcLegislationSeq.getObjectAt(i)).getString();
                    legislationCountryList.add(country);
                }
            }

        } catch (Exception e) {
        }
    }

    private void setStatementVal(ASN1EncodableVector qcStatementsSeq, ASN1ObjectIdentifier statementId) {
        setStatementVal(qcStatementsSeq, statementId, null);
    }

    private void setStatementVal(ASN1EncodableVector qcStatementsSeq, ASN1ObjectIdentifier statementId, ASN1Encodable value) {
        ASN1EncodableVector statement = new ASN1EncodableVector();
        statement.add(statementId);
        if (value != null) {
            statement.add(value);
        }
        qcStatementsSeq.add(new DERSequence(statement));
    }

    private void setSemanticsInfo(ASN1EncodableVector qcStatements, ASN1ObjectIdentifier syntaxVersion) {
        if (semanticsInfo == null) {
            setStatementVal(qcStatements, syntaxVersion);
            return;
        }
        ASN1EncodableVector siSeq = new ASN1EncodableVector();
        if (semanticsInfo.getSemanticsIdentifier() != null) {
            siSeq.add(semanticsInfo.getSemanticsIdentifier());
        }
        List<GeneralName> nameRegistrationAuthorityList = semanticsInfo.getNameRegistrationAuthorityList();
        if (!nameRegistrationAuthorityList.isEmpty()) {
            ASN1EncodableVector nraSeq = new ASN1EncodableVector();
            nameRegistrationAuthorityList.forEach((name) -> {
                nraSeq.add(name);
            });
            siSeq.add(new DERSequence(nraSeq));
        }
        setStatementVal(qcStatements, syntaxVersion, new DERSequence(siSeq));
    }

    private void getSemanticsInfoVals(ASN1Encodable siObject) {
        if (siObject instanceof ASN1ObjectIdentifier) {
            semanticsInfo.setSemanticsIdentifier((ASN1ObjectIdentifier.getInstance(siObject)));
        }
        if (siObject instanceof ASN1Sequence) {
            ASN1Sequence nraSeq = ASN1Sequence.getInstance(siObject);
            List<GeneralName> nameList = new ArrayList<>();
            for (int i = 0; i < nraSeq.size(); i++) {
                try {
                    nameList.add(GeneralName.getInstance(nraSeq.getObjectAt(i)));
                } catch (Exception e) {
                }
            }
            semanticsInfo.setNameRegistrationAuthorityList(nameList);
        }
    }
}
