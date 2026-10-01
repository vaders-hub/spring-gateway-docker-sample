package com.example.backend.member.service;

import com.example.backend.member.model.Member;
import java.util.List;

public interface MemberService {
    List<Member> list();
    Member get(long id);
    // 타 기능의 참조 검증은 부재를 값으로 반환한다. HTTP 오류 의미는 호출자가 결정한다.
    boolean exists(long memberId);
}
