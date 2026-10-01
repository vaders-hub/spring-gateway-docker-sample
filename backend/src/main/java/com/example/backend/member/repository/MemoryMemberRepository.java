package com.example.backend.member.repository;

import com.example.backend.member.model.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

// 고정된 불변 학습 데이터다. 사용자 세션/주문 상태를 메모리에 저장하지 않는다.
@Repository
@org.springframework.context.annotation.Profile("(local | test) & !persistence")
class MemoryMemberRepository implements MemberRepository {
    private static final List<Member> DATA = List.of(new Member(1, "Sample Member"), new Member(2, "Second Member"));
    public List<Member> findAll() { return DATA; }
    public Optional<Member> findById(long id) { return DATA.stream().filter(item -> item.id() == id).findFirst(); }
}
