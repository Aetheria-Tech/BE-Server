package com.serverbe.application.port.out.dto.art;

public record RunningArtUpdateDto(
        String title,
        String content
) {
    public static RunningArtUpdateDto of(String title, String content) {
        return new RunningArtUpdateDto(
                title == null ? "제목" : title,
                content == null ? "설명" : content
        );
    }
}