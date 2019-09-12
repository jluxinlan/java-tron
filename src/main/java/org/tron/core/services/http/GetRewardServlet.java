package org.tron.core.services.http;

import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.core.Wallet;
import org.tron.core.db.Manager;


@Component
@Slf4j(topic = "API")
public class GetRewardServlet extends HttpServlet {

  @Autowired
  private Manager manager;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      long value = 0;
      byte[] address = getAddress(request);
      if (address != null) {
        value = manager.getDelegationService().queryReward(address);
      }
      response.getWriter().println(value);
    } catch (Exception e) {
      logger.error("", e);
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    doGet(request, response);
  }

  private byte[] getAddress(HttpServletRequest request) {
    byte[] address = null;
    String addressStr = request.getParameter("address");
    if (StringUtils.isNotBlank(addressStr)) {
      address = Wallet.decodeFromBase58Check(addressStr);
    }
    return address;
  }
}