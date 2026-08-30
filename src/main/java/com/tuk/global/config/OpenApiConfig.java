package com.tuk.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI(/swagger-ui.html) 설정.
 * 우상단 Authorize 에 JWT accessToken 을 넣으면 이후 모든 요청에 Bearer 로 붙는다
 * (Postman 토큰 저글링 대체).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI tukOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("툭 (Tuk) API")
                        .version("v0.0.1")
                        .description("친한 친구끼리 거절 걱정 없이 '지금 볼래?' — 인증/친구/presence/번개/알림 API"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("POST /api/auth/login 으로 받은 accessToken 을 그대로 입력 (Bearer 접두어 불필요)")));
    }
}
