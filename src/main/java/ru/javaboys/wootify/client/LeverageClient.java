package ru.javaboys.wootify.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ru.javaboys.wootify.config.WoofiFeignConfig;
import ru.javaboys.wootify.dto.request.LeverageRequest;
import ru.javaboys.wootify.dto.response.LeverageResponse;

@FeignClient(name = "woofiLeverageClient", url = "${woofi.bridge.base-url}", configuration = WoofiFeignConfig.class)
public interface LeverageClient {

    @PutMapping(value = "/positions/{symbol}/leverage", consumes = "application/json")
    LeverageResponse setLeverage(
            @PathVariable("symbol") String symbol,
            @RequestParam("apiKey") String wooApiKey,
            @RequestParam("apiSecret") String wooApiSecret,
            @RequestParam("accountId") String accountId,
            @RequestParam(value = "env", required = false) String env,
            @RequestBody LeverageRequest body
    );
}
