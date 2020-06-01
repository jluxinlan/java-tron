package org.tron.program;

import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.sql.*;
import java.time.LocalDateTime;

@Slf4j
public class SyncDataToDB {

  private static String uri = "jdbc:mysql://localhost:3306/tronlink_dev";
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
      connect = DriverManager.getConnection(uri, userName, password);
      connect.setAutoCommit(true);
      return connect;
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }

    return null;
  }

  private static final String querySql = "select id from balance_info where account_address = ? and token_address = ?";
  public void save(String tokenAddress, String accountAddress, Long blockNum, BigInteger balance, Integer decimals) {
    final Connection connection = getConnection();
    try {

      final PreparedStatement statement = connection.prepareStatement(querySql);
      statement.setString(1, accountAddress);
      statement.setString(2, tokenAddress);
      final ResultSet resultSet = statement.executeQuery();
      if (resultSet.next()) {
        // 有数据就 update
        final long id = resultSet.getLong(1);
        update(connection, id, blockNum, balance, decimals);
        return;
      }

      insert(connection, tokenAddress, accountAddress, blockNum, balance, decimals);
    } catch (Exception e) {
      logger.error(" save error, num:" + blockNum + ", account:" + accountAddress + ", token:" + tokenAddress, e);
    }
  }

  private static final String insertSql = "insert into balance_info (account_address, token_address, balance, block_num, solidity_balance, solidity_block_num, decimals, created_time, updated_time) values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private void insert(Connection connection, String tokenAddress, String accountAddress, Long blockNum, BigInteger balance, Integer decimals) {
    PreparedStatement preparedStatement = null;
    try {
      preparedStatement = connection.prepareStatement(insertSql);
      preparedStatement.setString(1, accountAddress);
      preparedStatement.setString(2, tokenAddress);
      preparedStatement.setString(3, balance == null ? "0": balance.toString());
      preparedStatement.setLong(4, blockNum);
      preparedStatement.setString(5, balance == null ? "0": balance.toString());
      preparedStatement.setLong(6, blockNum);
      preparedStatement.setInt(7, decimals);
      Timestamp now = Timestamp.valueOf(LocalDateTime.now());
      preparedStatement.setTimestamp(8, now);
      preparedStatement.setTimestamp(9, now);
      preparedStatement.execute();
    } catch (SQLException e) {
      logger.error(" insert error, num:" + blockNum + ", account:" + accountAddress + ", token:" + tokenAddress, e);
    }
  }

  private static final String updatSql = "update balance_info set balance =?, block_num =?, solidity_balance =?, solidity_block_num =?, decimals=?, updated_time =?  where id = ?";
  private void update (Connection connection, Long id, Long blockNum, BigInteger balance, Integer decimals) {
    PreparedStatement preparedStatement = null;
    try {
      preparedStatement = connection.prepareStatement(updatSql);
      preparedStatement.setString(1, balance == null ? "0": balance.toString());
      preparedStatement.setLong(2, blockNum);
      preparedStatement.setString(3, balance == null ? "0": balance.toString());
      preparedStatement.setLong(4, blockNum);
      preparedStatement.setInt(5, decimals);
      Timestamp now = Timestamp.valueOf(LocalDateTime.now());
      preparedStatement.setTimestamp(6, now);
      preparedStatement.setLong(7, id);
      preparedStatement.execute();
    } catch (SQLException e) {
      logger.error(" update error, num:" + blockNum + ", id:" + id,  e);
    }
  }
}
