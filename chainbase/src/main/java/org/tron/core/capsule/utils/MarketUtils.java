/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.tron.core.capsule.utils;

import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.util.Arrays;
import org.tron.common.crypto.Hash;
import org.tron.common.utils.ByteArray;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.MarketAccountOrderCapsule;
import org.tron.core.capsule.MarketOrderCapsule;
import org.tron.core.capsule.MarketPriceCapsule;
import org.tron.core.exception.ItemNotFoundException;
import org.tron.core.store.AssetIssueStore;
import org.tron.core.store.DynamicPropertiesStore;
import org.tron.core.store.MarketAccountStore;
import org.tron.protos.Protocol.MarketOrder.State;
import org.tron.protos.Protocol.MarketOrderPair;
import org.tron.protos.Protocol.MarketPrice;

public class MarketUtils {

  public static final int TOKEN_ID_LENGTH = ByteArray
      .fromString(Long.toString(Long.MAX_VALUE)).length; // 19


  public static byte[] calculateOrderId(ByteString address, byte[] sellTokenId,
      byte[] buyTokenId, long count) {

    byte[] addressByteArray = address.toByteArray();
    byte[] countByteArray = ByteArray.fromLong(count);

    byte[] result = new byte[addressByteArray.length + TOKEN_ID_LENGTH
        + TOKEN_ID_LENGTH + countByteArray.length];

    System.arraycopy(addressByteArray, 0, result, 0, addressByteArray.length);
    System.arraycopy(sellTokenId, 0, result, addressByteArray.length, sellTokenId.length);
    System.arraycopy(buyTokenId, 0, result, addressByteArray.length + TOKEN_ID_LENGTH,
        buyTokenId.length);
    System.arraycopy(countByteArray, 0, result, addressByteArray.length
        + TOKEN_ID_LENGTH + TOKEN_ID_LENGTH, countByteArray.length);

    return Hash.sha3(result);
  }

  public static byte[] createPairPriceKey(byte[] sellTokenId, byte[] buyTokenId,
      long sellTokenQuantity, long buyTokenQuantity) {

//    byte[] pairKey = new byte[TOKEN_ID_LENGTH + TOKEN_ID_LENGTH];
//    System.arraycopy(sellTokenId, 0, pairKey, 0, sellTokenId.length);
//    System.arraycopy(buyTokenId, 0, pairKey, TOKEN_ID_LENGTH, buyTokenId.length);
//    byte[] pairKeyHash = Hash.sha3(pairKey);

    byte[] sellTokenQuantityBytes = ByteArray.fromLong(sellTokenQuantity);
    byte[] buyTokenQuantityBytes = ByteArray.fromLong(buyTokenQuantity);
    byte[] result = new byte[TOKEN_ID_LENGTH + TOKEN_ID_LENGTH
        + sellTokenQuantityBytes.length + buyTokenQuantityBytes.length];

    System.arraycopy(sellTokenId, 0, result, 0, sellTokenId.length);
    System.arraycopy(buyTokenId, 0, result, TOKEN_ID_LENGTH, buyTokenId.length);
    System.arraycopy(sellTokenQuantityBytes, 0, result,
        TOKEN_ID_LENGTH + TOKEN_ID_LENGTH,
        sellTokenQuantityBytes.length);
    System.arraycopy(buyTokenQuantityBytes, 0, result,
        TOKEN_ID_LENGTH + TOKEN_ID_LENGTH + sellTokenQuantityBytes.length,
        buyTokenQuantityBytes.length);

//    return Hash.sha3(result);
    return result;
  }

  /**
   * 0...18: sellTokenId
   * 19...37: buyTokenId
   * 38...45: sellTokenQuantity
   * 46...53: buyTokenQuantity
   * */
  public static MarketPrice decodeKeyToMarketPrice(byte[] key) {
    byte[] sellTokenQuantity = new byte[8];
    byte[] buyTokenQuantity = new byte[8];

    System.arraycopy(key, 38, sellTokenQuantity, 0, 8);
    System.arraycopy(key, 46, buyTokenQuantity, 0, 8);

    return new MarketPriceCapsule(ByteArray.toLong(sellTokenQuantity),
        ByteArray.toLong(buyTokenQuantity)).getInstance();
  }

  public static MarketOrderPair decodeKeyToMarketPair(byte[] key) {
    byte[] sellTokenId = new byte[TOKEN_ID_LENGTH];
    byte[] buyTokenId = new byte[TOKEN_ID_LENGTH];

    System.arraycopy(key, 0, sellTokenId, 0, TOKEN_ID_LENGTH);
    System.arraycopy(key, 18, buyTokenId, 0, TOKEN_ID_LENGTH);

    MarketOrderPair.Builder builder = MarketOrderPair.newBuilder();
    builder.setSellTokenId(ByteString.copyFrom(sellTokenId))
        .setBuyTokenId(ByteString.copyFrom(buyTokenId));

    return builder.build();
  }

  public static byte[] createPairKey(byte[] sellTokenId, byte[] buyTokenId) {
    byte[] result = new byte[TOKEN_ID_LENGTH * 2];
    System.arraycopy(sellTokenId, 0, result, 0, sellTokenId.length);
    System.arraycopy(buyTokenId, 0, result, TOKEN_ID_LENGTH, buyTokenId.length);
    return result;
  }

