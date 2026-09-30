package com.example.backend.product.contract;
import java.util.Optional;
/** 부재를 값으로 전달하며 HTTP 의미는 호출자에게 맡긴다. */
public interface ProductLookup { Optional<ProductSnapshot> findProduct(long productId); }
