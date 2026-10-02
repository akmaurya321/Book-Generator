package book.example.services;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class JwtCookieService {

    @Value("${app.security.cookie.secure:false}")
    private boolean secureCookie;

    public void writeToken(HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", ResponseCookie.from("docuai_token", token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofHours(12))
                .build()
                .toString());
    }
}