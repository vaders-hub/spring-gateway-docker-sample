package com.example.backend.member.web;

import com.example.backend.common.config.MappingConfig;
import com.example.backend.member.model.Member;
import com.example.backend.member.web.dto.MemberResponse;
import java.util.List;
import org.mapstruct.Mapper;

// 인터페이스 구현은 컴파일 때 생성된다. Entity 대신 업무 model을 받아 HTTP 응답 DTO로 변환한다.
@Mapper(config = MappingConfig.class)
public interface MemberDtoConverter {
    MemberResponse toResponse(Member source);
    List<MemberResponse> toResponses(List<Member> source);
}
