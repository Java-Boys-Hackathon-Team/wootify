package ru.javaboys.wootify.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.javaboys.wootify.config.WoofiFeignConfig;
import ru.javaboys.wootify.dto.response.MarketsResponse;

@FeignClient(name = "woofiMarketClient", url = "${woofi.bridge.base-url}", configuration = WoofiFeignConfig.class)
public interface MarketClient {

    @GetMapping("/markets")
    MarketsResponse getMarkets(
            @RequestParam("apiKey") String wooApiKey,
            @RequestParam("apiSecret") String wooApiSecret,
            @RequestParam("accountId") String accountId,
            @RequestParam(value = "env", required = false) String env
    );
}
