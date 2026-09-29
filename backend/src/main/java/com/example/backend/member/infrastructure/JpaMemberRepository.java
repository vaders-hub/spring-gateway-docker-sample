package com.example.backend.member.infrastructure;

import com.example.backend.common.config.ConditionalOnLearningPersistence;
import com.example.backend.member.application.MemberRepository;
import com.example.backend.member.domain.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnLearningPersistence
@Transactional(readOnly = true)
class JpaMemberRepository implements MemberRepository {
    private final MemberJpaRepository repository;
    JpaMemberRepository(MemberJpaRepository repository) { this.repository = repository; }
    // 조회 transaction 안에서 변환을 끝내므로 OSIV가 꺼져 있어도 응답에서 lazy loading이 발생하지 않는다.
    public List<Member> findAll() {
        return repository.findAll(Sort.by("id")).stream().map(MemberEntity::toDomain).toList();
    }
    public Optional<Member> findById(long id) { return repository.findById(id).map(MemberEntity::toDomain); }
}
