package com.serverbe.adapter.in.web;

import com.serverbe.adapter.out.external.google.GoogleAdapter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GoogleAdapter googleAdapter;

    // 사용자가 이 주소를 호출하면 구글 로그인 창으로 리다이렉트됨
    @GetMapping("/login/google")
    public void redirectToGoogle(HttpServletResponse response) throws IOException {
        String redirectUrl = googleAdapter.getGoogleRedirectUrl();
        response.sendRedirect(redirectUrl);
    }
}