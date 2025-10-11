package ru.javaboys.wootify.service;

import org.springframework.stereotype.Service;
import ru.javaboys.wootify.client.BalanceClient;
import ru.javaboys.wootify.dto.response.BalanceResponse;
import ru.javaboys.wootify.entity.Account;
import ru.javaboys.wootify.entity.ApiKey;

@Service
public class AssetsServiceImpl implements AssetsService {

    private final BalanceClient balanceClient;

    public AssetsServiceImpl(BalanceClient balanceClient) {
        this.balanceClient = balanceClient;
    }

    @Override
    public Double getCurrentAssetsInUSDC (Account account, ApiKey apiKey) {
        BalanceResponse response = balanceClient.getBalance(
                apiKey.getKey(),
                apiKey.getSecret(),
                account.getWoofiId(),
                apiKey.getEnv());

         if (response == null) {
             return 0.0;
         }

         if (response.getTotal() != null && response.getTotal().containsKey("USDC")) {
             return response.getTotal().get("USDC");
         }

         return 0.0;
     }

}
