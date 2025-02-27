/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.perm;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.web3j.utils.Numeric.toHexString;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.permissioning.NodeSmartContractPermissioningController;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.io.IOException;
import java.math.BigInteger;

import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;

public class NodeSmartContractPermissioningAllowNodeTransaction implements Transaction<Hash> {

  private static final BytesValue ADD_ENODE_SIGNATURE =
      org.hyperledger.besu.crypto.Hash.keccak256(
              BytesValue.of("addEnode(bytes32,bytes32,bytes16,uint16)".getBytes(UTF_8)))
          .slice(0, 4);

  private final Account sender;
  private final Address contractAddress;
  private final Node node;

  public NodeSmartContractPermissioningAllowNodeTransaction(
      final Account sender, final Address contractAddress, final Node node) {
    this.sender = sender;
    this.contractAddress = contractAddress;
    this.node = node;
  }

  @Override
  public Hash execute(final NodeRequests node) {
    final String signedTransactionData = signedTransactionData();
    try {
      String hash =
          node.eth().ethSendRawTransaction(signedTransactionData).send().getTransactionHash();
      return Hash.fromHexString(hash);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String signedTransactionData() {
    final String enodeURL = ((RunnableNode) node).enodeUrl().toASCIIString();
    final BytesValue payload =
        NodeSmartContractPermissioningController.createPayload(
            ADD_ENODE_SIGNATURE, EnodeURL.fromString(enodeURL));

    RawTransaction transaction =
        RawTransaction.createTransaction(
            sender.getNextNonce(),
            BigInteger.valueOf(1000),
            BigInteger.valueOf(100_000),
            contractAddress.toString(),
            payload.toString());

    return toHexString(TransactionEncoder.signMessage(transaction, sender.web3jCredentials()));
  }
}
