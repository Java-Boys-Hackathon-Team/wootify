package ru.javaboys.wootify.orderly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class OrderlyService {
    private static final Logger log = LoggerFactory.getLogger(OrderlyService.class);

//    private OrderlyStreamingMultiClient streamingService;

    @Value("${orderly.ws.url}") private String url;
    @Value("${orderly.account-id}") private String accountId;

//    public OrderlyService(OrderlyStreamingMultiClient streamingService) {
//        this.streamingService = streamingService;
//    }

//    @PostConstruct
//    public void checkMultiClient() {
//

//
//    }

    @PostConstruct
    public void checkClient() {


    }

}
