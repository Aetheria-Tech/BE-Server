package com.serverbe.domain.model.art.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Proficiency {
    INTRODUCTION("입문자", 0, 3),
    BEGINNER("초급자", 3, 10),
    SKILLED("숙련자", 10, 15),
    EXPERT("전문가", 15, 25),
    MASTER("마스터", 25, 42);


    private final String proficiency;
    private final Integer minimumDistance;
    private final Integer maxDistance;
}
