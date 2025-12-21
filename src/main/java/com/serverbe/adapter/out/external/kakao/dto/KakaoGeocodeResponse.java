package com.serverbe.adapter.out.external.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KakaoGeocodeResponse(
        List<Document> documents,
        Meta meta
) {
    public record Document(
            @JsonProperty("address_name") String addressName,
            String x,
            String y
    ) {
    }

    public record Meta(
            @JsonProperty("total_count") int totalCount
    ) {
    }
}