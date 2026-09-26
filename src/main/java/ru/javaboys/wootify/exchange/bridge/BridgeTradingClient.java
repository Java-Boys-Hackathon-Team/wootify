package ru.javaboys.wootify.exchange.bridge;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * API моста для движка ботов. Учётные данные Orderly передаются заголовками, а не в строке запроса.
 */
@FeignClient(name = "woofiBridgeTradingClient", url = "${woofi.bridge.base-url}",
        configuration = BridgeFeignConfig.class)
public interface BridgeTradingClient {

    String API_KEY = "X-Woo-Api-Key";
    String API_SECRET = "X-Woo-Api-Secret";
    String ACCOUNT_ID = "X-Woo-Account-Id";
    String BROKER_ID = "X-Woo-Broker-Id";
    String ENV = "X-Woo-Env";

    @PostMapping(value = "/v2/orders", consumes = "application/json")
    BridgeOrder createOrder(@RequestHeader(API_KEY) String apiKey,
                            @RequestHeader(API_SECRET) String apiSecret,
                            @RequestHeader(ACCOUNT_ID) String accountId,
                            @RequestHeader(value = BROKER_ID, required = false) String brokerId,
                            @RequestHeader(ENV) String env,
                            @RequestBody BridgeOrderRequest request);

    @GetMapping("/v2/orders/by-client-id/{clientOrderId}")
    BridgeOrder getOrderByClientId(@RequestHeader(API_KEY) String apiKey,
                                   @RequestHeader(API_SECRET) String apiSecret,
                                   @RequestHeader(ACCOUNT_ID) String accountId,
                                   @RequestHeader(value = BROKER_ID, required = false) String brokerId,
                                   @RequestHeader(ENV) String env,
                                   @PathVariable("clientOrderId") String clientOrderId,
                                   @RequestParam("symbol") String symbol);

    @GetMapping("/v2/orders/{orderId}")
    BridgeOrder getOrder(@RequestHeader(API_KEY) String apiKey,
                         @RequestHeader(API_SECRET) String apiSecret,
                         @RequestHeader(ACCOUNT_ID) String accountId,
                         @RequestHeader(value = BROKER_ID, required = false) String brokerId,
                         @RequestHeader(ENV) String env,
                         @PathVariable("orderId") String orderId,
                         @RequestParam("symbol") String symbol);

    @DeleteMapping("/v2/orders/by-client-id/{clientOrderId}")
    BridgeOrder cancelOrderByClientId(@RequestHeader(API_KEY) String apiKey,
                                      @RequestHeader(API_SECRET) String apiSecret,
                                      @RequestHeader(ACCOUNT_ID) String accountId,
                                      @RequestHeader(value = BROKER_ID, required = false) String brokerId,
                                      @RequestHeader(ENV) String env,
                                      @PathVariable("clientOrderId") String clientOrderId,
                                      @RequestParam("symbol") String symbol);

    @PutMapping(value = "/v2/leverage", consumes = "application/json")
    Map<String, Object> setLeverage(@RequestHeader(API_KEY) String apiKey,
                                    @RequestHeader(API_SECRET) String apiSecret,
                                    @RequestHeader(ACCOUNT_ID) String accountId,
                                    @RequestHeader(value = BROKER_ID, required = false) String brokerId,
                                    @RequestHeader(ENV) String env,
                                    @RequestParam("symbol") String symbol,
                                    @RequestBody Map<String, Object> body);
}
