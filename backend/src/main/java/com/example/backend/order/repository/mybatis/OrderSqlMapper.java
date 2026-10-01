package com.example.backend.order.repository.mybatis;

import com.example.backend.order.model.OrderSearchCriteria;
import com.example.backend.order.model.OrderSummary;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;

public interface OrderSqlMapper {
    int insert(OrderRow row);
    OrderRow findByIdAndOwnerSubject(@Param("id") UUID id, @Param("ownerSubject") String ownerSubject);
    List<OrderSummary> search(@Param("criteria") OrderSearchCriteria criteria, @Param("ownerSubject") String ownerSubject);
    long count(@Param("criteria") OrderSearchCriteria criteria, @Param("ownerSubject") String ownerSubject);
}
