package com.example.backend.member.contract;

/** 다른 feature에 공개하는 최소 조회 계약. 없는 회원은 공통 NOT_FOUND 업무 예외로 처리한다. */
public interface MemberLookup {
    void requireExists(long memberId);
}
