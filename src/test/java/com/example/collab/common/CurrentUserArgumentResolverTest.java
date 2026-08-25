package com.example.collab.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.collab.common.exception.MissingUserIdException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * 요청자 식별은 명세에 형식이 없어 재량으로 채운 지점이라 테스트로 잠근다.
 * 단계 2에는 {@code @CurrentUser}를 쓰는 엔드포인트가 없어 curl로는 검증할 수 없다.
 */
class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @Test
    @DisplayName("X-User-Id 헤더가 있으면 Long으로 주입한다")
    void resolvesHeaderValue() {
        Object resolved = resolver.resolveArgument(null, null, requestWith("42"), null);

        assertThat(resolved).isEqualTo(42L);
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 400으로 매핑되는 예외를 던진다")
    void rejectsMissingHeader() {
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, requestWith(null), null))
                .isInstanceOf(MissingUserIdException.class);
    }

    @Test
    @DisplayName("X-User-Id 헤더가 수치가 아니면 400으로 매핑되는 예외를 던진다")
    void rejectsNonNumericHeader() {
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, requestWith("alice"), null))
                .isInstanceOf(MissingUserIdException.class);
    }

    private ServletWebRequest requestWith(String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) {
            request.addHeader(CurrentUserArgumentResolver.USER_ID_HEADER, userId);
        }
        return new ServletWebRequest(request);
    }
}
