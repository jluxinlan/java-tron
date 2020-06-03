package org.tron.program;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.tron.core.capsule.BlockCapsule;
import redis.clients.jedis.Jedis;

import java.math.BigInteger;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public class SyncDataToDB {

  // todo 配置文件
  private static String uri = "jdbc:mysql://127.0.0.1:33067/tronlink_dev";
  private static String userName = "root";
  private static String password = "";

  private static Connection connect = null;

  private Connection getConnection() {
    try {
      if (connect != null) {
        return connect;
      }

      Class.forName("com.mysql.jdbc.Driver");
      // Setup the connection with the DB
      connect = DriverManager.getConnection(uri, userName, null);
      connect.setAutoCommit(true);
      return connect;
    } catch (Exception e) {
      System.out.println(" >>> create conn error");
      logger.error(e.getMessage(), e);
    }

    return null;
  }

  @Data
  @AllArgsConstructor
  public static class BalanceInfo {
    Long id;
    String tokenAddress;
    String accountAddress;
    Long blockNum;
    BigInteger balance;
    Integer decimals;
  }

  private static final String querySql = "select id from balance_info where account_address = ? and token_address = ?";
  public void saveAll(ConcurrentLinkedQueue<BalanceInfo> queue) {
    Connection connection = getConnection();
    try {
      List<BalanceInfo> insertInfos = new LinkedList<>();
      List<BalanceInfo> updateInfos = new LinkedList<>();

      PreparedStatement statement = connection.prepareStatement(querySql);
      while (!queue.isEmpty()) {
        try {
          BalanceInfo info = queue.poll();
          statement.setString(1, info.getAccountAddress());
          statement.setString(2, info.getTokenAddress());
          final ResultSet resultSet = statement.executeQuery();
          if (resultSet.next()) {
            // 有数据就 update
            final long id = resultSet.getLong(1);
            info.setId(id);
            updateInfos.add(info);
          }
          else {
            insertInfos.add(info);
          }
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }

      insert(connection, insertInfos);
      update(connection, updateInfos);
    } catch (Exception e) {
      logger.error("", e);
    }
  }

  private static final String insertSql = "insert into balance_info (account_address, token_address, balance, block_num, solidity_balance, solidity_block_num, decimals, version, created_time, updated_time) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private void insert(Connection connection, List<BalanceInfo> infos) {
    if (CollectionUtils.isEmpty(infos)) {
      return;
    }

    try {
      PreparedStatement preparedStatement = connection.prepareStatement(insertSql);

      infos.forEach(info -> {
        try {
          preparedStatement.setString(1, info.accountAddress);
          preparedStatement.setString(2, info.tokenAddress);
          preparedStatement.setString(3, info.balance.toString());
          preparedStatement.setLong(4, info.blockNum);
          preparedStatement.setString(5, info.balance.toString());
          preparedStatement.setLong(6, info.blockNum);
          preparedStatement.setString(7, info.decimals.toString());
          Timestamp now = Timestamp.valueOf(LocalDateTime.now());
          preparedStatement.setLong(8, 1);
          preparedStatement.setTimestamp(9, now);
          preparedStatement.setTimestamp(10, now);
          preparedStatement.addBatch();
        } catch (SQLException e) {
          logger.error("", e);
        }
      });

      preparedStatement.executeUpdate();
    }
    catch (Exception ex) {
      logger.error("", ex);
    }
  }


  private static final String updatSql = "update balance_info set balance =?, block_num =?, solidity_balance =?, solidity_block_num =?, decimals =?, updated_time =?  where id = ?";
  private void update (Connection connection, List<BalanceInfo> infos) {
    try {
      if (CollectionUtils.isEmpty(infos)) {
        return;
      }

      PreparedStatement preparedStatement = connection.prepareStatement(updatSql);

      infos.forEach(info -> {
        try {
          preparedStatement.setString(1, info.getBalance().toString());
          preparedStatement.setLong(2, info.blockNum);
          preparedStatement.setString(3, info.balance.toString());
          preparedStatement.setLong(4, info.blockNum);
          preparedStatement.setString(5, info.decimals.toString());
          Timestamp now = Timestamp.valueOf(LocalDateTime.now());
          preparedStatement.setTimestamp(6, now);
          preparedStatement.setLong(7, info.id);
          preparedStatement.addBatch();
        }
        catch (Exception e) {
          logger.error("", e);
        }
      });

      preparedStatement.executeUpdate();
    } catch (SQLException e) {
      logger.error("",  e);
    }
  }


  private Jedis getConn() {
    Jedis jedis = new Jedis("127.0.0.1", 63791);
    jedis.auth("defi-redis");
//    Jedis jedis = new Jedis("127.0.0.1");
    System.out.println("Connected to Redis");
    return jedis;
  }

  public static final String INIT_KEY = "tron-link-data-init";
  public static final String BLOCK_CURRENT_NUM = "tron-link-current-num";
  public static final String BLOCK_CURRENT_HASH = "tron-link-current-hash";
  public static final String BLOCK_CURRENT_SOLIDITY_NUM = "tron-link-current-solidity-num";

  public void syncDataToRedis(BlockCapsule blockCapsule) {
    try {
      final Jedis conn = getConn();
      conn.set(INIT_KEY, "true");
      conn.set(BLOCK_CURRENT_NUM, "" + blockCapsule.getNum());
      conn.set(BLOCK_CURRENT_HASH, "" + blockCapsule.getBlockId().toString());
      conn.set(BLOCK_CURRENT_SOLIDITY_NUM, "" + blockCapsule.getNum());
      System.out.println(" >>>>> syncDataToRedis success. num:" + blockCapsule.getNum());
    }
    catch (Exception ex) {
      System.out.println(" >>>> update redis error !!!");
      logger.error(" update redis error, num:" + blockCapsule.getNum(),  ex);
    }
  }
}
