package com.example.backend.order.service.impl;

import com.example.backend.order.model.OrderSearchCriteria;
import com.example.backend.order.model.OrderSearchResult;
import com.example.backend.order.repository.OrderSearchRepository;
import com.example.backend.order.service.OrderSearchService;
import com.example.platform.code.CommonErrorCode;
import com.example.platform.exception.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mybatis")
class OrderSearchServiceImpl implements OrderSearchService {
    private final OrderSearchRepository repository;
    OrderSearchServiceImpl(OrderSearchRepository repository) { this.repository = repository; }
    // PostgreSQL의 동일 snapshot으로 total과 items를 읽는다. SQL은 저장소에서만 작성한다.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderSearchResult search(OrderSearchCriteria criteria, String ownerSubject) {
        if (ownerSubject == null || ownerSubject.isBlank() || ownerSubject.length() > 255) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        long total = repository.count(criteria, ownerSubject);
        return new OrderSearchResult(repository.search(criteria, ownerSubject), total, criteria.page(), criteria.size());
    }
}
