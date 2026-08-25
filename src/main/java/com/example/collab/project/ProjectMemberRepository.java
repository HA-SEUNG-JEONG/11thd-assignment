package com.example.collab.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    long countByProjectIdAndRole(Long projectId, ProjectRole role);

    /** 프로젝트 삭제 시 자식 행을 먼저 지운다. 호출자의 트랜잭션 안에서만 유효. */
    void deleteByProjectId(Long projectId);

    /** 멤버 목록 응답에 name·email이 필요하므로 LAZY user를 한 번에 가져온다(N+1 회피). */
    @Query("SELECT m FROM ProjectMember m JOIN FETCH m.user WHERE m.project.id = :projectId")
    List<ProjectMember> findAllWithUserByProjectId(@Param("projectId") Long projectId);
}
