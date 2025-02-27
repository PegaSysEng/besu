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
package org.hyperledger.besu.cli;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hyperledger.besu.cli.DefaultCommandValues.getDefaultBesuDataPath;
import static org.hyperledger.besu.cli.config.NetworkName.MAINNET;
import static org.hyperledger.besu.controller.BesuController.DATABASE_PATH;
import static org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration.DEFAULT_GRAPHQL_HTTP_PORT;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration.DEFAULT_JSON_RPC_PORT;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.DEFAULT_JSON_RPC_APIS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration.DEFAULT_WEBSOCKET_PORT;
import static org.hyperledger.besu.metrics.BesuMetricCategory.DEFAULT_METRIC_CATEGORIES;
import static org.hyperledger.besu.metrics.prometheus.MetricsConfiguration.DEFAULT_METRICS_PORT;
import static org.hyperledger.besu.metrics.prometheus.MetricsConfiguration.DEFAULT_METRICS_PUSH_PORT;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.Runner;
import org.hyperledger.besu.RunnerBuilder;
import org.hyperledger.besu.chainimport.RlpBlockImporter;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.cli.converter.MetricCategoryConverter;
import org.hyperledger.besu.cli.converter.PercentageConverter;
import org.hyperledger.besu.cli.converter.RpcApisConverter;
import org.hyperledger.besu.cli.custom.CorsAllowedOriginsProperty;
import org.hyperledger.besu.cli.custom.JsonRPCWhitelistHostsProperty;
import org.hyperledger.besu.cli.custom.RpcAuthFileValidator;
import org.hyperledger.besu.cli.error.BesuExceptionHandler;
import org.hyperledger.besu.cli.options.EthProtocolOptions;
import org.hyperledger.besu.cli.options.MetricsCLIOptions;
import org.hyperledger.besu.cli.options.NetworkingOptions;
import org.hyperledger.besu.cli.options.SynchronizerOptions;
import org.hyperledger.besu.cli.options.TransactionPoolOptions;
import org.hyperledger.besu.cli.subcommands.PasswordSubCommand;
import org.hyperledger.besu.cli.subcommands.PublicKeySubCommand;
import org.hyperledger.besu.cli.subcommands.PublicKeySubCommand.KeyLoader;
import org.hyperledger.besu.cli.subcommands.RetestethSubCommand;
import org.hyperledger.besu.cli.subcommands.blocks.BlocksSubCommand;
import org.hyperledger.besu.cli.subcommands.blocks.BlocksSubCommand.JsonBlockImporterFactory;
import org.hyperledger.besu.cli.subcommands.blocks.BlocksSubCommand.RlpBlockExporterFactory;
import org.hyperledger.besu.cli.subcommands.operator.OperatorSubCommand;
import org.hyperledger.besu.cli.subcommands.rlp.RLPSubCommand;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.cli.util.ConfigOptionSearchAndRunHandler;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.config.CrosschainConfigOptions;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.controller.BesuControllerBuilder;
import org.hyperledger.besu.controller.KeyPairUtil;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.p2p.peers.StaticNodesParser;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfigurationBuilder;
import org.hyperledger.besu.ethereum.permissioning.SmartContractPermissioningConfiguration;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.worldstate.PruningConfiguration;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.metrics.vertx.VertxMetricsAdapterFactory;
import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBPlugin;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.services.BesuEventsImpl;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.services.PicoCLIOptionsImpl;
import org.hyperledger.besu.services.StorageServiceImpl;
import org.hyperledger.besu.util.NetworkUtility;
import org.hyperledger.besu.util.PermissioningConfigurationValidator;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.number.Fraction;
import org.hyperledger.besu.util.number.PositiveNumber;
import org.hyperledger.besu.util.uint.UInt256;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.metrics.MetricsOptions;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import picocli.CommandLine.AbstractParseResultHandler;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

