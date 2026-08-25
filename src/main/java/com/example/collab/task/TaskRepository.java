package com.example.collab.task;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * 프로젝트 경계가 쿼리 안에 있다 — 애플리케이션에서 걸러내면 페이징 수가 틀어진다.
     * keyword·status는 null이면 해당 조건을 통과시킨다(빈 문자열은 서비스에서 null로 정규화).
     */
    @Query("""
            SELECT t FROM Task t
            WHERE t.project.id = :projectId
              AND (:keyword IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR t.status = :status)
            """)
    Page<Task> search(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("status") TaskStatus status,
            Pageable pageable);

    /** 단건 조회도 프로젝트 경계를 함께 건다 — findById면 타 프로젝트 작업이 유출된다. */
    Optional<Task> findByIdAndProjectId(Long id, Long projectId);

    /** 프로젝트 삭제 시 tasks가 남으면 FK 위반. cascade 대신 명시 삭제(경로가 코드에 보인다). */
    void deleteByProjectId(Long projectId);
}
