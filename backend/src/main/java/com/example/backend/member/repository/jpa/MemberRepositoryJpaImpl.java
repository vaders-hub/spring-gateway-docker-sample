package com.example.backend.member.repository.jpa;

import com.example.backend.member.repository.MemberRepository;
import com.example.backend.member.model.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@org.springframework.context.annotation.Profile("(!local & !test) | persistence")
@Transactional(readOnly = true)
class MemberRepositoryJpaImpl implements MemberRepository {
    private final MemberJpaRepository repository;
    MemberRepositoryJpaImpl(MemberJpaRepository repository) { this.repository = repository; }
    // 조회 transaction 안에서 변환을 끝내므로 OSIV가 꺼져 있어도 응답에서 lazy loading이 발생하지 않는다.
    public List<Member> findAll() {
        return repository.findAll(Sort.by("id")).stream().map(MemberEntity::toDomain).toList();
    }
    public Optional<Member> findById(long id) { return repository.findById(id).map(MemberEntity::toDomain); }
}
