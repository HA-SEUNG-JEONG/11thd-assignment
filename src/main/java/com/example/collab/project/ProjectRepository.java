package com.example.collab.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * 내가 멤버인 프로젝트만. 경계 조건이 쿼리 안에 있어야 애플리케이션 필터링 누락으로 인한
     * 데이터 노출이 구조적으로 불가능하다.
     */
    @Query(value = """
            SELECT p FROM Project p
            WHERE p.id IN (SELECT m.project.id FROM ProjectMember m WHERE m.user.id = :userId)
            """,
            countQuery = """
            SELECT COUNT(p) FROM Project p
            WHERE p.id IN (SELECT m.project.id FROM ProjectMember m WHERE m.user.id = :userId)
            """)
    Page<Project> findAllByMemberUserId(@Param("userId") Long userId, Pageable pageable);
}
