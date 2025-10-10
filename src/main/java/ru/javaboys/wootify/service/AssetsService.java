package ru.javaboys.wootify.service;

import ru.javaboys.wootify.entity.Account;
import ru.javaboys.wootify.entity.ApiKey;

public interface AssetsService {
    Double getCurrentAssetsInUSDC(Account account, ApiKey apiKey);
}
