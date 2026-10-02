package com.example.backend.member.service.impl;

import com.example.backend.member.service.MemberService;

import com.example.backend.member.error.MemberErrorCode;
import com.example.platform.exception.BusinessException;
import com.example.backend.member.model.Member;
import com.example.backend.member.repository.MemberRepository;
import java.util.List;
import org.springframework.stereotype.Service;

// 저장소 인터페이스에만 의존하므로 메모리에서 JPA로 바꿔도 조회 업무 로직은 동일하다.
@Service
public class MemberServiceImpl implements MemberService {
    private final MemberRepository repository;
    public MemberServiceImpl(MemberRepository repository) { this.repository = repository; }
    public List<Member> list() { return repository.findAll(); }
    // 다른 기능에는 존재 검증만 공개한다. 전체 회원 정보나 내부 model 객체를 전달할 필요가 없다.
    @Override
    public boolean exists(long memberId) { return repository.findById(memberId).isPresent(); }
    public Member get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
