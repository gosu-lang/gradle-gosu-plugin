/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gosulang.gradle.tasks.compile.incremental.deps;


import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class IntSetSerializer implements Serializer<Set<Integer>> {
  public static final IntSetSerializer INSTANCE = new IntSetSerializer();

  private IntSetSerializer() {
  }

  @Override
  public Set<Integer> read(Decoder decoder) throws Exception {
    int size = decoder.readInt();
    if (size == 0) {
      return Collections.emptySet();
    }
    Set<Integer> result = new HashSet<>(size);
    for (int i = 0; i < size; i++) {
      result.add(decoder.readInt());
    }
    return Collections.unmodifiableSet(result);
  }

  @Override
  public void write(Encoder encoder, Set<Integer> value) throws Exception {
    encoder.writeInt(value.size());
    for (Integer integer : value) {
      encoder.writeInt(integer);
    }
  }
}
