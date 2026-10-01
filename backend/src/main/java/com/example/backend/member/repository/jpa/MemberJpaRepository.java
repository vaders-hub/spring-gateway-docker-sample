package com.example.backend.member.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data가 구현하는 DB 전용 인터페이스. application의 조회 port와 구분한다.
interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {}
