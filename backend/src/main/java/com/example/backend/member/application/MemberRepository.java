package com.example.backend.member.application;

import com.example.backend.member.domain.Member;
import java.util.List;
import java.util.Optional;

// application이 필요한 조회 계약. fixture/DB 구현은 infrastructure에서 교체한다.
public interface MemberRepository {
    List<Member> findAll();
    Optional<Member> findById(long id);
}
