package ru.javaboys.wootify.dto.trade;

import jnr.ffi.annotations.In;
import lombok.Builder;
import lombok.Data;
import ru.javaboys.wootify.dto.response.PositionResponse;

@Data
@Builder
public class PositionStateResponse {
    Integer positionsCount;
    PositionResponse positionResponse;
}
