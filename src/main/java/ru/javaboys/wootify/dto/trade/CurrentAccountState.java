package ru.javaboys.wootify.dto.trade;

import lombok.Builder;
import lombok.Data;
import ru.javaboys.wootify.entity.Account;
import ru.javaboys.wootify.entity.ApiKey;

@Data
@Builder
public class CurrentAccountState {
    Account account;
    ApiKey apiKey;
}
