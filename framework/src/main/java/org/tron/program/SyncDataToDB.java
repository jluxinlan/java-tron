package org.tron.program;

import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.sql.*;
import java.time.LocalDateTime;

@Slf4j
public class SyncDataToDB {

  private static String uri = "";
  private static String userName = "";
  private static String password = "";

  private static Connection connect = null;

  private Connection getConnection() {
    try {
      if (connect != null) {
        return connect;
      }

      Class.forName("com.mysql.jdbc.Driver");
      // Setup the connection with the DB
      connect = DriverManager.getConnection("jdbc:mysql://localhost:3306/tron", userName, password);
      connect.setAutoCommit(true);
      return connect;
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }

    return null;
  }


  private static final String insertSql = "insert into balance_info (account_address, token_address, balance, block_num, decimals, created_time, updated_time) values (?, ?, ?, ?, ?, ?, ?)";


  public void save(String tokenAddress, String accountAddress, Long blockNum, BigInteger balance, Integer decimals) {
    final Connection connection = getConnection();
    try {
      final PreparedStatement preparedStatement = connection.prepareStatement(insertSql);
      preparedStatement.setString(1, accountAddress);
      preparedStatement.setString(2, tokenAddress);
      preparedStatement.setString(3, balance == null ? "0": balance.toString());
      preparedStatement.setLong(4, blockNum);
      preparedStatement.setInt(5, decimals);
      Timestamp now = Timestamp.valueOf(LocalDateTime.now());
      preparedStatement.setTimestamp(6, now);
      preparedStatement.setTimestamp(7, now);
      preparedStatement.execute();
    } catch (Exception e) {
      logger.error(" insert error, num:" + blockNum + ", account:" + accountAddress + ", token:" + tokenAddress, e);
    }
  }
}