  /**
   * Note: the params should be the same token pair, or you should change the order
   * */
  public static int comparePrice(long price1SellQuantity, long price1BuyQuantity,
      long price2SellQuantity, long price2BuyQuantity) {
    try {
      return Long.compare(Math.multiplyExact(price1BuyQuantity, price2SellQuantity),
          Math.multiplyExact(price2BuyQuantity, price1SellQuantity));

    } catch (ArithmeticException ex) {
      // do nothing here, because we will use BigInteger to compute again
    }

    BigInteger price1BuyQuantityBI = BigInteger.valueOf(price1BuyQuantity);
    BigInteger price1SellQuantityBI = BigInteger.valueOf(price1SellQuantity);
    BigInteger price2BuyQuantityBI = BigInteger.valueOf(price2BuyQuantity);
    BigInteger price2SellQuantityBI = BigInteger.valueOf(price2SellQuantity);

    return price1BuyQuantityBI.multiply(price2SellQuantityBI)
        .compareTo(price2BuyQuantityBI.multiply(price1SellQuantityBI));
  }

  /**
   * ex.
   * for sellToken is A, buyToken is TRX.
   * price_A_maker * sellQuantity_maker = Price_TRX * buyQuantity_maker
   * ==> price_A_maker = Price_TRX * buyQuantity_maker/sellQuantity_maker
   *
   * price_A_maker_1 < price_A_maker_2
   * ==> buyQuantity_maker_1/sellQuantity_maker_1 < buyQuantity_maker_2/sellQuantity_maker_2
   * ==> buyQuantity_maker_1*sellQuantity_maker_2 < buyQuantity_maker_2 * sellQuantity_maker_1
   */
  public static int comparePrice(MarketPrice price1, MarketPrice price2) {
    return comparePrice(price1.getSellTokenQuantity(), price1.getBuyTokenQuantity(),
        price2.getSellTokenQuantity(), price2.getBuyTokenQuantity());
  }

  public static boolean isLowerPrice(MarketPrice price1, MarketPrice price2) {
    return comparePrice(price1, price2) == -1;
  }

  /**
   * if takerPrice >= makerPrice, return True
   * note: here are two different token pairs
   * firstly, we should change the token pair of taker to be the same with maker
   */
  public static boolean priceMatch(MarketPrice takerPrice, MarketPrice makerPrice) {
    // for takerPrice, buyToken is A,sellToken is TRX.
    // price_A_taker * buyQuantity_taker = Price_TRX * sellQuantity_taker
    // ==> price_A_taker = Price_TRX * sellQuantity_taker/buyQuantity_taker

    // price_A_taker must be greater or equal to price_A_maker
    // price_A_taker / price_A_maker >= 1
    // ==> Price_TRX * sellQuantity_taker/buyQuantity_taker >= Price_TRX * buyQuantity_maker/sellQuantity_maker
    // ==> sellQuantity_taker * sellQuantity_maker > buyQuantity_taker * buyQuantity_maker

    return comparePrice(takerPrice.getBuyTokenQuantity(), takerPrice.getSellTokenQuantity(),
        makerPrice.getSellTokenQuantity(), makerPrice.getBuyTokenQuantity()) >= 0;
  }

  public static void updateOrderState(MarketOrderCapsule orderCapsule,
      State state, MarketAccountStore marketAccountStore) throws ItemNotFoundException {
    orderCapsule.setState(state);

    // remove from account order list
    if (state == State.INACTIVE || state == State.CANCELED) {
      MarketAccountOrderCapsule accountOrderCapsule = marketAccountStore
          .get(orderCapsule.getOwnerAddress().toByteArray());
      accountOrderCapsule.removeOrder(orderCapsule.getID());
      marketAccountStore.put(accountOrderCapsule.createDbKey(), accountOrderCapsule);
    }
  }


  public static long multiplyAndDivide(long a, long b, long c) {
    try {
      long tmp = Math.multiplyExact(a, b);
      return Math.floorDiv(tmp, c);
    } catch (ArithmeticException ex) {

    }

    BigInteger aBig = BigInteger.valueOf(a);
    BigInteger bBig = BigInteger.valueOf(b);
    BigInteger cBig = BigInteger.valueOf(c);

    return aBig.multiply(bBig).divide(cBig).longValue();

  }

  // for taker
  public static void returnSellTokenRemain(MarketOrderCapsule orderCapsule, AccountCapsule accountCapsule,
      DynamicPropertiesStore dynamicStore, AssetIssueStore assetIssueStore) {
    byte[] sellTokenId = orderCapsule.getSellTokenId();
    long sellTokenQuantityRemain = orderCapsule.getSellTokenQuantityRemain();
    if (Arrays.equals(sellTokenId, "_".getBytes())) {
      accountCapsule.setBalance(Math.addExact(
          accountCapsule.getBalance(), sellTokenQuantityRemain));
    } else {
      accountCapsule
          .addAssetAmountV2(sellTokenId, sellTokenQuantityRemain, dynamicStore, assetIssueStore);
    }
    orderCapsule.setSellTokenQuantityRemain(0L);
  }

}