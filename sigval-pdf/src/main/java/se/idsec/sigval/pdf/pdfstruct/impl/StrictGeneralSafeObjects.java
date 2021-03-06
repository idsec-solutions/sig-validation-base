/*
 * Copyright (c) 2021. IDsec Solutions AB
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

package se.idsec.sigval.pdf.pdfstruct.impl;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import se.idsec.sigval.pdf.pdfstruct.*;

import java.util.Arrays;
import java.util.List;

@Slf4j
@NoArgsConstructor
public class StrictGeneralSafeObjects implements GeneralSafeObjects {

  @Override public void addGeneralSafeObjects(PDFDocRevision revData) {
    // Adding no safe objects
  }

}
