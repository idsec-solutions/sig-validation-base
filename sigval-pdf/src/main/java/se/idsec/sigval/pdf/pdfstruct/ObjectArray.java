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

package se.idsec.sigval.pdf.pdfstruct;

import lombok.Getter;
import org.apache.pdfbox.cos.COSArray;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class ObjectArray {

  List<ObjectValue> values;

  public ObjectArray(COSArray cosArray) {
    values = cosArray.toList().stream()
      .map(cosBase -> new ObjectValue(cosBase))
      .collect(Collectors.toList());
  }

  public boolean matches(Object matchArray) {
    if(!(matchArray instanceof ObjectArray)) return false;
    List<ObjectValue> matchArrayValues = ((ObjectArray)matchArray).getValues();
    if (this.values.size() != matchArrayValues.size()){
      return false;
    }
    for (int i = 0; i< this.values.size() ; i++){
      if (!this.values.get(i).matches(matchArrayValues.get(i))){
        return false;
      }
    }
    return true;
  }

}
