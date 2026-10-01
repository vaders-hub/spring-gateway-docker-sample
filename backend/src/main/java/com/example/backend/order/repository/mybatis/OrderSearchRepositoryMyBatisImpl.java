package com.example.backend.order.repository.mybatis;

import com.example.backend.order.model.OrderSearchCriteria;
import com.example.backend.order.model.OrderSummary;
import com.example.backend.order.repository.OrderSearchRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mybatis")
class OrderSearchRepositoryMyBatisImpl implements OrderSearchRepository {
    private final OrderSqlMapper mapper;
    OrderSearchRepositoryMyBatisImpl(OrderSqlMapper mapper) { this.mapper = mapper; }
    public List<OrderSummary> search(OrderSearchCriteria criteria, String ownerSubject) { return mapper.search(criteria, ownerSubject); }
    public long count(OrderSearchCriteria criteria, String ownerSubject) { return mapper.count(criteria, ownerSubject); }
}
