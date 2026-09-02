package com.tuk.global.config;

import com.tuk.global.auth.jwt.JwtAuthenticationFilter;
import com.tuk.global.auth.jwt.JwtProperties;
import com.tuk.global.auth.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;

    /** 문서가 꺼진 환경(운영)에서는 Swagger 경로를 permitAll 로 열지도 않는다. */
    private final boolean apiDocsEnabled;

    /** 허용 오리진. 운영은 CORS_ALLOWED_ORIGINS 로 실제 도메인 주입. */
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtTokenProvider tokenProvider,
                          @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled,
                          @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.tokenProvider = tokenProvider;
        this.apiDocsEnabled = apiDocsEnabled;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // Swagger UI / OpenAPI 문서 — 문서가 켜진 환경(로컬·개발)에서만 열어준다.
                    // 운영은 springdoc.*.enabled=false 라 이 블록을 건너뛰고 401 이 된다.
                    if (apiDocsEnabled) {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    }
                    auth
                        // 로그인·토큰 재발급은 인증 없이 접근
                        .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // WebSocket 핸드셰이크는 열어두고 인증은 STOMP CONNECT 에서 처리
                        .requestMatchers("/ws/**").permitAll()
                        // 그 외 전부 access 토큰 필요 (로그아웃 포함)
                        .anyRequest().authenticated();
                })
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider),
                        UsernamePasswordAuthenticationFilter.class)
                // 인증 실패는 리다이렉트 없이 401
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, e) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)));
        return http.build();
    }

    /**
     * CORS. 허용 오리진은 설정(app.cors.allowed-origins)에서 온다 — 기본값은 로컬 웹 개발 서버뿐.
     * 앱(Flutter 네이티브)은 브라우저가 아니라 CORS 대상이 아니다.
     * allowCredentials=true 라 와일드카드(*)는 쓸 수 없다(스펙상 금지, 써도 브라우저가 거부).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
