package com.example.collab.common;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code @CurrentUser}는 {@code X-User-Id} 헤더에서 값을 읽는 커스텀 resolver 파라미터라
 * springdoc이 시그니처만으로는 알아내지 못한다. 두 방향으로 어긋난다 —
 * 헤더는 문서에 안 나와 Swagger 호출이 족족 400이 되고,
 * 파라미터는 필수 query {@code userId}로 오해되어 Execute 자체가 막힌다.
 * 전자는 전 오퍼레이션에 헤더를 직접 붙여서, 후자는 애노테이션을 전역 무시시켜 맞춘다.
 */
@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    @Bean
    public OperationCustomizer currentUserHeader() {
        return (operation, handlerMethod) -> operation.addParametersItem(new Parameter()
                .in("header")
                .name("X-User-Id")
                .description("요청자 사용자 ID (사용자 생성·조회 엔드포인트는 사용하지 않음)")
                .required(false)
                .schema(new StringSchema()));
    }
}
