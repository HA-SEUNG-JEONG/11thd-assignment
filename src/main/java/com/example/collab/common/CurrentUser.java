package com.example.collab.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청자 식별자를 주입받는 마커 애노테이션.
 *
 * <p>인증을 실제로 구현(JWT/세션)하게 되면 {@link CurrentUserArgumentResolver} 한 곳만 바뀐다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
