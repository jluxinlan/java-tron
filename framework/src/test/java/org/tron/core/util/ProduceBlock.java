package org.tron.core.util;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import org.tron.common.utils.ByteArray;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.capsule.TransactionInfoCapsule;
import org.tron.core.db.Manager;
import org.tron.protos.Protocol;
import org.tron.protos.contract.BalanceContract;

public class ProduceBlock {


    public static void addTransactionToStore(Protocol.Transaction transaction, Manager manager) {
        TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
        manager.getTransactionStore()
                .put(transactionCapsule.getTransactionId().getBytes(), transactionCapsule);
    }

    public static void addTransactionInfoToStore(Protocol.Transaction transaction, Manager manager) {
        TransactionInfoCapsule transactionInfo = new TransactionInfoCapsule();
        byte[] trxId = transaction.getRawData().toByteArray();
        transactionInfo.setId(trxId);
        manager.getTransactionHistoryStore().put(trxId, transactionInfo);
    }


    public static Protocol.Transaction getBuildTransaction(
            BalanceContract.TransferContract transferContract, long transactionTimestamp, long refBlockNum) {
        return Protocol.Transaction.newBuilder().setRawData(
                Protocol.Transaction.raw.newBuilder().setTimestamp(transactionTimestamp)
                        .setRefBlockNum(refBlockNum)
                        .addContract(
                                Protocol.Transaction.Contract.newBuilder().setType(Protocol.Transaction.Contract.ContractType.TransferContract)
                                        .setParameter(Any.pack(transferContract)).build()).build())
                .build();
    }

    public static BalanceContract.TransferContract getBuildTransferContract(String ownerAddress, String toAddress) {
        return BalanceContract.TransferContract.newBuilder().setAmount(10)
                .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
                .setToAddress(ByteString.copyFrom(ByteArray.fromHexString(toAddress))).build();
    }


    public static void addBlockToStore(Protocol.Block block, Manager manager) {
        BlockCapsule blockCapsule = new BlockCapsule(block);
        manager.getBlockStore().put(blockCapsule.getBlockId().getBytes(), blockCapsule);
    }

    public static Protocol.Block getBuildBlock(long timestamp, long num, long witnessId,
                                               String witnessAddress, Protocol.Transaction transaction, Protocol.Transaction transactionNext) {
        return Protocol.Block.newBuilder().setBlockHeader(Protocol.BlockHeader.newBuilder().setRawData(
                Protocol.BlockHeader.raw.newBuilder().setTimestamp(timestamp).setNumber(num).setWitnessId(witnessId)
                        .setWitnessAddress(ByteString.copyFrom(ByteArray.fromHexString(witnessAddress)))
                        .build()).build()).addTransactions(transaction).addTransactions(transactionNext)
                .build();
    }

    public static Protocol.Block getBuildBlock(long timestamp, long num, long witnessId,
                                               String witnessAddress, Protocol.Transaction transaction) {
        return Protocol.Block.newBuilder().setBlockHeader(Protocol.BlockHeader.newBuilder().setRawData(
                Protocol.BlockHeader.raw.newBuilder().setTimestamp(timestamp).setNumber(num).setWitnessId(witnessId)
                        .setWitnessAddress(ByteString.copyFrom(ByteArray.fromHexString(witnessAddress)))
                        .build()).build()).addTransactions(transaction)
                .build();
    }

}
