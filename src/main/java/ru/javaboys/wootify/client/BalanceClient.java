package ru.javaboys.wootify.client;

import org.springframework.web.bind.annotation.RequestHeader;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.javaboys.wootify.config.WoofiFeignConfig;
import ru.javaboys.wootify.dto.response.BalanceResponse;

@FeignClient(name = "woofiBalanceClient", url = "${woofi.bridge.base-url}", configuration = WoofiFeignConfig.class)
public interface BalanceClient {

    @GetMapping("/balance")
    BalanceResponse getBalance(
            @RequestHeader("X-Woo-Api-Key") String wooApiKey,
            @RequestHeader("X-Woo-Api-Secret") String wooApiSecret,
            @RequestHeader("X-Woo-Account-Id") String accountId,
            @RequestHeader(value = "X-Woo-Env", required = false) String env
    );
}
