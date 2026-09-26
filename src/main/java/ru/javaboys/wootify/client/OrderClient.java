package ru.javaboys.wootify.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import ru.javaboys.wootify.config.WoofiFeignConfig;
import ru.javaboys.wootify.dto.request.OrderRequest;
import ru.javaboys.wootify.dto.response.OrderCancelResponse;
import ru.javaboys.wootify.dto.response.OrderDetailResponse;
import ru.javaboys.wootify.dto.response.OrderResponse;
import ru.javaboys.wootify.dto.response.OrdersResponse;

@FeignClient(name = "woofiOrderClient", url = "${woofi.bridge.base-url}", configuration = WoofiFeignConfig.class)
public interface OrderClient {

    @PostMapping(value = "/order", consumes = "application/json")
    OrderResponse createOrder(
            @RequestHeader("X-Woo-Api-Key") String wooApiKey,
            @RequestHeader("X-Woo-Api-Secret") String wooApiSecret,
            @RequestHeader("X-Woo-Account-Id") String accountId,
            @RequestHeader(value = "X-Woo-Env", required = false) String env,
            @RequestBody OrderRequest body
    );

    @GetMapping("/order/{orderId}")
    OrderDetailResponse getOrderById(
            @PathVariable("orderId") String orderId,
            @RequestParam("symbol") String symbol,
            @RequestHeader("X-Woo-Api-Key") String wooApiKey,
            @RequestHeader("X-Woo-Api-Secret") String wooApiSecret,
            @RequestHeader("X-Woo-Account-Id") String accountId,
            @RequestHeader(value = "X-Woo-Env", required = false) String env
    );

    @PostMapping("/cancel")
    OrderCancelResponse cancelOrder(
            @RequestParam("symbol") String symbol,
            @RequestParam("order_id") String orderId,
            @RequestHeader("X-Woo-Api-Key") String wooApiKey,
            @RequestHeader("X-Woo-Api-Secret") String wooApiSecret,
            @RequestHeader("X-Woo-Account-Id") String accountId,
            @RequestHeader(value = "X-Woo-Env", required = false) String env
    );

    @GetMapping("/orders")
    OrdersResponse getOrders(
            @RequestParam("status") String status, // open | closed | all
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "since", required = false) Long since,
            @RequestHeader("X-Woo-Api-Key") String wooApiKey,
            @RequestHeader("X-Woo-Api-Secret") String wooApiSecret,
            @RequestHeader("X-Woo-Account-Id") String accountId,
            @RequestHeader(value = "X-Woo-Env", required = false) String env
    );

}
