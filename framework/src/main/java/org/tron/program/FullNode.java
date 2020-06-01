package org.tron.program;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import java.io.File;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.sun.prism.shader.Solid_TextureYV12_Loader;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.util.CollectionUtils;
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
    System.out.println(" >>>>>>>>>>> start");
//    String dbPath = "/Users/tron/Downloads/output-directory";
    String dbPath = "/data/mainnetdb/output-directory";
    args = new String[] {"-d", dbPath};
//    String conf = Constant.TESTNET_CONF;
//    String conf = "/Users/tron/Downloads/main_net_config.conf";
    String conf = "/home/java-tron/main_net_config.conf";
    Args.setParam(args, conf);
    CommonParameter parameter = Args.getInstance();

    load(parameter.getLogbackPath());
    System.out.println(" >>>>>>>>>>> load");

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
    TronApplicationContext context = new TronApplicationContext(beanFactory);
    context.register(DefaultConfig.class);
    System.out.println(" >>>>>>>>>>> context");

    long l1 = System.currentTimeMillis();
    context.refresh();
    long l2 = System.currentTimeMillis();
    System.out.println(" >>>>>>>>>>> context refresh, cost:" + (l2 - l1));
    Application appT = ApplicationFactory.create(context);
    shutdown(appT);
    System.out.println(" >>>>>>>>>>> shutdown");

    final Manager dbManager = appT.getDbManager();
    blockStore = dbManager.getBlockStore();
    blockIndexStore = dbManager.getBlockIndexStore();
    transactionRetStore = dbManager.getTransactionRetStore();
    transactionHistoryStore = dbManager.getTransactionHistoryStore();



    final long headBlockNum = dbManager.getHeadBlockNum();
    System.out.println(" >>>>>>>>>>> headBlockNum" + headBlockNum);

    Map<String, Map<String, Long>> tokenMap = new ConcurrentHashMap<>();
    handlerMap(headBlockNum, tokenMap);
    System.out.println(" >>> tokenMap.size:{}" + tokenMap.keySet().size());

//    final long count = tokenMap.entrySet().stream().mapToInt(item -> item.getValue().size()).count();
//    tokenMap.forEach((k, v) -> {
//      System.out.println(" >>>>>>>> tokenMap,k:" + k + ", set.size:" + v.size());
//    });
    final long sum = tokenMap.values().stream().mapToLong(item -> item.size()).sum();
    System.out.println(" >>> tokenMap.size:{}" + sum);
//    System.out.println(" >>> tokenMap.val.size:{}" + count);

    handlerMapToDB(headBlockNum, tokenMap);


    System.out.println(" >>>>>>>>>>> main is end!!!!!!!!");
    System.exit(0);
  }

  private static void handlerMapToDB(long headBlockNum, Map<String, Map<String, Long>> tokenMap) {
    final BlockCapsule blockCapsule = getBlockByNum(headBlockNum);
//    SyncDataToDB syncDataToDB = new SyncDataToDB();
    final AtomicInteger count = new AtomicInteger();

    tokenMap.forEach((tokenAddress, treeSet) -> {
      try {
        if (count.get() > 100) {
          return;
        }

        treeSet.forEach((accountAddress, blockNum) -> {
          if (count.getAndIncrement() > 100) {
            return;
          }
//          BlockCapsule blockCapsule = getBlockByNum(blockNum);
          final BigInteger trc20Decimal = getTRC20Decimal(tokenAddress, blockCapsule);
          final BigInteger trc20Balance = getTRC20Balance(accountAddress, tokenAddress, blockCapsule);
          System.out.println(" >>> token:" + tokenAddress + ", acc:" + accountAddress + ",banlace:" + trc20Balance + ", dec:" + trc20Decimal);

  //        syncDataToDB.save(tokenAddress, accountAddress, headBlockNum, trc20Balance, trc20Decimal.intValue());
        });

      }
      catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    });
  }

  private static void handlerMap(long headBlockNum, Map<String, Map<String, Long>> tokenMap) {
    long l1 = System.currentTimeMillis();


    LongStream.range(headBlockNum - 50000, headBlockNum - 10000).forEach(item -> {
      parseTrc20Map(item, tokenMap);

      if (item % 1000 == 0) {
        System.out.println(" >>>>>>>>>>> handlerMap, num:" + item + ", time:" + System.currentTimeMillis());
      }
    });
//    for (long num = 1; num <= headBlockNum; num++) {
//      parseTrc20Map(num, tokenMap);
//
//      if (num % 10000 == 0) {
//        long l2 = System.currentTimeMillis();
//        System.out.println(" >>>>>>>>>>> handlerMap, num:" + num + ", time:" + (l2 - l1));
//        l1 = l2;
//      }
//    }
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

  public static void parseTrc20Map(Long blockNum, Map<String, Map<String, Long>> tokenMap) {
    try {
      TransactionRetCapsule retCapsule = transactionRetStore
              .getTransactionInfoByBlockNum(ByteArray.fromLong(blockNum));
      if (retCapsule != null) {
        for (Protocol.TransactionInfo transactionResultInfo : retCapsule.getInstance().getTransactioninfoList()) {
          List<Protocol.TransactionInfo.Log> logs = transactionResultInfo.getLogList();
          for (Protocol.TransactionInfo.Log l : logs) {
            handlerToMap(blockNum, l, tokenMap);
          }
        }
      }
    } catch (BadItemException e) {
      logger.error("TRC20Parser: block: {} parse error ", blockNum);
    }
  }

  private static void handlerToMap(Long blockNum, Protocol.TransactionInfo.Log log,
                                   Map<String, Map<String, Long>> tokenMap) {
    final List<ByteString> topicsList = log.getTopicsList();

    if (CollectionUtils.isEmpty(topicsList) || topicsList.size() < 3) {
      return;
    }

    final String topic0 = new DataWord(topicsList.get(0).toByteArray()).toHexString();

    if (!Objects.equals(topic0, ConcernTopics.TRANSFER.getSignHash())) {
      return;
    }

    //TransferCase : decrease sender, increase receiver
    String senderAddr = MUtil
            .encode58Check(MUtil.convertToTronAddress(new DataWord(topicsList.get(1).toByteArray()).getLast20Bytes()));
    String recAddr = MUtil
            .encode58Check(MUtil.convertToTronAddress(new DataWord(topicsList.get(2).toByteArray()).getLast20Bytes()));
    String tokenAddress = MUtil
            .encode58Check(MUtil.convertToTronAddress(log.getAddress().toByteArray()));

    Map<String, Long> treeSet = tokenMap.get(tokenAddress);
    if (treeSet == null) {
      treeSet = new ConcurrentHashMap();
      tokenMap.put(tokenAddress, treeSet);
    }

    treeSet.put(senderAddr, blockNum);
    treeSet.put(recAddr, blockNum);
  }

  public static BigInteger getTRC20Balance(String ownerAddress, String contractAddress,
                                           BlockCapsule baseBlockCap) {
    byte[] data = Bytes.concat(Hex.decode("70a082310000000000000000000000"),
            Commons.decodeFromBase58Check(ownerAddress));
    ProgramResult result = triggerFromVM(contractAddress, data, baseBlockCap);
    if (result != null
            &&!result.isRevert() && StringUtils.isEmpty(result.getRuntimeError())
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
    if (result != null
            && !result.isRevert() && StringUtils.isEmpty(result.getRuntimeError())
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
    if (result != null) {
      System.out.println("  >>> rsult:" + result.getResultCode() + ", " + result.isRevert()
              + ", " + result.getRuntimeError() + ", " + result.getHReturn());
    }

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
