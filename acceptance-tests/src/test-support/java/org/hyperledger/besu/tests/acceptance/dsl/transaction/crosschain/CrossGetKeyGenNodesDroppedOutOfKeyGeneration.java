/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.tests.acceptance.dsl.transaction.crosschain;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;

import org.web3j.protocol.besu.response.crosschain.KeyGenFailureToCompleteReason;
import org.web3j.protocol.besu.response.crosschain.KeyGenNodesDroppedOutOfKeyGenerationResponse;

public class CrossGetKeyGenNodesDroppedOutOfKeyGeneration
    implements Transaction<Map<BigInteger, KeyGenFailureToCompleteReason>> {
  private final long keyVersion;

  CrossGetKeyGenNodesDroppedOutOfKeyGeneration(final long keyVersion) {
    this.keyVersion = keyVersion;
  }

  @Override
  public Map<BigInteger, KeyGenFailureToCompleteReason> execute(final NodeRequests node) {
    try {
      final KeyGenNodesDroppedOutOfKeyGenerationResponse result =
          node.eth().crossGetKeyGenNodesDroppedOutOfKeyGeneration(keyVersion).send();
      assertThat(result.hasError()).isFalse();
      assertThat(result).isNotNull();
      return result.getResult();

    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
