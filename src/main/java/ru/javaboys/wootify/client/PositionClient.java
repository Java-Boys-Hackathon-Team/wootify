package ru.javaboys.wootify.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import ru.javaboys.wootify.config.WoofiFeignConfig;
import ru.javaboys.wootify.dto.response.PositionsResponse;

@FeignClient(name = "woofiPositionClient", url = "${woofi.bridge.base-url}", configuration = WoofiFeignConfig.class)
public interface PositionClient {

    @GetMapping("/positions")
    PositionsResponse getPositions(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "only_open", required = false) Boolean onlyOpen,
            @RequestParam("apiKey") String wooApiKey,
            @RequestParam("apiSecret") String wooApiSecret,
            @RequestParam("accountId") String accountId,
            @RequestParam(value = "env", required = false) String env
    );
}
