package com.example.backend.order.repository.mybatis;

import com.example.backend.member.repository.MemberRepository;
import com.example.backend.member.repository.mybatis.MemberMapper;
import com.example.backend.order.model.OrderSearchCriteria;
import com.example.backend.order.repository.OrderPersistenceContractTest;
import com.example.backend.order.service.OrderSearchService;
import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.repository.mybatis.ProductMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles({"prod", "mybatis"})
class MyBatisOrderPersistenceIntegrationTest extends OrderPersistenceContractTest {
    @Autowired OrderSearchService search;
    @Autowired ApplicationContext context;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void selectsOnlyMyBatisRepositoriesAndJdbcTransactions() {
        assertThat(context.getBeansOfType(MemberRepository.class)).hasSize(1);
        assertThat(context.getBeansOfType(ProductRepository.class)).hasSize(1);
        assertThat(context.getBeansOfType(MemberMapper.class)).hasSize(1);
        assertThat(context.getBeansOfType(ProductMapper.class)).hasSize(1);
        assertThat(context.getBeansOfType(OrderMapper.class)).hasSize(1);
        assertThat(context.getBean(org.apache.ibatis.session.SqlSessionFactory.class)).isNotNull();
        assertThat(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class)).isEmpty();
        assertThat(transactionManager).isInstanceOf(org.springframework.jdbc.support.JdbcTransactionManager.class);
    }

    @Test
    void joinAndFiltersUseSnapshotPriceAndOnlyOwnersRows() {
        var keyboard = orders.place(1, 1, 2, "alice");
        orders.place(2, 2, 1, "alice");
        orders.place(1, 1, 10, "bob");
        jdbc.update("update products set name='New Keyboard', unit_price=90000 where id=1");
        var result = search.search(new OrderSearchCriteria(1L, "keyBOARD", new BigDecimal("100000"), 0, 20), "alice");
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        var item = result.items().getFirst();
        assertThat(item.id()).isEqualTo(keyboard.id());
        assertThat(item.memberName()).isEqualTo("Sample Member");
        assertThat(item.productName()).isEqualTo("Keyboard");
        assertThat(item.currentProductName()).isEqualTo("New Keyboard");
        assertThat(item.totalPrice()).isEqualByComparingTo("100000");
        assertThat(search.search(new OrderSearchCriteria(null, null, new BigDecimal("100001"), 0, 20), "alice").items()).isEmpty();
    }

    @Test
    void paginationIsStableForTiesAndCountMatchesAllFilters() {
        for (int i = 0; i < 3; i++) orders.place(1, 1, 1, "alice");
        orders.place(1, 1, 1, "bob");
        jdbc.update("update purchase_orders set created_at='2026-01-01T00:00:00Z'");
        var all = search.search(new OrderSearchCriteria(null, null, null, 0, 10), "alice");
        var first = search.search(new OrderSearchCriteria(null, null, null, 0, 2), "alice");
        var second = search.search(new OrderSearchCriteria(null, null, null, 1, 2), "alice");
        assertThat(all.total()).isEqualTo(3);
        assertThat(first.total()).isEqualTo(3);
        assertThat(second.total()).isEqualTo(3);
        var ids = new ArrayList<UUID>();
        first.items().forEach(item -> ids.add(item.id()));
        second.items().forEach(item -> ids.add(item.id()));
        assertThat(ids).doesNotHaveDuplicates().containsExactlyElementsOf(all.items().stream().map(item -> item.id()).toList());
        assertThat(search.search(new OrderSearchCriteria(null, null, null, 2, 2), "alice").items()).isEmpty();
    }

    @Test
    void searchHttpValidatesInputsAndNeverUsesClientSuppliedOwner() throws Exception {
        orders.place(1, 1, 1, "alice");
        orders.place(2, 2, 1, "bob");
        var response = request("GET", "/orders?ownerSubject=bob", "alice", "api.read", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(data(response).get("total").toString()).isEqualTo("1");
        assertThat(data(response).get("page").toString()).isEqualTo("0");
        assertThat(data(response).get("size").toString()).isEqualTo("20");
        assertThat(response.body()).contains("Keyboard").doesNotContain("Mouse", "ownerSubject");
        assertProblem(request("GET", "/orders", "alice", null, null), 401, "UNAUTHORIZED");
        assertProblem(request("GET", "/orders", "alice", "api.write", null), 403, "ACCESS_DENIED");
        for (String query : List.of("size=0", "size=101", "page=-1", "page=10001", "memberId=0", "minimumTotal=-1", "page=no-number")) {
            assertProblem(request("GET", "/orders?" + query, "alice", "api.read", null), 400, "INVALID_REQUEST");
        }
    }

    @Test
    void searchReflectsQuantityChangesAndDeletion() {
        var order = orders.place(1, 1, 1, "alice");
        var criteria = new OrderSearchCriteria(null, null, new BigDecimal("60000"), 0, 20);
        assertThat(search.search(criteria, "alice").total()).isZero();
        orders.updateQuantity(order.id(), 2, "alice");
        var result = search.search(criteria, "alice");
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().totalPrice()).isEqualByComparingTo("100000");
        orders.delete(order.id(), "alice");
        var deleted = search.search(criteria, "alice");
        assertThat(deleted.total()).isZero();
        assertThat(deleted.items()).isEmpty();
    }

    @Test
    void textSearchTreatsSqlAndWildcardCharactersAsLiteralText() {
        orders.place(1, 1, 1, "alice");
        for (String input : List.of("' OR 1=1 --", "%", "_")) {
            var result = search.search(new OrderSearchCriteria(null, input, null, 0, 20), "alice");
            assertThat(result.total()).isZero();
            assertThat(result.items()).isEmpty();
        }
        assertThat(search.search(new OrderSearchCriteria(null, "  ", null, 0, 20), "alice").total()).isEqualTo(1);
    }

    @Test
    void readTransactionKeepsCountAndRowsOnOneSnapshot() {
        orders.place(1, 1, 1, "alice");
        var tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setReadOnly(true);
        tx.executeWithoutResult(status -> {
            assertThat(search.search(new OrderSearchCriteria(null, null, null, 0, 20), "alice").total()).isEqualTo(1);
            // 별도 연결에서 주문이 추가돼도 진행 중인 조회는 같은 snapshot을 사용한다.
            var insert = new TransactionTemplate(transactionManager);
            insert.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            insert.executeWithoutResult(other -> orders.place(1, 1, 1, "alice"));
            var same = search.search(new OrderSearchCriteria(null, null, null, 0, 20), "alice");
            assertThat(same.total()).isEqualTo(1);
            assertThat(same.items()).hasSize(1);
        });
        assertThat(search.search(new OrderSearchCriteria(null, null, null, 0, 20), "alice").total()).isEqualTo(2);
    }
}
