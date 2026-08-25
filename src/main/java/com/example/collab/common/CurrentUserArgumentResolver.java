package com.example.collab.common;

import com.example.collab.common.exception.MissingUserIdException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code X-User-Id} 헤더를 {@link CurrentUser} 파라미터로 주입한다.
 *
 * <p>사용자 존재 여부는 검사하지 않는다. 없는 ID는 프로젝트 접근 게이트에서 멤버십 부재로 걸러지므로
 * 여기에 {@code UserRepository} 의존을 붙이지 않는다.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String raw = request == null ? null : request.getHeader(USER_ID_HEADER);

        if (raw == null || raw.isBlank()) {
            throw new MissingUserIdException(USER_ID_HEADER + " header is required");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new MissingUserIdException(USER_ID_HEADER + " header must be a number: " + raw);
        }
    }
}
