package com.example.backend.member.api;

import com.example.backend.common.config.MappingConfig;
import com.example.backend.member.domain.Member;
import com.example.backend.member.api.dto.MemberResponse;
import java.util.List;
import org.mapstruct.Mapper;

// 인터페이스 구현은 컴파일 때 생성된다. Entity 대신 domain을 받아 외부 응답 계약을 분리한다.
@Mapper(config = MappingConfig.class)
public interface MemberMapper {
    MemberResponse toResponse(Member source);
    List<MemberResponse> toResponses(List<Member> source);
}
