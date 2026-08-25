package com.example.collab.common;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /**
     * 요청자 식별은 {@code @CurrentUser} 커스텀 resolver가 읽는 {@code X-User-Id} 헤더다.
     * 커스텀 resolver라 springdoc이 시그니처에서 알아낼 수 없어 문서에 아예 안 나온다 —
     * 그러면 Swagger에서 호출하는 족족 400이 된다. 전 오퍼레이션에 직접 붙인다.
     */
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
