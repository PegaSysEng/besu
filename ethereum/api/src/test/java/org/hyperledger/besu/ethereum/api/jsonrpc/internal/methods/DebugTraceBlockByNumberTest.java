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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;

import org.junit.Test;

public class DebugTraceBlockByNumberTest {

  private final JsonRpcParameter parameters = new JsonRpcParameter();
  private final BlockchainQueries blockchain = mock(BlockchainQueries.class);
  private final BlockTracer blockTracer = mock(BlockTracer.class);
  private final DebugTraceBlockByNumber debugTraceBlockByNumber =
      new DebugTraceBlockByNumber(parameters, blockTracer, blockchain);

  private final Hash blockHash =
      Hash.fromHexString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

  @Test
  public void nameShouldBeDebugTraceBlockByNumber() {
    assertThat(debugTraceBlockByNumber.getName()).isEqualTo("debug_traceBlockByNumber");
  }

  @Test
  public void shouldReturnCorrectResponse() {
    final long blockNumber = 1L;
    final Object[] params = new Object[] {Long.toHexString(blockNumber)};
    final JsonRpcRequest request = new JsonRpcRequest("2.0", "debug_traceBlockByNumber", params);

    final TraceFrame traceFrame =
        new TraceFrame(
            12,
            "NONE",
            Gas.of(45),
            Optional.of(Gas.of(56)),
            2,
            EnumSet.noneOf(ExceptionalHaltReason.class),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    final TransactionProcessor.Result transaction1Result = mock(TransactionProcessor.Result.class);
    final TransactionProcessor.Result transaction2Result = mock(TransactionProcessor.Result.class);

    final TransactionTrace transaction1Trace = mock(TransactionTrace.class);
    final TransactionTrace transaction2Trace = mock(TransactionTrace.class);

    final BlockTrace blockTrace = new BlockTrace(asList(transaction1Trace, transaction2Trace));

    when(transaction1Trace.getTraceFrames()).thenReturn(singletonList(traceFrame));
    when(transaction2Trace.getTraceFrames()).thenReturn(singletonList(traceFrame));
    when(transaction1Trace.getResult()).thenReturn(transaction1Result);
    when(transaction2Trace.getResult()).thenReturn(transaction2Result);
    when(transaction1Result.getOutput()).thenReturn(BytesValue.fromHexString("1234"));
    when(transaction2Result.getOutput()).thenReturn(BytesValue.fromHexString("1234"));
    when(blockchain.getBlockHashByNumber(blockNumber)).thenReturn(Optional.of(blockHash));
    when(blockTracer.trace(eq(blockHash), any())).thenReturn(Optional.of(blockTrace));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) debugTraceBlockByNumber.response(request);
    final Collection<DebugTraceTransactionResult> result = getResult(response);
    assertThat(result)
        .usingFieldByFieldElementComparator()
        .isEqualTo(DebugTraceTransactionResult.of(blockTrace.getTransactionTraces()));
  }

  @SuppressWarnings("unchecked")
  private Collection<DebugTraceTransactionResult> getResult(final JsonRpcSuccessResponse response) {
    return (Collection<DebugTraceTransactionResult>) response.getResult();
  }
}
