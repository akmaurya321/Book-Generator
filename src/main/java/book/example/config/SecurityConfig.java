package book.example.config;

import book.example.Entity.AppUser;
import book.example.services.JwtAuthenticationFilter;
import book.example.services.JwtCookieService;
import book.example.services.JwtService;
import book.example.services.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserService userService;
    private final JwtService jwtService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtCookieService jwtCookieService;
    private final SameOriginRequestFilter sameOriginRequestFilter;

    @Value("${app.security.google.enabled:false}")
    private boolean googleAuthEnabled;


    public SecurityConfig(UserService userService,
                          JwtService jwtService,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtCookieService jwtCookieService,
                          SameOriginRequestFilter sameOriginRequestFilter) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtCookieService = jwtCookieService;
        this.sameOriginRequestFilter = sameOriginRequestFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(sameOriginRequestFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .defaultAuthenticationEntryPointFor(
                                (request, response, authException) -> {
                                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                    response.getWriter().write("{\"message\":\"Authentication required.\"}");
                                },
                                request -> request.getServletPath().startsWith("/api/")))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/",
                            "/index.html",
                            "/app.js",
                            "/style.css",
                            "/js/**",
                            "/css/**",
                            "/assets/**",
                            "/react.js",
                            "/react-dom-client.js",
                            "/react-jsx-runtime.js",
                            "/support.html",
                            "/privacy.html",
                            "/terms.html",
                            "/favicon.ico",
                            "/error",
                            "/actuator/health",
                            "/actuator/health/**",
                                "/api/auth/config",
                                "/api/auth/me",
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/password/forgot",
                                "/api/auth/password/reset",
                                "/oauth2/**",
                            "/login/oauth2/**"
                    ).permitAll();

                    auth.requestMatchers(
                            HttpMethod.GET,
                            "/api/v1/marketplace",
                            "/api/v1/marketplace/categories",
                            "/api/v1/marketplace/*",
                            "/api/v1/marketplace/*/download/*"
                    ).permitAll();
                    auth.requestMatchers("/api/v1/marketplace/**").authenticated();
                    auth.requestMatchers(
                            HttpMethod.GET,
                            "/api/v1/templates",
                            "/api/v1/templates/*",
                            "/api/v1/templates/*/preview/*"
                    ).permitAll();
                    auth.requestMatchers("/api/v1/admin/**", "/api/v1/template-submissions/**").authenticated();

                            auth.requestMatchers(
                                    "/api/auth/avatar",
                                    "/api/auth/usage",
                                    "/api/auth/usage/summary").authenticated();

                        // Every documentation endpoint is user-owned and the controller
                        // performs the jobId -> authenticated-user ownership check.
                        // Use Ant-style wildcard matching here; \{jobId\} is not a wildcard
                        // pattern for Spring Security requestMatchers(String).
                        auth.requestMatchers("/api/documentation/**").authenticated();

                    auth.anyRequest().denyAll();
                })
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("docuai_token")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":true}");
                        }));

        // Register the Google login flow only when it is explicitly enabled.
        // This keeps disabled local-development builds from exposing an OAuth
        // entry point with placeholder credentials.
        if (googleAuthEnabled) {
            http.oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserService()))
                    .successHandler((request, response, authentication) -> {
                        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
                        AppUser user = userService.upsertOAuth2User(principal);
                        jwtCookieService.writeToken(response, jwtService.generateToken(user));
                        response.sendRedirect("/");
                    })
                    .failureHandler((request, response, exception) -> {
                        response.sendRedirect("/?oauthError=google_login_failed");
                    }));
        }

        return http.build();
    }

    @Bean
    public org.springframework.security.oauth2.client.userinfo.OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return userRequest -> {
            OAuth2User user = delegate.loadUser(userRequest);
            return user;
        };
    }
}
