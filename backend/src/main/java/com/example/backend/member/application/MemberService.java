package com.example.backend.member.application;

import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.config.ConditionalOnLearningMock;
import com.example.backend.common.exception.BusinessException;
import com.example.backend.member.domain.Member;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnLearningMock
public class MemberService {
    private final MemberRepository repository;
    public MemberService(MemberRepository repository) { this.repository = repository; }
    public List<Member> list() { return repository.findAll(); }
    public Member get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
