package org.tron.core;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import org.spongycastle.util.encoders.Hex;
import org.springframework.util.CollectionUtils;
import org.tron.api.GrpcAPI;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.storage.DepositImpl;
import org.tron.common.utils.*;
import org.tron.core.capsule.*;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.consensus.ConsensusService;
import org.tron.core.db.BlockStore;
import org.tron.core.db.Manager;
import org.tron.core.db.TransactionHistoryStore;
import org.tron.core.store.AccountStore;
import org.tron.core.store.ContractStore;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.Protocol.TransactionInfo;
import org.tron.protos.contract.SmartContractOuterClass;
import stest.tron.wallet.common.client.WalletClient;
import stest.tron.wallet.common.client.utils.DataWord;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.tron.core.vm.utils.MUtil.convertToTronAddress;

@Slf4j
public class ForReadDataTest {

  private static String fullnode = "127.0.0.1:50051";
  private static String soliditynode = "127.0.0.1:50061";
  private static String dbPath = "/Users/tron/IdeaProjects/java-tron-work/build/libs/output-directory";
  private static TronApplicationContext context;
  private static Wallet wallet;
  private static Manager manager;
  private static DepositImpl deposit;
  private static ConsensusService consensusService;

  private static Transaction transaction1;
  private static Transaction transaction2;
  private static ByteString trId1;
  private static ByteString trId2;
  private static byte[] ownerAddressBytes = null;

  private static String trc20ContractAddress = "TX89RWqH3gX3m5wvZvSDaedyLBF4SzZqy5";
  private static String shieldedTRC20ContractAddress = "TB8MuSWh979b4donqWUZtFJ4aYAemZ1R6U";
  private static String privateKey = "650950B193DDDDB35B6E48912DD28F7AB0E7140C1BFDEFD493348F02295BD812";
  private static String pubAddress = "TFsrP7YcSSRwHzLPwaCnXyTKagHs8rXKNJ";


  static {
    String conf = "/Users/tron/Downloads/main_net_config.conf";
    Args.setParam(new String[]{"-d", dbPath}, conf);
    context = new TronApplicationContext(DefaultConfig.class);
  }

  @BeforeClass
  public static void beforeClass() {
//    ownerAddressBytes = WalletClient.decodeFromBase58Check(pubAddress);
//    wallet = context.getBean(Wallet.class);
//    manager = context.getBean(Manager.class);
//    consensusService = context.getBean(ConsensusService.class);
//    consensusService.start();
  }

