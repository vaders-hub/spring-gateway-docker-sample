package com.example.backend.member.application;

import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.config.ConditionalOnLearningFeature;
import com.example.backend.common.exception.BusinessException;
import com.example.backend.member.domain.Member;
import com.example.backend.member.application.port.MemberRepository;
import com.example.backend.member.contract.MemberLookup;
import java.util.List;
import org.springframework.stereotype.Service;

// 저장소 port에만 의존하므로 fixture에서 JPA로 바꿔도 조회 업무 로직은 동일하다.
@Service
@ConditionalOnLearningFeature
public class MemberService implements MemberLookup {
    private final MemberRepository repository;
    public MemberService(MemberRepository repository) { this.repository = repository; }
    public List<Member> list() { return repository.findAll(); }
    // 다른 feature에는 존재 검증만 공개한다. 전체 회원 정보나 내부 domain 객체를 전달할 필요가 없다.
    @Override
    public void requireExists(long memberId) { get(memberId); }
    public Member get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
