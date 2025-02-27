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
pragma solidity >=0.4.0 <0.6.0;
import "./NonLockableCtrtInt.sol";

contract LockableCtrt is NonLockableCtrtInt {
    uint256 public flag;

    constructor() public {
        flag = 0;
    }

    function viewfn() external view returns (uint256) {
        return 1;
    }

    function updateState() external {
        flag = 1;
    }
}