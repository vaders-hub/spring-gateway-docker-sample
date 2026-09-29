package com.example.backend.member.api;

import com.example.backend.common.config.MappingConfig;
import com.example.backend.member.domain.Member;
import com.example.backend.member.api.dto.MemberResponse;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(config = MappingConfig.class)
public interface MemberMapper {
    MemberResponse toResponse(Member source);
    List<MemberResponse> toResponses(List<Member> source);
}
