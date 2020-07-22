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
package se.idsec.sigval.cert.extensions.missing;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.asn1.x509.qualified.BiometricData;
import org.bouncycastle.asn1.x509.qualified.TypeOfBiometricData;
import org.bouncycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
public class BiometricInfo extends ASN1Object {

    public static final ASN1ObjectIdentifier OID = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.2");
    List<BiometricData> biometricDataList = new ArrayList<>();

    public static BiometricInfo getInstance(ASN1TaggedObject obj, boolean explicit) {
        return getInstance(ASN1Sequence.getInstance(obj, explicit));
    }

    public static BiometricInfo getInstance(Object obj) {
        if (obj instanceof BiometricInfo) {
            return (BiometricInfo) obj;
        }
        if (obj instanceof X509Extension) {
            return getInstance(X509Extension.convertValueToObject((X509Extension) obj));
        }
        if (obj != null) {
            return new BiometricInfo(ASN1Sequence.getInstance(obj));
        }

        return null;
    }

    public static BiometricInfo fromExtensions(Extensions extensions) {
        return BiometricInfo.getInstance(extensions.getExtensionParsedValue(OID));
    }

    /**
     * Parse the content of ASN1 sequence to populate set values
     *
     * @param seq
     */
    private BiometricInfo(ASN1Sequence seq) {
        this.biometricDataList = new ArrayList<>();
        try {
            for (int i = 0; i < seq.size(); i++) {
                BiometricData biometricData = BiometricData.getInstance(seq.getObjectAt(i));
                biometricDataList.add(biometricData);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad extension content");
        }
    }

    public BiometricInfo(List<BiometricData> biometricDataList) {
        this.biometricDataList = biometricDataList;
    }

    public List<BiometricData> getBiometricDataList() {
        return biometricDataList;
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
     * @return
     */
    @Override
    public ASN1Primitive toASN1Primitive() {
        ASN1EncodableVector biometricInfo = new ASN1EncodableVector();
        for (BiometricData bd: biometricDataList){
            biometricInfo.add(bd.toASN1Primitive());
        }
        return new DERSequence(biometricInfo);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("Biometric Data:\n");
        for (BiometricData biometricData : biometricDataList) {
            b.append("    Data Type: ").append(getTypeString(biometricData.getTypeOfBiometricData())).append("\n");
            b.append("    Hash algorithm: ").append(biometricData.getHashAlgorithm().getAlgorithm().getId()).append("\n");
            b.append("    Biometric hash: ").append(Hex.toHexString(biometricData.getBiometricDataHash().getOctets())).append("\n");
            if (biometricData.getSourceDataUri()!=null){
                b.append("    Source URI: ").append(biometricData.getSourceDataUri().getString()).append("\n");
            }
        }
        return b.toString();
    }

    public static String getTypeString(TypeOfBiometricData typeOfBiometricData){
        if (typeOfBiometricData.isPredefined()){
            switch (typeOfBiometricData.getPredefinedBiometricType()){
            case 0:
                return "Picture";
            case 1:
                return "Handwritten signature";
            default:
                 return String.valueOf(typeOfBiometricData.getPredefinedBiometricType());
            }
        }
        return typeOfBiometricData.getBiometricDataOid().getId();
    }
}
