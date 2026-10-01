package com.example.backend.member.repository.mybatis;

import com.example.backend.member.model.Member;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

// 조건부 MapperScan으로만 등록한다. MapStruct DtoConverter와 다른 SQL 매퍼다.
@Mapper
public interface MemberMapper {
    List<Member> findAll();
    Member findById(@Param("id") long id);
}
