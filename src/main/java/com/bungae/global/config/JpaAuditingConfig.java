package com.bungae.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseTimeEntity 의 @CreatedDate/@LastModifiedDate 를 채우기 위한 JPA Auditing 활성화.
 * 메인 애플리케이션이 아닌 별도 설정으로 분리 → @DataJpaTest / @WebMvcTest 에서 선택적으로 제외 가능.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
