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
package org.hyperledger.besu.ethereum.eth.sync.worldstate;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;
import org.hyperledger.besu.services.tasks.Task;

import java.util.List;

public class PersistDataStep {
  private final WorldStateStorage worldStateStorage;

  public PersistDataStep(final WorldStateStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
  }

  public List<Task<NodeDataRequest>> persist(
      final List<Task<NodeDataRequest>> tasks,
      final BlockHeader blockHeader,
      final WorldDownloadState downloadState) {
    final Updater updater = worldStateStorage.updater();
    tasks.stream()
        .map(Task::getData)
        .filter(request -> request.getData() != null)
        .forEach(
            request -> {
              if (isRootState(blockHeader, request)) {
                downloadState.setRootNodeData(request.getData());
              } else {
                request.persist(updater);
              }
            });
    updater.commit();
    return tasks;
  }

  private boolean isRootState(final BlockHeader blockHeader, final NodeDataRequest request) {
    return request.getHash().equals(blockHeader.getStateRoot());
  }
}
