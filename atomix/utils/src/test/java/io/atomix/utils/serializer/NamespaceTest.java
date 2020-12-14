/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.utils.serializer;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import org.junit.Test;

public class NamespaceTest {

  private final Namespace ns = new Namespace.Builder().register(Integer.class).build();

  @Test
  public void shouldDeserializeObject() {
    // given
    final Integer want = 99;

    // when
    final byte[] ser = ns.serialize(want);
    final Object got = ns.deserialize(ser);

    // then
    assertEquals(want, got);
  }

  @Test
  public void shouldDeserializeObjectWithBuffer() {
    // given
    final Integer want = 99;

    // when
    final ByteBuffer buffer = ByteBuffer.allocate(4);
    ns.serialize(want, buffer);
    buffer.flip();
    final Object got = ns.deserialize(buffer);

    // then
    assertEquals(want, got);
  }
}
