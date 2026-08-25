package com.example.collab.task;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.collab.common.CurrentUserArgumentResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 과제가 코드로 답하라고 명시한 유일한 항목: "두 사용자가 같은 작업을 동시에 수정하면
 * 나중 요청이 앞선 변경을 덮어쓴다. 이 상황을 어떻게 다룰지 결정하고 구현하세요."
 *
 * <p>테스트에 {@code @Transactional}을 붙이지 않는다. 붙이면 두 요청이 한 영속성 컨텍스트를
 * 공유해 과제가 말한 <b>트랜잭션 밖</b> 충돌이 재현되지 않는다 — 그건 {@code @Version}
 * 애노테이션만으로도 통과해버리는 상황이라 아무것도 증명하지 못한다. 요청마다 서비스의
 * 트랜잭션이 따로 열리고 커밋되어야 실제 시나리오다.
 *
 * <p>그래서 시드 작업의 version이 영구히 오른다. 메서드를 하나로 두고 현재 version을 읽어
 * 시작하므로 실행 순서에 값이 낡지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskConcurrencyTest {

    private static final String TASK_URL = "/api/projects/1/tasks/1";
    private static final String ALICE = "1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("같은 version으로 두 번 수정하면 두 번째는 409 — 앞선 변경이 덮이지 않는다")
    void staleVersionIsRejected() throws Exception {
        long version = currentVersion();

        mockMvc.perform(patch(TASK_URL)
                        .header(CurrentUserArgumentResolver.USER_ID_HEADER, ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("먼저 도착한 수정", version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("먼저 도착한 수정"))
                .andExpect(jsonPath("$.version").value(version + 1));

        // 같은 version을 다시 보낸다 = 앞선 변경을 못 본 클라이언트의 저장 요청.
        mockMvc.perform(patch(TASK_URL)
                        .header(CurrentUserArgumentResolver.USER_ID_HEADER, ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("나중에 도착한 수정", version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Task was modified by another user"));

        // 덮어쓰기가 실제로 일어나지 않았는지 확인한다. 409만 보고 끝내면
        // "거절은 했는데 이미 반영된" 경우를 놓친다.
        mockMvc.perform(get(TASK_URL).header(CurrentUserArgumentResolver.USER_ID_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("먼저 도착한 수정"));
    }

    /**
     * 삭제도 같은 분실 갱신 문제를 안는다 — 남이 이미 고친 작업을 낡은 화면에서 그대로 지우는 것.
     *
     * <p>이 클래스에는 테스트 {@code @Transactional}이 없어 삭제가 공유 H2에 영구 반영된다.
     * 그래서 시드 작업이 아니라 이 테스트가 직접 만든 작업만 지운다.
     */
    @Test
    @DisplayName("낡은 version으로 삭제하면 409, 맞는 version이어야 지워진다")
    void staleVersionDeleteIsRejected() throws Exception {
        String createdUrl = createTask();
        long version = currentVersion(createdUrl);

        mockMvc.perform(delete(createdUrl)
                        .header(CurrentUserArgumentResolver.USER_ID_HEADER, ALICE)
                        .param("version", String.valueOf(version + 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Task was modified by another user"));

        mockMvc.perform(delete(createdUrl)
                        .header(CurrentUserArgumentResolver.USER_ID_HEADER, ALICE)
                        .param("version", String.valueOf(version)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(createdUrl).header(CurrentUserArgumentResolver.USER_ID_HEADER, ALICE))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("version 없이 삭제하면 400 — 낡은 화면인지 판단할 근거 자체가 없다")
    void deleteWithoutVersionIsRejected() throws Exception {
        mockMvc.perform(delete(TASK_URL).header(CurrentUserArgumentResolver.USER_ID_HEADER, ALICE))
                .andExpect(status().isBadRequest());
    }

    /** 이 테스트만 소유하는 작업을 만든다. 시드 작업을 지우면 공유 H2에 영구 반영된다. */
    private String createTask() throws Exception {
        return mockMvc.perform(post("/api/projects/1/tasks")
                        .header(CurrentUserArgumentResolver.USER_ID_HEADER, ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "삭제 version 검사용 작업"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
    }

    private long currentVersion() throws Exception {
        return currentVersion(TASK_URL);
    }

    private long currentVersion(String url) throws Exception {
        String body = mockMvc.perform(
                        get(url).header(CurrentUserArgumentResolver.USER_ID_HEADER, ALICE))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("version").asLong();
    }

    private String updateBody(String title, long version) {
        return """
                {"title": "%s", "version": %d}
                """.formatted(title, version);
    }
}
