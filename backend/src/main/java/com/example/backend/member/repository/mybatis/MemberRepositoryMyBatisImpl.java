package com.example.backend.member.repository.mybatis;

import com.example.backend.member.model.Member;
import com.example.backend.member.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("mybatis")
@Transactional(readOnly = true)
class MemberRepositoryMyBatisImpl implements MemberRepository {
    private final MemberSqlMapper mapper;
    MemberRepositoryMyBatisImpl(MemberSqlMapper mapper) { this.mapper = mapper; }
    public List<Member> findAll() { return mapper.findAll(); }
    public Optional<Member> findById(long id) { return Optional.ofNullable(mapper.findById(id)); }
}