@SuppressWarnings("FieldCanBeLocal") // because Picocli injected fields report false positives
@Command(
    description = "This command runs the Besu Ethereum client full node.",
    abbreviateSynopsis = true,
    name = "besu",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    header = "Usage:",
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Besu is licensed under the Apache License 2.0")
public class BesuCommand implements DefaultCommandValues, Runnable {

  private final Logger logger;

  private CommandLine commandLine;

  private final RlpBlockImporter rlpBlockImporter;
  private final JsonBlockImporterFactory jsonBlockImporterFactory;
  private final RlpBlockExporterFactory rlpBlockExporterFactory;

  final NetworkingOptions networkingOptions = NetworkingOptions.create();
  final SynchronizerOptions synchronizerOptions = SynchronizerOptions.create();
  final EthProtocolOptions ethProtocolOptions = EthProtocolOptions.create();
  final MetricsCLIOptions metricsCLIOptions = MetricsCLIOptions.create();
  final TransactionPoolOptions transactionPoolOptions = TransactionPoolOptions.create();
  private final RunnerBuilder runnerBuilder;
  private final BesuController.Builder controllerBuilderFactory;
  private final BesuPluginContextImpl besuPluginContext;
  private final StorageServiceImpl storageService;
  private final Map<String, String> environment;

  protected KeyLoader getKeyLoader() {
    return KeyPairUtil::loadKeyPair;
  }

  // Public IP stored to prevent having to research it each time we need it.
  private InetAddress autoDiscoveredDefaultIP = null;

  // Property to indicate whether Besu has been launched via docker
  private final boolean isDocker = Boolean.getBoolean("besu.docker");

  // CLI options defined by user at runtime.
  // Options parsing is done with CLI library Picocli https://picocli.info/

  // Completely disables P2P within Besu.
  @Option(
      names = {"--p2p-enabled"},
      description = "Enable P2P functionality (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Boolean p2pEnabled = true;

  // Boolean option to indicate if peers should NOT be discovered, default to
  // false indicates that
  // the peers should be discovered by default.
  //
  // This negative option is required because of the nature of the option that is
  // true when
  // added on the command line. You can't do --option=false, so false is set as
  // default
  // and you have not to set the option at all if you want it false.
  // This seems to be the only way it works with Picocli.
  // Also many other software use the same negative option scheme for false
  // defaults
  // meaning that it's probably the right way to handle disabling options.
  @Option(
      names = {"--discovery-enabled"},
      description = "Enable P2P peer discovery (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Boolean peerDiscoveryEnabled = true;

  // A list of bootstrap nodes can be passed
  // and a hardcoded list will be used otherwise by the Runner.
  // NOTE: we have no control over default value here.
  @Option(
      names = {"--bootnodes"},
      paramLabel = "<enode://id@host:port>",
      description =
          "Comma separated enode URLs for P2P discovery bootstrap. "
              + "Default is a predefined list.",
      split = ",",
      arity = "0..*")
  void setBootnodes(final List<String> values) {
    try {
      bootNodes =
          values.stream()
              .filter(value -> !value.isEmpty())
              .map(EnodeURL::fromString)
              .collect(Collectors.toList());
      DiscoveryConfiguration.assertValidBootnodes(bootNodes);
    } catch (final IllegalArgumentException e) {
      throw new ParameterException(commandLine, e.getMessage());
    }
  }

  private List<EnodeURL> bootNodes = null;

  @Option(
      names = {"--max-peers"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Maximum P2P peer connections that can be established (default: ${DEFAULT-VALUE})")
  private final Integer maxPeers = DEFAULT_MAX_PEERS;

  @Option(
      names = {"--remote-connections-limit-enabled"},
      description =
          "Whether to limit the number of P2P connections initiated remotely. (default: ${DEFAULT-VALUE})")
  private final Boolean isLimitRemoteWireConnectionsEnabled = true;

  @Option(
      names = {"--remote-connections-max-percentage"},
      paramLabel = MANDATORY_DOUBLE_FORMAT_HELP,
      description =
          "The maximum percentage of P2P connections that can be initiated remotely. Must be between 0 and 100 inclusive. (default: ${DEFAULT-VALUE})",
      arity = "1",
      converter = PercentageConverter.class)
  private final Integer maxRemoteConnectionsPercentage =
      Fraction.fromFloat(DEFAULT_FRACTION_REMOTE_WIRE_CONNECTIONS_ALLOWED)
          .toPercentage()
          .getValue();

  @Option(
      names = {"--banned-node-ids", "--banned-node-id"},
      paramLabel = MANDATORY_NODE_ID_FORMAT_HELP,
      description = "A list of node IDs to ban from the P2P network.",
      split = ",",
      arity = "1..*")
  void setBannedNodeIds(final List<String> values) {
    try {
      bannedNodeIds =
          values.stream()
              .filter(value -> !value.isEmpty())
              .map(EnodeURL::parseNodeId)
              .collect(Collectors.toList());
    } catch (final IllegalArgumentException e) {
      throw new ParameterException(
          commandLine, "Invalid ids supplied to '--banned-node-ids'. " + e.getMessage());
    }
  }

  private Collection<BytesValue> bannedNodeIds = new ArrayList<>();

  @Option(
      names = {"--sync-mode"},
      paramLabel = MANDATORY_MODE_FORMAT_HELP,
      description =
          "Synchronization mode, possible values are ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
  private final SyncMode syncMode = DEFAULT_SYNC_MODE;

  @Option(
      names = {"--fast-sync-min-peers"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Minimum number of peers required before starting fast sync. (default: ${DEFAULT-VALUE})")
  private final Integer fastSyncMinPeerCount = FAST_SYNC_MIN_PEER_COUNT;

  @Option(
      names = {"--network"},
      paramLabel = MANDATORY_NETWORK_FORMAT_HELP,
      description =
          "Synchronize against the indicated network, possible values are ${COMPLETION-CANDIDATES}."
              + " (default: MAINNET)")
  private final NetworkName network = null;

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
  @Option(
      names = {"--p2p-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Ip address this node advertises to its peers (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String p2pHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--p2p-interface"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description =
          "The network interface address on which this node listens for p2p communication (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String p2pInterface = NetworkUtility.INADDR_ANY;

  @Option(
      names = {"--p2p-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port on which to listen for p2p communication (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer p2pPort = EnodeURL.DEFAULT_LISTENING_PORT;

  @Option(
      names = {"--nat-method"},
      description =
          "Specify the NAT circumvention method to be used, possible values are ${COMPLETION-CANDIDATES}."
              + " NONE disables NAT functionality. (default: ${DEFAULT-VALUE})")
  private final NatMethod natMethod = DEFAULT_NAT_METHOD;

  @Option(
      names = {"--network-id"},
      paramLabel = "<BIG INTEGER>",
      description =
          "P2P network identifier. (default: the selected network chain ID or custom genesis chain ID)",
      arity = "1")
  private final BigInteger networkId = null;

  @Option(
      names = {"--graphql-http-enabled"},
      description = "Set to start the GraphQL HTTP service (default: ${DEFAULT-VALUE})")
  private final Boolean isGraphQLHttpEnabled = false;

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
  @Option(
      names = {"--graphql-http-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host for GraphQL HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String graphQLHttpHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--graphql-http-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port for GraphQL HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer graphQLHttpPort = DEFAULT_GRAPHQL_HTTP_PORT;

  @Option(
      names = {"--graphql-http-cors-origins"},
      description = "Comma separated origin domain URLs for CORS validation (default: none)")
  private final CorsAllowedOriginsProperty graphQLHttpCorsAllowedOrigins =
      new CorsAllowedOriginsProperty();

  @Option(
      names = {"--rpc-http-enabled"},
      description = "Set to start the JSON-RPC HTTP service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcHttpEnabled = false;

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
  @Option(
      names = {"--rpc-http-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host for JSON-RPC HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String rpcHttpHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--rpc-http-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port for JSON-RPC HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer rpcHttpPort = DEFAULT_JSON_RPC_PORT;

  // A list of origins URLs that are accepted by the JsonRpcHttpServer (CORS)
  @Option(
      names = {"--rpc-http-cors-origins"},
      description = "Comma separated origin domain URLs for CORS validation (default: none)")
  private final CorsAllowedOriginsProperty rpcHttpCorsAllowedOrigins =
      new CorsAllowedOriginsProperty();

  @Option(
      names = {"--rpc-http-api", "--rpc-http-apis"},
      paramLabel = "<api name>",
      split = ",",
      arity = "1..*",
      converter = RpcApisConverter.class,
      description =
          "Comma separated list of APIs to enable on JSON-RPC HTTP service (default: ${DEFAULT-VALUE})")
  private final Collection<RpcApi> rpcHttpApis = DEFAULT_JSON_RPC_APIS;

  @Option(
      names = {"--rpc-http-authentication-enabled"},
      description =
          "Require authentication for the JSON-RPC HTTP service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcHttpAuthenticationEnabled = false;

  @Option(
      names = {"--rpc-ws-enabled"},
      description = "Set to start the JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcWsEnabled = false;

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
  @Option(
      names = {"--rpc-ws-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host for JSON-RPC WebSocket service to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String rpcWsHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--rpc-ws-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port for JSON-RPC WebSocket service to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer rpcWsPort = DEFAULT_WEBSOCKET_PORT;

  @Option(
      names = {"--rpc-ws-api", "--rpc-ws-apis"},
      paramLabel = "<api name>",
      split = ",",
      arity = "1..*",
      converter = RpcApisConverter.class,
      description =
          "Comma separated list of APIs to enable on JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
  private final List<RpcApi> rpcWsApis = DEFAULT_JSON_RPC_APIS;

  @Option(
      names = {"--rpc-ws-authentication-enabled"},
      description =
          "Require authentication for the JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcWsAuthenticationEnabled = false;

  @Option(
      names = {"--metrics-enabled"},
      description = "Set to start the metrics exporter (default: ${DEFAULT-VALUE})")
  private final Boolean isMetricsEnabled = false;

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
  @Option(
      names = {"--metrics-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host for the metrics exporter to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String metricsHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--metrics-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port for the metrics exporter to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer metricsPort = DEFAULT_METRICS_PORT;

  @Option(
      names = {"--metrics-category", "--metrics-categories"},
      paramLabel = "<category name>",
      split = ",",
      arity = "1..*",
      description =
          "Comma separated list of categories to track metrics for (default: ${DEFAULT-VALUE})")
  private final Set<MetricCategory> metricCategories = DEFAULT_METRIC_CATEGORIES;

  @Option(
      names = {"--metrics-push-enabled"},
      description = "Enable the metrics push gateway integration (default: ${DEFAULT-VALUE})")
  private final Boolean isMetricsPushEnabled = false;

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
  @Option(
      names = {"--metrics-push-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host of the Prometheus Push Gateway for push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String metricsPushHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--metrics-push-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port of the Prometheus Push Gateway for push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer metricsPushPort = DEFAULT_METRICS_PUSH_PORT;

  @Option(
      names = {"--metrics-push-interval"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Interval in seconds to push metrics when in push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer metricsPushInterval = 15;

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
  @Option(
      names = {"--metrics-push-prometheus-job"},
      description = "Job name to use when in push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String metricsPrometheusJob = "besu-client";

  @Option(
      names = {"--host-whitelist"},
      paramLabel = "<hostname>[,<hostname>...]... or * or all",
      description =
          "Comma separated list of hostnames to whitelist for RPC access, or * to accept any host (default: ${DEFAULT-VALUE})",
      defaultValue = "localhost,127.0.0.1")
  private final JsonRPCWhitelistHostsProperty hostsWhitelist = new JsonRPCWhitelistHostsProperty();

  @Option(
      names = {"--logging", "-l"},
      paramLabel = "<LOG VERBOSITY LEVEL>",
      description =
          "Logging verbosity levels: OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL (default: ${DEFAULT-VALUE})")
  private final Level logLevel = Level.INFO;

  @Option(
      names = {"--miner-enabled"},
      description = "Set if node will perform mining (default: ${DEFAULT-VALUE})")
  private final Boolean isMiningEnabled = false;

  @Option(
      names = {"--miner-coinbase"},
      description =
          "Account to which mining rewards are paid. You must specify a valid coinbase if "
              + "mining is enabled using --miner-enabled option",
      arity = "1")
  private final Address coinbase = null;

  @Option(
      names = {"--min-gas-price"},
      description =
          "Minimum price (in Wei) offered by a transaction for it to be included in a mined "
              + "block (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Wei minTransactionGasPrice = DEFAULT_MIN_TRANSACTION_GAS_PRICE;

  @Option(
      names = {"--miner-extra-data"},
      description =
          "A hex string representing the (32) bytes to be included in the extra data "
              + "field of a mined block (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final BytesValue extraData = DEFAULT_EXTRA_DATA;

  @Option(
      names = {"--pruning-enabled"},
      hidden = true,
      description =
          "Enable pruning of world state of blocks older than the retention period (default: ${DEFAULT-VALUE})")
  private final Boolean isPruningEnabled = false;

  @Option(
      names = {"--pruning-blocks-retained"},
      hidden = true,
      description =
          "Number of recent blocks for which to keep entire world state (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Long pruningBlocksRetained = DEFAULT_PRUNING_BLOCKS_RETAINED;

  @Option(
      names = {"--pruning-block-confirmations"},
      hidden = true,
      description =
          "Number of confirmations on a block before marking begins (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Long pruningBlockConfirmations = DEFAULT_PRUNING_BLOCK_CONFIRMATIONS;

  @Option(
      names = {"--permissions-nodes-config-file-enabled"},
      description = "Enable node level permissions (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsNodesEnabled = false;

  @Option(
      names = {"--permissions-accounts-config-file-enabled"},
      description = "Enable account level permissions (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsAccountsEnabled = false;

  @Option(
      names = {"--permissions-nodes-contract-address"},
      description = "Address of the node permissioning smart contract",
      arity = "1")
  private final Address permissionsNodesContractAddress = null;

  @Option(
      names = {"--permissions-nodes-contract-enabled"},
      description = "Enable node level permissions via smart contract (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsNodesContractEnabled = false;

  @Option(
      names = {"--permissions-accounts-contract-address"},
      description = "Address of the account permissioning smart contract",
      arity = "1")
  private final Address permissionsAccountsContractAddress = null;

  @Option(
      names = {"--permissions-accounts-contract-enabled"},
      description =
          "Enable account level permissions via smart contract (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsAccountsContractEnabled = false;

  @Option(
      names = {"--privacy-enabled"},
      description = "Enable private transactions (default: ${DEFAULT-VALUE})")
  private final Boolean isPrivacyEnabled = false;

  @Option(
      names = {"--revert-reason-enabled"},
      description =
          "Enable passing the revert reason back through TransactionReceipts (default: ${DEFAULT-VALUE})")
  private final Boolean isRevertReasonEnabled = false;

  @Option(
      names = {"--privacy-url"},
      description = "The URL on which the enclave is running")
  private final URI privacyUrl = PrivacyParameters.DEFAULT_ENCLAVE_URL;

  @Option(
      names = {"--privacy-precompiled-address"},
      description =
          "The address to which the privacy pre-compiled contract will be mapped to (default: ${DEFAULT-VALUE})")
  private final Integer privacyPrecompiledAddress = Address.PRIVACY;

  @Option(
      names = {"--privacy-marker-transaction-signing-key-file"},
      description =
          "The name of a file containing the private key used to sign privacy marker transactions. If unset, each will be signed with a random key.")
  private final Path privacyMarkerTransactionSigningKeyPath = null;

  @Option(
      names = {"--tx-pool-max-size"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Maximum number of pending transactions that will be kept in the transaction pool (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer txPoolMaxSize = TransactionPoolConfiguration.MAX_PENDING_TRANSACTIONS;

  @Option(
      names = {"--tx-pool-retention-hours"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Maximum retention period of pending transactions in hours (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer pendingTxRetentionPeriod =
      TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS;

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
  @Option(
      names = {"--key-value-storage"},
      description = "Identity for the key-value storage to be used.",
      arity = "1")
  private String keyValueStorageName = DEFAULT_KEY_VALUE_STORAGE_NAME;

  @Option(
      names = {"--override-genesis-config"},
      paramLabel = "NAME=VALUE",
      description = "Overrides configuration values in the genesis file.  Use with care.",
      arity = "*",
      hidden = true,
      split = ",")
  private final Map<String, String> genesisConfigOverrides =
      new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  @Option(
      names = {"--crosschain-enabled"},
      description = "Enable crosschain features (default: ${DEFAULT-VALUE}")
  public final Boolean isCrosschainsEnabled = false;

  private EthNetworkConfig ethNetworkConfig;
  private JsonRpcConfiguration jsonRpcConfiguration;
  private GraphQLConfiguration graphQLConfiguration;
  private WebSocketConfiguration webSocketConfiguration;
  private MetricsConfiguration metricsConfiguration;
  private Optional<PermissioningConfiguration> permissioningConfiguration;
  private Collection<EnodeURL> staticNodes;
  private BesuController<?> besuController;
  private StandaloneCommand standaloneCommands;
  private BesuConfiguration pluginCommonConfiguration;
  private final Supplier<ObservableMetricsSystem> metricsSystem =
      Suppliers.memoize(() -> PrometheusMetricsSystem.init(metricsConfiguration()));

  public BesuCommand(
      final Logger logger,
      final RlpBlockImporter rlpBlockImporter,
      final JsonBlockImporterFactory jsonBlockImporterFactory,
      final RlpBlockExporterFactory rlpBlockExporterFactory,
      final RunnerBuilder runnerBuilder,
      final BesuController.Builder controllerBuilderFactory,
      final BesuPluginContextImpl besuPluginContext,
      final Map<String, String> environment) {
    this(
        logger,
        rlpBlockImporter,
        jsonBlockImporterFactory,
        rlpBlockExporterFactory,
        runnerBuilder,
        controllerBuilderFactory,
        besuPluginContext,
        environment,
        new StorageServiceImpl());
  }

  @VisibleForTesting
  protected BesuCommand(
      final Logger logger,
      final RlpBlockImporter rlpBlockImporter,
      final JsonBlockImporterFactory jsonBlockImporterFactory,
      final RlpBlockExporterFactory rlpBlockExporterFactory,
      final RunnerBuilder runnerBuilder,
      final BesuController.Builder controllerBuilderFactory,
      final BesuPluginContextImpl besuPluginContext,
      final Map<String, String> environment,
      final StorageServiceImpl storageService) {
    this.logger = logger;
    this.rlpBlockImporter = rlpBlockImporter;
    this.rlpBlockExporterFactory = rlpBlockExporterFactory;
    this.jsonBlockImporterFactory = jsonBlockImporterFactory;
    this.runnerBuilder = runnerBuilder;
    this.controllerBuilderFactory = controllerBuilderFactory;
    this.besuPluginContext = besuPluginContext;
    this.environment = environment;
    this.storageService = storageService;
  }

  public void parse(
      final AbstractParseResultHandler<List<Object>> resultHandler,
      final BesuExceptionHandler exceptionHandler,
      final InputStream in,
      final String... args) {
    commandLine = new CommandLine(this).setCaseInsensitiveEnumValuesAllowed(true);
    handleStandaloneCommand()
        .addSubCommands(resultHandler, in)
        .registerConverters()
        .handleUnstableOptions()
        .preparePlugins()
        .parse(resultHandler, exceptionHandler, args);
  }

  @Override
  public void run() {
    try {
      prepareLogging();
      logger.info("Starting Besu version: {}", BesuInfo.version());
      validateOptions().configure().controller().startPlugins().startSynchronization();
    } catch (final Exception e) {
      throw new ParameterException(this.commandLine, e.getMessage(), e);
    }
  }

  private void addConfigurationService() {
    if (pluginCommonConfiguration == null) {
      pluginCommonConfiguration = new BesuConfigurationImpl(dataDir().resolve(DATABASE_PATH));
      besuPluginContext.addService(BesuConfiguration.class, pluginCommonConfiguration);
    }
  }

  @VisibleForTesting
  void setBesuConfiguration(final BesuConfiguration pluginCommonConfiguration) {
    this.pluginCommonConfiguration = pluginCommonConfiguration;
  }

  private BesuCommand handleStandaloneCommand() {
    standaloneCommands = new StandaloneCommand();
    if (isFullInstantiation()) {
      commandLine.addMixin("standaloneCommands", standaloneCommands);
    }
    return this;
  }

  private BesuCommand addSubCommands(
      final AbstractParseResultHandler<List<Object>> resultHandler, final InputStream in) {
    commandLine.addSubcommand(
        BlocksSubCommand.COMMAND_NAME,
        new BlocksSubCommand(
            rlpBlockImporter,
            jsonBlockImporterFactory,
            rlpBlockExporterFactory,
            resultHandler.out()));
    commandLine.addSubcommand(
        PublicKeySubCommand.COMMAND_NAME,
        new PublicKeySubCommand(resultHandler.out(), getKeyLoader()));
    commandLine.addSubcommand(
        PasswordSubCommand.COMMAND_NAME, new PasswordSubCommand(resultHandler.out()));
    commandLine.addSubcommand(RetestethSubCommand.COMMAND_NAME, new RetestethSubCommand());
    commandLine.addSubcommand(
        RLPSubCommand.COMMAND_NAME, new RLPSubCommand(resultHandler.out(), in));
    commandLine.addSubcommand(
        OperatorSubCommand.COMMAND_NAME, new OperatorSubCommand(resultHandler.out()));
    return this;
  }

  private BesuCommand registerConverters() {
    commandLine.registerConverter(Address.class, Address::fromHexStringStrict);
    commandLine.registerConverter(BytesValue.class, BytesValue::fromHexString);
    commandLine.registerConverter(Level.class, Level::valueOf);
    commandLine.registerConverter(SyncMode.class, SyncMode::fromString);
    commandLine.registerConverter(UInt256.class, (arg) -> UInt256.of(new BigInteger(arg)));
    commandLine.registerConverter(Wei.class, (arg) -> Wei.of(Long.parseUnsignedLong(arg)));
    commandLine.registerConverter(PositiveNumber.class, PositiveNumber::fromString);

    final MetricCategoryConverter metricCategoryConverter = new MetricCategoryConverter();
    metricCategoryConverter.addCategories(BesuMetricCategory.class);
    metricCategoryConverter.addCategories(StandardMetricCategory.class);
    commandLine.registerConverter(MetricCategory.class, metricCategoryConverter);
    return this;
  }

  private BesuCommand handleUnstableOptions() {
    // Add unstable options
    final ImmutableMap.Builder<String, Object> unstableOptionsBuild = ImmutableMap.builder();
    final ImmutableMap<String, Object> unstableOptions =
        unstableOptionsBuild
            .put("Ethereum Wire Protocol", ethProtocolOptions)
            .put("Metrics", metricsCLIOptions)
            .put("P2P Network", networkingOptions)
            .put("Synchronizer", synchronizerOptions)
            .put("TransactionPool", transactionPoolOptions)
            .build();

    UnstableOptionsSubCommand.createUnstableOptions(commandLine, unstableOptions);
    return this;
  }

  private BesuCommand preparePlugins() {
    besuPluginContext.addService(PicoCLIOptions.class, new PicoCLIOptionsImpl(commandLine));
    besuPluginContext.addService(StorageService.class, storageService);

    // register built-in plugins
    new RocksDBPlugin().register(besuPluginContext);

    besuPluginContext.registerPlugins(pluginsDir());
    return this;
  }

  private void parse(
      final AbstractParseResultHandler<List<Object>> resultHandler,
      final BesuExceptionHandler exceptionHandler,
      final String... args) {
    // Create a handler that will search for a config file option and use it for
    // default values
    // and eventually it will run regular parsing of the remaining options.
    final ConfigOptionSearchAndRunHandler configParsingHandler =
        new ConfigOptionSearchAndRunHandler(
            resultHandler, exceptionHandler, CONFIG_FILE_OPTION_NAME, environment, isDocker);
    commandLine.parseWithHandlers(configParsingHandler, exceptionHandler, args);
  }

  private void startSynchronization() {
    synchronize(
        besuController,
        p2pEnabled,
        peerDiscoveryEnabled,
        ethNetworkConfig,
        maxPeers,
        p2pHost,
        p2pInterface,
        p2pPort,
        graphQLConfiguration,
        jsonRpcConfiguration,
        webSocketConfiguration,
        metricsConfiguration,
        permissioningConfiguration,
        staticNodes);
  }

  private BesuCommand startPlugins() {
    besuPluginContext.addService(
        BesuEvents.class,
        new BesuEventsImpl(
            besuController.getProtocolManager().getBlockBroadcaster(),
            besuController.getTransactionPool(),
            besuController.getSyncState()));
    besuPluginContext.addService(MetricsSystem.class, getMetricsSystem());
    besuPluginContext.startPlugins();
    return this;
  }

  private void prepareLogging() {
    // set log level per CLI flags
    if (logLevel != null) {
      System.out.println("Setting logging level to " + logLevel.name());
      Configurator.setAllLevels("", logLevel);
    }
  }

  private BesuCommand validateOptions() {
    issueOptionWarnings();

    validateP2PInterface(p2pInterface);
    validateMiningParams();

    return this;
  }

  private void validateMiningParams() {
    // noinspection ConstantConditions
    if (isMiningEnabled && coinbase == null) {
      throw new ParameterException(
          this.commandLine,
          "Unable to mine without a valid coinbase. Either disable mining (remove --miner-enabled)"
              + "or specify the beneficiary of mining (via --miner-coinbase <Address>)");
    }
  }

  protected void validateP2PInterface(final String p2pInterface) {
    final String failMessage = "The provided --p2p-interface is not available: " + p2pInterface;
    try {
      if (!NetworkUtility.isNetworkInterfaceAvailable(p2pInterface)) {
        throw new ParameterException(commandLine, failMessage);
      }
    } catch (UnknownHostException | SocketException e) {
      throw new ParameterException(commandLine, failMessage, e);
    }
  }

  private void issueOptionWarnings() {
    // Check that P2P options are able to work
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--p2p-enabled",
        !p2pEnabled,
        asList(
            "--bootnodes",
            "--discovery-enabled",
            "--max-peers",
            "--banned-node-id",
            "--banned-node-ids",
            "--p2p-host",
            "--p2p-interface",
            "--p2p-port",
            "--remote-connections-max-percentage"));
    // Check that mining options are able to work
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--miner-enabled",
        !isMiningEnabled,
        asList("--miner-coinbase", "--min-gas-price", "--miner-extra-data"));

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--sync-mode",
        !SyncMode.FAST.equals(syncMode),
        singletonList("--fast-sync-min-peers"));

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--pruning-enabled",
        !isPruningEnabled,
        asList("--pruning-block-confirmations", "--pruning-blocks-retained"));
  }

  private BesuCommand configure() throws Exception {
    ethNetworkConfig = updateNetworkConfig(getNetwork());
    jsonRpcConfiguration = jsonRpcConfiguration();
    graphQLConfiguration = graphQLConfiguration();
    webSocketConfiguration = webSocketConfiguration();
    permissioningConfiguration = permissioningConfiguration();
    staticNodes = loadStaticNodes();
    logger.info("Connecting to {} static nodes.", staticNodes.size());
    logger.trace("Static Nodes = {}", staticNodes);
    final List<URI> enodeURIs =
        ethNetworkConfig.getBootNodes().stream().map(EnodeURL::toURI).collect(Collectors.toList());
    permissioningConfiguration
        .flatMap(PermissioningConfiguration::getLocalConfig)
        .ifPresent(p -> ensureAllNodesAreInWhitelist(enodeURIs, p));

    permissioningConfiguration
        .flatMap(PermissioningConfiguration::getLocalConfig)
        .ifPresent(
            p ->
                ensureAllNodesAreInWhitelist(
                    staticNodes.stream().map(EnodeURL::toURI).collect(Collectors.toList()), p));
    metricsConfiguration = metricsConfiguration();
    crosschainConfig();
    return this;
  }

  private NetworkName getNetwork() {
    // noinspection ConstantConditions network is not always null but injected by
    // PicoCLI if used
    return network == null ? MAINNET : network;
  }

  private void ensureAllNodesAreInWhitelist(
      final Collection<URI> enodeAddresses,
      final LocalPermissioningConfiguration permissioningConfiguration) {
    try {
      PermissioningConfigurationValidator.areAllNodesAreInWhitelist(
          enodeAddresses, permissioningConfiguration);
    } catch (final Exception e) {
      throw new ParameterException(this.commandLine, e.getMessage());
    }
  }

  private BesuCommand controller() {
    besuController = buildController();
    return this;
  }

  public BesuController<?> buildController() {
    try {
      return getControllerBuilder().build();
    } catch (final Exception e) {
      throw new ExecutionException(this.commandLine, e.getMessage(), e);
    }
  }

  public BesuControllerBuilder<?> getControllerBuilder() {
    try {
      addConfigurationService();
      return controllerBuilderFactory
          .fromEthNetworkConfig(updateNetworkConfig(getNetwork()), genesisConfigOverrides)
          .synchronizerConfiguration(buildSyncConfig())
          .ethProtocolConfiguration(ethProtocolOptions.toDomainObject())
          .dataDirectory(dataDir())
          .miningParameters(
              new MiningParameters(coinbase, minTransactionGasPrice, extraData, isMiningEnabled))
          .transactionPoolConfiguration(buildTransactionPoolConfiguration())
          .nodePrivateKeyFile(nodePrivateKeyFile())
          .metricsSystem(metricsSystem.get())
          .privacyParameters(privacyParameters())
          .clock(Clock.systemUTC())
          .isRevertReasonEnabled(isRevertReasonEnabled)
          .storageProvider(keyStorageProvider(keyValueStorageName))
          .isPruningEnabled(isPruningEnabled)
          .pruningConfiguration(buildPruningConfiguration())
          .genesisConfigOverrides(genesisConfigOverrides);
    } catch (final IOException e) {
      throw new ExecutionException(this.commandLine, "Invalid path", e);
    }
  }

  private GraphQLConfiguration graphQLConfiguration() {

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--graphql-http-enabled",
        !isRpcHttpEnabled,
        asList("--graphql-http-cors-origins", "--graphql-http-host", "--graphql-http-port"));

    final GraphQLConfiguration graphQLConfiguration = GraphQLConfiguration.createDefault();
    graphQLConfiguration.setEnabled(isGraphQLHttpEnabled);
    graphQLConfiguration.setHost(graphQLHttpHost);
    graphQLConfiguration.setPort(graphQLHttpPort);
    graphQLConfiguration.setHostsWhitelist(hostsWhitelist);
    graphQLConfiguration.setCorsAllowedDomains(graphQLHttpCorsAllowedOrigins);

    return graphQLConfiguration;
  }

  private JsonRpcConfiguration jsonRpcConfiguration() {

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-http-enabled",
        !isRpcHttpEnabled,
        asList(
            "--rpc-http-api",
            "--rpc-http-apis",
            "--rpc-http-cors-origins",
            "--rpc-http-host",
            "--rpc-http-port",
            "--rpc-http-authentication-enabled",
            "--rpc-http-authentication-credentials-file"));

    if (isRpcHttpAuthenticationEnabled && rpcHttpAuthenticationCredentialsFile() == null) {
      throw new ParameterException(
          commandLine,
          "Unable to authenticate JSON-RPC HTTP endpoint without a supplied credentials file");
    }

    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setEnabled(isRpcHttpEnabled);
    jsonRpcConfiguration.setHost(rpcHttpHost);
    jsonRpcConfiguration.setPort(rpcHttpPort);
    jsonRpcConfiguration.setCorsAllowedDomains(rpcHttpCorsAllowedOrigins);
    jsonRpcConfiguration.setRpcApis(rpcHttpApis.stream().distinct().collect(Collectors.toList()));
    jsonRpcConfiguration.setHostsWhitelist(hostsWhitelist);
    jsonRpcConfiguration.setAuthenticationEnabled(isRpcHttpAuthenticationEnabled);
    jsonRpcConfiguration.setAuthenticationCredentialsFile(rpcHttpAuthenticationCredentialsFile());
    return jsonRpcConfiguration;
  }

  private WebSocketConfiguration webSocketConfiguration() {

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-ws-enabled",
        !isRpcWsEnabled,
        asList(
            "--rpc-ws-api",
            "--rpc-ws-apis",
            "--rpc-ws-host",
            "--rpc-ws-port",
            "--rpc-ws-authentication-enabled",
            "--rpc-ws-authentication-credentials-file"));

    if (isRpcWsAuthenticationEnabled && rpcWsAuthenticationCredentialsFile() == null) {
      throw new ParameterException(
          commandLine,
          "Unable to authenticate JSON-RPC WebSocket endpoint without a supplied credentials file");
    }

    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setEnabled(isRpcWsEnabled);
    webSocketConfiguration.setHost(rpcWsHost);
    webSocketConfiguration.setPort(rpcWsPort);
    webSocketConfiguration.setRpcApis(rpcWsApis);
    webSocketConfiguration.setAuthenticationEnabled(isRpcWsAuthenticationEnabled);
    webSocketConfiguration.setAuthenticationCredentialsFile(rpcWsAuthenticationCredentialsFile());
    webSocketConfiguration.setHostsWhitelist(hostsWhitelist);
    return webSocketConfiguration;
  }

  public MetricsConfiguration metricsConfiguration() {
    if (isMetricsEnabled && isMetricsPushEnabled) {
      throw new ParameterException(
          this.commandLine,
          "--metrics-enabled option and --metrics-push-enabled option can't be used at the same "
              + "time.  Please refer to CLI reference for more details about this constraint.");
    }

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--metrics-enabled",
        !isMetricsEnabled,
        asList("--metrics-host", "--metrics-port"));

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--metrics-push-enabled",
        !isMetricsPushEnabled,
        asList(
            "--metrics-push-host",
            "--metrics-push-port",
            "--metrics-push-interval",
            "--metrics-push-prometheus-job"));

    return metricsCLIOptions
        .toDomainObject()
        .enabled(isMetricsEnabled)
        .host(metricsHost)
        .port(metricsPort)
        .metricCategories(metricCategories)
        .pushEnabled(isMetricsPushEnabled)
        .pushHost(metricsPushHost)
        .pushPort(metricsPushPort)
        .pushInterval(metricsPushInterval)
        .hostsWhitelist(hostsWhitelist)
        .prometheusJob(metricsPrometheusJob)
        .build();
  }

  private Optional<PermissioningConfiguration> permissioningConfiguration() throws Exception {
    if (!(localPermissionsEnabled() || contractPermissionsEnabled())) {
      if (rpcHttpApis.contains(RpcApis.PERM) || rpcWsApis.contains(RpcApis.PERM)) {
        logger.warn(
            "Permissions are disabled. Cannot enable PERM APIs when not using Permissions.");
      }
      return Optional.empty();
    }

    final Optional<LocalPermissioningConfiguration> localPermissioningConfigurationOptional;
    if (localPermissionsEnabled()) {
      final Optional<String> nodePermissioningConfigFile =
          Optional.ofNullable(nodePermissionsConfigFile());
      final Optional<String> accountPermissioningConfigFile =
          Optional.ofNullable(accountsPermissionsConfigFile());

      final LocalPermissioningConfiguration localPermissioningConfiguration =
          PermissioningConfigurationBuilder.permissioningConfiguration(
              permissionsNodesEnabled,
              nodePermissioningConfigFile.orElse(getDefaultPermissioningFilePath()),
              permissionsAccountsEnabled,
              accountPermissioningConfigFile.orElse(getDefaultPermissioningFilePath()));

      localPermissioningConfigurationOptional = Optional.of(localPermissioningConfiguration);
    } else {
      if (nodePermissionsConfigFile() != null && !permissionsNodesEnabled) {
        logger.warn(
            "Node permissioning config file set {} but no permissions enabled",
            nodePermissionsConfigFile());
      }

      if (accountsPermissionsConfigFile() != null && !permissionsAccountsEnabled) {
        logger.warn(
            "Account permissioning config file set {} but no permissions enabled",
            accountsPermissionsConfigFile());
      }
      localPermissioningConfigurationOptional = Optional.empty();
    }

    final SmartContractPermissioningConfiguration smartContractPermissioningConfiguration =
        SmartContractPermissioningConfiguration.createDefault();
    if (permissionsNodesContractEnabled) {
      if (permissionsNodesContractAddress == null) {
        throw new ParameterException(
            this.commandLine,
            "No node permissioning contract address specified. Cannot enable smart contract based node permissioning.");
      } else {
        smartContractPermissioningConfiguration.setSmartContractNodeWhitelistEnabled(
            permissionsNodesContractEnabled);
        smartContractPermissioningConfiguration.setNodeSmartContractAddress(
            permissionsNodesContractAddress);
      }
    } else if (permissionsNodesContractAddress != null) {
      logger.warn(
          "Node permissioning smart contract address set {} but smart contract node permissioning is disabled.",
          permissionsNodesContractAddress);
    }

    if (permissionsAccountsContractEnabled) {
      if (permissionsAccountsContractAddress == null) {
        throw new ParameterException(
            this.commandLine,
            "No account permissioning contract address specified. Cannot enable smart contract based account permissioning.");
      } else {
        smartContractPermissioningConfiguration.setSmartContractAccountWhitelistEnabled(
            permissionsAccountsContractEnabled);
        smartContractPermissioningConfiguration.setAccountSmartContractAddress(
            permissionsAccountsContractAddress);
      }
    } else if (permissionsAccountsContractAddress != null) {
      logger.warn(
          "Account permissioning smart contract address set {} but smart contract account permissioning is disabled.",
          permissionsAccountsContractAddress);
    }

    final PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            localPermissioningConfigurationOptional,
            Optional.of(smartContractPermissioningConfiguration));

    return Optional.of(permissioningConfiguration);
  }

  private boolean localPermissionsEnabled() {
    return permissionsAccountsEnabled || permissionsNodesEnabled;
  }

  private boolean contractPermissionsEnabled() {
    return permissionsNodesContractEnabled || permissionsAccountsContractEnabled;
  }

  private PrivacyParameters privacyParameters() throws IOException {

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--privacy-enabled",
        !isPrivacyEnabled,
        asList("--privacy-url", "--privacy-public-key-file", "--privacy-precompiled-address"));

    final PrivacyParameters.Builder privacyParametersBuilder = new PrivacyParameters.Builder();
    if (isPrivacyEnabled) {
      privacyParametersBuilder.setEnabled(true);
      privacyParametersBuilder.setEnclaveUrl(privacyUrl);
      if (privacyPublicKeyFile() != null) {
        privacyParametersBuilder.setEnclavePublicKeyUsingFile(privacyPublicKeyFile());
      } else {
        throw new ParameterException(
            commandLine, "Please specify Enclave public key file path to enable privacy");
      }
      privacyParametersBuilder.setPrivacyAddress(privacyPrecompiledAddress);
      privacyParametersBuilder.setPrivateKeyPath(privacyMarkerTransactionSigningKeyPath);
      privacyParametersBuilder.setStorageProvider(
          keyStorageProvider(keyValueStorageName + "-privacy"));
    }

    return privacyParametersBuilder.build();
  }

  private KeyValueStorageProvider keyStorageProvider(final String name) {
    return new KeyValueStorageProviderBuilder()
        .withStorageFactory(
            storageService
                .getByName(name)
                .orElseThrow(
                    () -> new StorageException("No KeyValueStorageFactory found for key: " + name)))
        .withCommonConfiguration(pluginCommonConfiguration)
        .withMetricsSystem(getMetricsSystem())
        .build();
  }

  private SynchronizerConfiguration buildSyncConfig() {
    return synchronizerOptions
        .toDomainObject()
        .syncMode(syncMode)
        .fastSyncMinimumPeerCount(fastSyncMinPeerCount)
        .build();
  }

  private TransactionPoolConfiguration buildTransactionPoolConfiguration() {
    return transactionPoolOptions
        .toDomainObject()
        .txPoolMaxSize(txPoolMaxSize)
        .pendingTxRetentionPeriod(pendingTxRetentionPeriod)
        .build();
  }

  private PruningConfiguration buildPruningConfiguration() {
    return new PruningConfiguration(pruningBlockConfirmations, pruningBlocksRetained);
  }

  private void crosschainConfig() {
    if (isCrosschainsEnabled) {
      CrosschainConfigOptions.isEnabled = true;
      // TODO put any crosschain specific stuff here.
    }
  }

  // Blockchain synchronisation from peers.
  private void synchronize(
      final BesuController<?> controller,
      final boolean p2pEnabled,
      final boolean peerDiscoveryEnabled,
      final EthNetworkConfig ethNetworkConfig,
      final int maxPeers,
      final String p2pAdvertisedHost,
      final String p2pListenInterface,
      final int p2pListenPort,
      final GraphQLConfiguration graphQLConfiguration,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final Optional<PermissioningConfiguration> permissioningConfiguration,
      final Collection<EnodeURL> staticNodes) {

    checkNotNull(runnerBuilder);

    permissioningConfiguration.ifPresent(runnerBuilder::permissioningConfiguration);

    final ObservableMetricsSystem metricsSystem = this.metricsSystem.get();
    final Runner runner =
        runnerBuilder
            .vertx(Vertx.vertx(createVertxOptions(metricsSystem)))
            .besuController(controller)
            .p2pEnabled(p2pEnabled)
            .natMethod(natMethod)
            .discovery(peerDiscoveryEnabled)
            .ethNetworkConfig(ethNetworkConfig)
            .p2pAdvertisedHost(p2pAdvertisedHost)
            .p2pListenInterface(p2pListenInterface)
            .p2pListenPort(p2pListenPort)
            .maxPeers(maxPeers)
            .limitRemoteWireConnectionsEnabled(isLimitRemoteWireConnectionsEnabled)
            .fractionRemoteConnectionsAllowed(
                Fraction.fromPercentage(maxRemoteConnectionsPercentage).getValue())
            .networkingConfiguration(networkingOptions.toDomainObject())
            .graphQLConfiguration(graphQLConfiguration)
            .jsonRpcConfiguration(jsonRpcConfiguration)
            .webSocketConfiguration(webSocketConfiguration)
            .dataDir(dataDir())
            .bannedNodeIds(bannedNodeIds)
            .metricsSystem(metricsSystem)
            .metricsConfiguration(metricsConfiguration)
            .staticNodes(staticNodes)
            .build();

    addShutdownHook(runner);
    runner.start();
    runner.awaitStop();
  }

  private VertxOptions createVertxOptions(final MetricsSystem metricsSystem) {
    return new VertxOptions()
        .setMetricsOptions(
            new MetricsOptions()
                .setEnabled(true)
                .setFactory(new VertxMetricsAdapterFactory(metricsSystem)));
  }

  private void addShutdownHook(final Runner runner) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    besuPluginContext.stopPlugins();
                    runner.close();
                    LogManager.shutdown();
                  } catch (final Exception e) {
                    logger.error("Failed to stop Besu");
                  }
                }));
  }

  // Used to discover the default IP of the client.
  // Loopback IP is used by default as this is how smokeTests require it to be
  // and it's probably a good security behaviour to default only on the localhost.
  private InetAddress autoDiscoverDefaultIP() {

    if (autoDiscoveredDefaultIP != null) {
      return autoDiscoveredDefaultIP;
    }

    autoDiscoveredDefaultIP = InetAddress.getLoopbackAddress();

    return autoDiscoveredDefaultIP;
  }

  private EthNetworkConfig updateNetworkConfig(final NetworkName network) {
    final EthNetworkConfig.Builder builder =
        new EthNetworkConfig.Builder(EthNetworkConfig.getNetworkConfig(network));

    // custom genesis file use comes with specific default values for the genesis
    // file itself
    // but also for the network id and the bootnodes list.
    final File genesisFile = genesisFile();
    if (genesisFile != null) {

      // noinspection ConstantConditions network is not always null but injected by
      // PicoCLI if used
      if (this.network != null) {
        // We check if network option was really provided by user and not only looking
        // at the
        // default value.
        // if user provided it and provided the genesis file option at the same time, it
        // raises a
        // conflict error
        throw new ParameterException(
            this.commandLine,
            "--network option and --genesis-file option can't be used at the same time.  Please "
                + "refer to CLI reference for more details about this constraint.");
      }

      builder.setGenesisConfig(genesisConfig());

      if (networkId == null) {
        // if no network id option is defined on the CLI we have to set a default value
        // from the
        // genesis file.
        // We do the genesis parsing only in this case as we already have network id
        // constants
        // for known networks to speed up the process.
        // Also we have to parse the genesis as we don't already have a parsed version
        // at this
        // stage.
        // If no chain id is found in the genesis as it's an optional, we use mainnet
        // network id.
        try {
          final GenesisConfigFile genesisConfigFile = GenesisConfigFile.fromConfig(genesisConfig());
          builder.setNetworkId(
              genesisConfigFile
                  .getConfigOptions(genesisConfigOverrides)
                  .getChainId()
                  .orElse(EthNetworkConfig.getNetworkConfig(MAINNET).getNetworkId()));
        } catch (final DecodeException e) {
          throw new ParameterException(
              this.commandLine, String.format("Unable to parse genesis file %s.", genesisFile), e);
        } catch (final ArithmeticException e) {
          throw new ParameterException(
              this.commandLine,
              "No networkId specified and chainId in "
                  + "genesis file is too large to be used as a networkId");
        }
      }

      if (bootNodes == null) {
        // We default to an empty bootnodes list if the option is not provided on CLI
        // because
        // mainnet bootnodes won't work as the default value for a custom genesis,
        // so it's better to have an empty list as default value that forces to create a
        // custom one
        // than a useless one that may make user think that it can work when it can't.
        builder.setBootNodes(new ArrayList<>());
      }
    }

    if (networkId != null) {
      builder.setNetworkId(networkId);
    }

    if (bootNodes != null) {
      builder.setBootNodes(bootNodes);
    }

    return builder.build();
  }

  private String genesisConfig() {
    try {
      return Resources.toString(genesisFile().toURI().toURL(), UTF_8);
    } catch (final IOException e) {
      throw new ParameterException(
          this.commandLine, String.format("Unable to load genesis file %s.", genesisFile()), e);
    }
  }

  private File genesisFile() {
    if (isFullInstantiation()) {
      return standaloneCommands.genesisFile;
    } else if (isDocker) {
      final File genesisFile = new File(DOCKER_GENESIS_LOCATION);
      if (genesisFile.exists()) {
        return genesisFile;
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  public Path dataDir() {
    if (isFullInstantiation()) {
      return standaloneCommands.dataPath.toAbsolutePath();
    } else if (isDocker) {
      return Paths.get(DOCKER_DATADIR_LOCATION);
    } else {
      return getDefaultBesuDataPath(this);
    }
  }

  private Path pluginsDir() {
    if (isFullInstantiation()) {
      final String pluginsDir = System.getProperty("besu.plugins.dir");
      if (pluginsDir == null) {
        return new File(System.getProperty("besu.home", "."), "plugins").toPath();
      } else {
        return new File(pluginsDir).toPath();
      }
    } else if (isDocker) {
      return Paths.get(DOCKER_PLUGINSDIR_LOCATION);
    } else {
      return null; // null means no plugins
    }
  }

  public File nodePrivateKeyFile() {
    File nodePrivateKeyFile = null;
    if (isFullInstantiation()) {
      nodePrivateKeyFile = standaloneCommands.nodePrivateKeyFile; // always null!
    }

    return nodePrivateKeyFile != null
        ? nodePrivateKeyFile
        : KeyPairUtil.getDefaultKeyFile(dataDir());
  }

  private File privacyPublicKeyFile() {
    if (isDocker) {
      final File keyFile = new File(DOCKER_PRIVACY_PUBLIC_KEY_FILE);
      if (keyFile.exists()) {
        return keyFile;
      } else {
        return null;
      }
    } else {
      return standaloneCommands.privacyPublicKeyFile;
    }
  }

  private String rpcHttpAuthenticationCredentialsFile() {
    String filename = null;
    if (isFullInstantiation()) {
      filename = standaloneCommands.rpcHttpAuthenticationCredentialsFile;
    } else if (isDocker) {
      final File authFile = new File(DOCKER_RPC_HTTP_AUTHENTICATION_CREDENTIALS_FILE_LOCATION);
      if (authFile.exists()) {
        filename = authFile.getAbsolutePath();
      }
    }

    if (filename != null) {
      RpcAuthFileValidator.validate(commandLine, filename, "HTTP");
    }
    return filename;
  }

  private String rpcWsAuthenticationCredentialsFile() {
    String filename = null;
    if (isFullInstantiation()) {
      filename = standaloneCommands.rpcWsAuthenticationCredentialsFile;
    } else if (isDocker) {
      final File authFile = new File(DOCKER_RPC_WS_AUTHENTICATION_CREDENTIALS_FILE_LOCATION);
      if (authFile.exists()) {
        filename = authFile.getAbsolutePath();
      }
    }

    if (filename != null) {
      RpcAuthFileValidator.validate(commandLine, filename, "WS");
    }
    return filename;
  }

  private String nodePermissionsConfigFile() {
    return permissionsConfigFile(standaloneCommands.nodePermissionsConfigFile);
  }

  private String accountsPermissionsConfigFile() {
    return permissionsConfigFile(standaloneCommands.accountPermissionsConfigFile);
  }

  private String permissionsConfigFile(final String permissioningFilename) {
    String filename = null;
    if (isFullInstantiation()) {
      filename = permissioningFilename;
    } else if (isDocker) {
      final File file = new File(DOCKER_PERMISSIONS_CONFIG_FILE_LOCATION);
      if (file.exists()) {
        filename = file.getAbsolutePath();
      }
    }
    return filename;
  }

  private String getDefaultPermissioningFilePath() {
    return dataDir().toAbsolutePath()
        + System.getProperty("file.separator")
        + DefaultCommandValues.PERMISSIONING_CONFIG_LOCATION;
  }

  private boolean isFullInstantiation() {
    return !isDocker;
  }

  public MetricsSystem getMetricsSystem() {
    return metricsSystem.get();
  }

  private Set<EnodeURL> loadStaticNodes() throws IOException {
    final String staticNodesFilename = "static-nodes.json";
    final Path staticNodesPath = dataDir().resolve(staticNodesFilename);

    return StaticNodesParser.fromPath(staticNodesPath);
  }

  public BesuExceptionHandler exceptionHandler() {
    return new BesuExceptionHandler(this::getLogLevel);
  }

  private Level getLogLevel() {
    return logLevel;
  }
}
