package com.example.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HuobiResponseDto {

    @JsonProperty("status")
    private String status;

    @JsonProperty("data")
    private List<HuobiTickerDto> data;
}
