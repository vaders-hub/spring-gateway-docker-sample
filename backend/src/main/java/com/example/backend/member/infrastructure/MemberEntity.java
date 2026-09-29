package com.example.backend.member.infrastructure;

import com.example.backend.member.domain.Member;
import jakarta.persistence.*;

// DB 매핑 전용 Entity다. JPA 객체를 Controller로 노출하지 않고 domain으로 변환한다.
@Entity
@Table(name = "members")
class MemberEntity {
    @Id
    private Long id;
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;
    protected MemberEntity() {}
    Member toDomain() { return new Member(id, displayName); }
}
