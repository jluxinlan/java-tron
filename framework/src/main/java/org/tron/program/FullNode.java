package org.tron.program;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import java.io.File;
import java.math.BigInteger;
import java.util.*;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.util.StringUtils;
import org.tron.common.application.Application;
import org.tron.common.application.ApplicationFactory;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.parameter.CommonParameter;
import org.tron.common.runtime.ProgramResult;
import org.tron.common.runtime.vm.DataWord;
import org.tron.common.runtime.vm.LogInfo;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Commons;
import org.tron.core.Constant;
import org.tron.core.actuator.VMActuator;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.capsule.TransactionInfoCapsule;
import org.tron.core.capsule.TransactionRetCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.BlockIndexStore;
import org.tron.core.db.BlockStore;
import org.tron.core.db.Manager;
import org.tron.core.db.TransactionContext;
import org.tron.core.exception.BadItemException;
import org.tron.core.services.RpcApiService;
import org.tron.core.store.StoreFactory;
import org.tron.core.store.TransactionHistoryStore;
import org.tron.core.store.TransactionRetStore;
import org.tron.core.vm.utils.MUtil;
import org.tron.protos.Protocol;
import org.tron.protos.contract.SmartContractOuterClass;

@Slf4j(topic = "app")
public class FullNode {


  public static void load(String path) {
    try {
      File file = new File(path);
      if (!file.exists() || !file.isFile() || !file.canRead()) {
        return;
      }
      LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(lc);
      lc.reset();
      configurator.doConfigure(file);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }

  private static TransactionRetStore transactionRetStore;
  private static TransactionHistoryStore transactionHistoryStore;
  private static BlockStore blockStore;
  private static BlockIndexStore blockIndexStore;


  /**
   * Start the FullNode.
   */
  public static void main(String[] args) {
    logger.info("Full node running.");
    // todo dbPath
    String dbPath = "";
    args = new String[]{"-d", dbPath};
    Args.setParam(args, Constant.TESTNET_CONF);
    CommonParameter parameter = Args.getInstance();

    load(parameter.getLogbackPath());

    if (parameter.isHelp()) {
      logger.info("Here is the help message.");
      return;
    }

    if (Args.getInstance().isDebug()) {
      logger.info("in debug mode, it won't check energy time");
    } else {
      logger.info("not in debug mode, it will check energy time");
    }

    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.setAllowCircularReferences(false);
    TronApplicationContext context =
        new TronApplicationContext(beanFactory);
    context.register(DefaultConfig.class);

    context.refresh();
    Application appT = ApplicationFactory.create(context);
    shutdown(appT);

    final Manager dbManager = appT.getDbManager();
    blockStore = dbManager.getBlockStore();
    blockIndexStore = dbManager.getBlockIndexStore();
    transactionRetStore = dbManager.getTransactionRetStore();
    transactionHistoryStore = dbManager.getTransactionHistoryStore();

    final long headBlockNum = dbManager.getHeadBlockNum();
    Map<String, TreeSet<String>> tokenMap = handlerMap(headBlockNum);
    handlerMapToDB(tokenMap, headBlockNum);

    // grpc api server
    RpcApiService rpcApiService = context.getBean(RpcApiService.class);
    appT.addService(rpcApiService);

    appT.initServices(parameter);
    appT.startServices();
    appT.startup();

    rpcApiService.blockUntilShutdown();
  }

  private static void handlerMapToDB(Map<String, TreeSet<String>> tokenMap, long headBlockNum) {
    final BlockCapsule blockCapsule = getBlockByNum(headBlockNum);
    SyncDataToDB syncDataToDB = new SyncDataToDB();

    tokenMap.forEach((tokenAddress, treeSet) -> {
      final BigInteger trc20Decimal = getTRC20Decimal(tokenAddress, blockCapsule);
      treeSet.forEach(accountAddress -> {
        final BigInteger trc20Balance = getTRC20Balance(accountAddress, tokenAddress, blockCapsule);
        syncDataToDB.save(tokenAddress, accountAddress, headBlockNum, trc20Balance, trc20Decimal.intValue());
      });
    });
  }

  private static Map<String, TreeSet<String>> handlerMap(long headBlockNum) {
    Map<String, TreeSet<String>> tokenMap = new HashMap<>();
    for (long num = 1; num <= headBlockNum; num++) {
      final BlockCapsule blockCapsule = getBlockByNum(num);
      final List<LogInfo> logInfoList = getLogInfoList(getTransactioninfoList(blockCapsule));
      parseTrc20Map(blockCapsule, logInfoList, tokenMap);
    }

    return tokenMap;
  }

  private static BlockCapsule getBlockByNum(long num) {
    BlockCapsule blockCapsule = null;
    try {
      blockCapsule = blockStore.get(blockIndexStore.get(num).getBytes());
    } catch (Exception e) {
      logger.error(" >>> get block error, num:{}", num);
    }
    return blockCapsule;
  }

  private static List<Protocol.TransactionInfo> getTransactioninfoList(BlockCapsule blockCapsule) {
    List<Protocol.TransactionInfo> ret = new ArrayList<>();
    Map<ByteString, Protocol.TransactionInfo> retMap = new HashMap<>();
    TransactionRetCapsule retCapsule = null;
    try {
      retCapsule = transactionRetStore
              .getTransactionInfoByBlockNum(ByteArray.fromLong(blockCapsule.getNum()));
      if (retCapsule != null) {
        for (Protocol.TransactionInfo transactionResultInfo : retCapsule.getInstance()
                .getTransactioninfoList()) {
          ret.add(transactionResultInfo);
          retMap.put(transactionResultInfo.getId(), transactionResultInfo);
        }
      }
    } catch (BadItemException e) {
      logger.error("TRC20Parser: block: {} parse error ", blockCapsule.getNum());
    }
    //front check: if ret.size == block inner tx size
    if (blockCapsule.getTransactions().size() != ret.size()) {
      for (TransactionCapsule capsule : blockCapsule.getTransactions()) {
        if (retMap.get(capsule.getTransactionId().getByteString()) == null) {
          try {
            TransactionInfoCapsule infoCapsule = transactionHistoryStore
                    .get(capsule.getTransactionId().getBytes());
            if (infoCapsule != null) {
              ret.add(infoCapsule.getInstance());
            }
          } catch (BadItemException e) {
            logger.error("TRC20Parser: txid: {} parse from transactionHistoryStore error ",
                    capsule.getTransactionId());
          }
        }
      }
    }
    return ret;
  }

  private static List<LogInfo> getLogInfoList(List<Protocol.TransactionInfo> transactionInfos) {
    List<LogInfo> ret = new ArrayList<>();
    for (Protocol.TransactionInfo transactionInfo : transactionInfos) {
      List<Protocol.TransactionInfo.Log> logs = transactionInfo.getLogList();
      for (Protocol.TransactionInfo.Log l : logs) {
        List<DataWord> topics = new ArrayList<>();
        for (ByteString b : l.getTopicsList()) {
          topics.add(new DataWord(b.toByteArray()));
        }
        LogInfo logInfo = new LogInfo(l.getAddress().toByteArray(), topics,
                l.getData().toByteArray());
        ret.add(logInfo);
      }
    }
    return ret;
  }

  public static void parseTrc20Map(BlockCapsule block, List<LogInfo> logInfos, Map<String, TreeSet<String>> tokenMap) {
    for (LogInfo logInfo : logInfos) {
      List<String> topics = logInfo.getHexTopics();
      if (topics == null) {
        continue;
      }
      if (topics.size() >= 3 && topics.get(0).equals(ConcernTopics.TRANSFER.getSignHash())) {
        //TransferCase : decrease sender, increase receiver
        String senderAddr = MUtil
                .encode58Check(MUtil.convertToTronAddress(logInfo.getTopics().get(1).getLast20Bytes()));
        String recAddr = MUtil
                .encode58Check(MUtil.convertToTronAddress(logInfo.getTopics().get(2).getLast20Bytes()));
        String tokenAddress = MUtil
                .encode58Check(MUtil.convertToTronAddress(logInfo.getAddress()));

        TreeSet<String> treeSet = tokenMap.get(tokenAddress);
        if (treeSet == null) {
          treeSet = new TreeSet<String>();
          tokenMap.put(tokenAddress, treeSet);
        }

        treeSet.add(senderAddr);
        treeSet.add(recAddr);
      }
    }
  }

  public static BigInteger getTRC20Balance(String ownerAddress, String contractAddress,
                                           BlockCapsule baseBlockCap) {
    byte[] data = Bytes.concat(Hex.decode("70a082310000000000000000000000"),
            Commons.decodeFromBase58Check(ownerAddress));
    ProgramResult result = triggerFromVM(contractAddress, data, baseBlockCap);
    if (result.getResultCode().equals(Protocol.Transaction.Result.contractResult.SUCCESS) &&
            !result.isRevert() && StringUtils.isEmpty(result.getRuntimeError())
            && result.getHReturn() != null) {
      try {
        BigInteger ret = toBigInteger(result.getHReturn());
        return ret;
      } catch (Exception e) {
      }
    }
    return null;
  }

  public static BigInteger getTRC20Decimal(String contractAddress, BlockCapsule baseBlockCap) {
    byte[] data = Hex.decode("313ce567");
    ProgramResult result = triggerFromVM(contractAddress, data, baseBlockCap);
    if (result.getResultCode().equals(Protocol.Transaction.Result.contractResult.SUCCESS) && !result.isRevert() && StringUtils
            .isEmpty(result.getRuntimeError())
            && result.getHReturn() != null) {
      try {
        BigInteger ret = toBigInteger(result.getHReturn());
        return ret;
      } catch (Exception e) {
      }
    }
    return null;

  }

  private static ProgramResult triggerFromVM(String contractAddress, byte[] data,
                                             BlockCapsule baseBlockCap) {
    SmartContractOuterClass.TriggerSmartContract.Builder build = SmartContractOuterClass.TriggerSmartContract.newBuilder();
    build.setData(ByteString.copyFrom(data));
    build.setOwnerAddress(ByteString.EMPTY);
    build.setCallValue(0);
    build.setCallTokenValue(0);
    build.setTokenId(0);
    build.setContractAddress(ByteString.copyFrom(Commons.decodeFromBase58Check(contractAddress)));
    TransactionCapsule trx = new TransactionCapsule(build.build(),
            Protocol.Transaction.Contract.ContractType.TriggerSmartContract);
    Protocol.Transaction.Builder txBuilder = trx.getInstance().toBuilder();
    Protocol.Transaction.raw.Builder rawBuilder = trx.getInstance().getRawData().toBuilder();
    rawBuilder.setFeeLimit(1000000000L);
    txBuilder.setRawData(rawBuilder);

    TransactionContext context = new TransactionContext(baseBlockCap,
            new TransactionCapsule(txBuilder.build()),
            StoreFactory.getInstance(), true,
            false);
    VMActuator vmActuator = new VMActuator(true);

    try {
      vmActuator.validate(context);
      vmActuator.execute(context);
    } catch (Exception e) {
      logger.warn("{} trigger failed!", contractAddress);
    }

    ProgramResult result = context.getProgramResult();
    return result;
  }

  private static BigInteger toBigInteger(byte[] input) {
    if (input != null && input.length > 0) {
      try {
        String hex = Hex.toHexString(input);
        return hexStrToBigInteger(hex);
      } catch (Exception e) {
      }
    }
    return null;
  }

  private static String bigIntegertoString(BigInteger bigInteger) {
    if (bigInteger != null) {
      return bigInteger.toString();
    }
    return null;
  }

  public static BigInteger hexStrToBigInteger(String hexStr) {
    if (!StringUtils.isEmpty(hexStr)) {
      try {
        return new BigInteger(hexStr, 16);
      } catch (Exception e) {
      }
    }
    return null;
  }



  @Data
  public static class AssetStatusPojo {

    private String accountAddress;
    private String tokenAddress;
    private String balance;
    private String incrementBalance;
    private String decimals;
  }

  public static enum ConcernTopics {
    TRANSFER("Transfer(address,address,uint256)",
            "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");

    @Getter
    private String sign;
    @Getter
    private String signHash;


    ConcernTopics(String sign, String signHash) {
      this.sign = sign;
      this.signHash = signHash;
    }

  }

  public static void shutdown(final Application app) {
    logger.info("********register application shutdown hook********");
    Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
  }
}