  @Test
  public void forTest() {
    logger.info(" ------ forTest start ------- ");
    BlockStore blockStore = context.getBean(BlockStore.class);
    TransactionHistoryStore trxHis = context.getBean(TransactionHistoryStore.class);

    List<BlockCapsule> blocks = blockStore.getLimitNumber(0, 10);
    logger.info(" >>> blockByLatestNum:{}", blocks.size());

    blocks.stream().forEach(block -> {
      final List<TransactionCapsule> transactions = block.getTransactions();

      if (CollectionUtils.isEmpty(transactions)) {
        logger.info(" >>> block no trx, num: {}", block.getNum());
      }

      transactions.stream().forEach(transaction -> {
        final Sha256Hash transactionId = transaction.getTransactionId();
        try {
          final TransactionInfoCapsule transactionInfoCapsule = trxHis.get(transactionId.getBytes());

          if (transactionInfoCapsule == null || transactionInfoCapsule.getInstance() == null
                  || transactionInfoCapsule.getInstance().getLogCount() < 0) {
            logger.info(" >>>>>>>> transactionInfoCapsule:{}", transactionInfoCapsule);
            return;
          }

          logger.info(" >>> has val");
          final TransactionInfo.Log log = transactionInfoCapsule.getInstance().getLogList().get(0);
          final String myAddress = WalletUtil.encode58Check(convertToTronAddress(new DataWord(log.getAddress().toByteArray()).getLast20Bytes()));
          logger.info(" >>>> address :{}", log.getAddress().toString()); // 20币地址
          logger.info(" >>>> address :{}", myAddress); // 20币地址
          log.getTopicsList().get(0); // 方法名 transferTo(from, to)
          log.getTopics(1);// from的地址
          //ByteArray

//          final String myAddress = WalletUtil.encode58Check(convertToTronAddress(new DataWord(log.getTopics(1).toByteArray()).getLast20Bytes()));

          log.getTopics(2); // to地址
          log.getData(); // val金额

        } catch (Exception e) {
          e.printStackTrace();
        }
      });
    });

    AccountStore accountStore = context.getBean(AccountStore.class);
    ContractStore contractStore = context.getBean(ContractStore.class);
    logger.info(" >>>> account.size:{}", accountStore.size());
    logger.info(" >>>> contractStore.size:{}", contractStore.size());
    Set set = new TreeSet();

    accountStore.forEach(item -> {
      final Protocol.AccountType type = item.getValue().getType();
//      set.add(type.getValueDescriptor());

      // 不是普通账户则返回
      if (type.getNumber() != 0) {
        logger.info(" >>> {}, {}, {}", type.getNumber(), type.getValueDescriptor());
        return;
      }

//      String accountAddress = WalletClient.encode58Check(item.getKey());
//      logger.info(" >>>> accountAddress:{}", accountAddress);
//
//      contractStore.forEach(token -> {
//        String tokenAddress = WalletClient.encode58Check(token.getKey());
//        logger.info(" >>>> tokenAddress:{}", tokenAddress);
//        balanceOfTest(tokenAddress, accountAddress);
//      });
    });

    logger.info(" >>> set:{}", set);

    logger.info(" ------ forTest end ------- ");
  }

  public void balanceOfTest(String tokenAddress, String accountAddress) {
    byte[] contractAddress = WalletClient.decodeFromBase58Check(tokenAddress);
    byte[] userAccountAddress = new byte[32];
    byte[] shieldedContractAddress = WalletClient.decodeFromBase58Check(accountAddress);
    System.arraycopy(shieldedContractAddress, 0, userAccountAddress, 11, 21);
    String methodSign = "balanceOf(address)";
    byte[] selector = new byte[4];
    System.arraycopy(Hash.sha3(methodSign.getBytes()), 0, selector, 0, 4);
    byte[] input = ByteUtil.merge(selector, userAccountAddress);

    SmartContractOuterClass.TriggerSmartContract.Builder triggerBuilder = SmartContractOuterClass.TriggerSmartContract.newBuilder();
    triggerBuilder.setContractAddress(ByteString.copyFrom(contractAddress));
    triggerBuilder.setData(ByteString.copyFrom(input));

    try {
      TransactionCapsule trxCap = wallet.createTransactionCapsule(triggerBuilder.build(),
              Transaction.Contract.ContractType.TriggerSmartContract);
      GrpcAPI.TransactionExtention.Builder trxExtBuilder = GrpcAPI.TransactionExtention.newBuilder();
      GrpcAPI.Return.Builder retBuilder = GrpcAPI.Return.newBuilder();

      final Transaction transaction = wallet.triggerConstantContract(triggerBuilder.build(), trxCap, trxExtBuilder, retBuilder);
      trxExtBuilder.setTransaction(transaction);
      trxExtBuilder.setTxid(trxCap.getTransactionId().getByteString());
      String result = mergeResult(trxExtBuilder.build());
      logger.info(" >>>>> balance:{}", result);
    } catch (Exception e) {
      logger.error(" >>> trigger contract error:", e);
    }
  }

  private String mergeResult(GrpcAPI.TransactionExtention trxExt2) {
    List<ByteString> list = trxExt2.getConstantResultList();
    byte[] listBytes = new byte[0];
    for (ByteString bs : list) {
      listBytes = ByteUtil.merge(listBytes, bs.toByteArray());
    }

    return Hex.toHexString(listBytes);
  }


}
