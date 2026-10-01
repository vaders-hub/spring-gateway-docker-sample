package com.example.backend.member.repository;

import com.example.backend.member.model.Member;
import java.util.List;
import java.util.Optional;

// 서비스가 사용하는 조회 계약. 메모리/JPA 구현은 프로필에 따라 선택한다.
public interface MemberRepository {
    List<Member> findAll();
    Optional<Member> findById(long id);
}
