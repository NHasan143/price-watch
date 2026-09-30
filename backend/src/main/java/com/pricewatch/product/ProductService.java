package com.pricewatch.product;

import com.pricewatch.product.PriceRecordRepository.PriceStats;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Application service behind the REST controller: CRUD plus mapping entities to API responses. */
@Service
public class ProductService {

    private final ProductRepository products;
    private final PriceRecordRepository records;
    private final PriceCheckService priceChecks;

    public ProductService(ProductRepository products, PriceRecordRepository records, PriceCheckService priceChecks) {
        this.products = products;
        this.records = records;
        this.priceChecks = priceChecks;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        Map<Long, PriceStats> stats = records.summarizeAll().stream()
                .collect(Collectors.toMap(PriceStats::getProductId, Function.identity()));
        return products.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(product -> toResponse(product, stats.get(product.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(find(id));
    }

    public ProductResponse create(CreateProductRequest request) {
        Product product = priceChecks.createProduct(
                request.name(),
                request.url().trim(),
                request.cssSelector(),
                request.targetPrice().setScale(2, RoundingMode.HALF_UP));
        return toResponse(product);
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = find(id);
        if (request.name() != null && !request.name().isBlank()) {
            product.setName(request.name().trim());
        }
        product.setTargetPrice(request.targetPrice().setScale(2, RoundingMode.HALF_UP));
        // If the new target is below the current price, allow a fresh alert once the price drops again.
        if (!product.isBelowTarget()) {
            product.setAlertSent(false);
        }
        return toResponse(product);
    }

    @Transactional
    public void delete(Long id) {
        if (!products.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        records.deleteAllForProduct(id);
        products.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PricePointResponse> history(Long id) {
        if (!products.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        return records.findByProductIdOrderByCheckedAtAsc(id).stream()
                .map(record -> new PricePointResponse(record.getCheckedAt(), record.getPrice()))
                .toList();
    }

    public ProductResponse checkNow(Long id) {
        return toResponse(priceChecks.checkNow(id));
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    private ProductResponse toResponse(Product product) {
        return toResponse(product, records.summarizeProduct(product.getId()).orElse(null));
    }

    private ProductResponse toResponse(Product product, PriceStats stats) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getUrl(),
                product.getImageUrl(),
                product.getCurrency(),
                product.getTargetPrice(),
                product.getCurrentPrice(),
                stats == null ? null : stats.getMinPrice(),
                stats == null ? null : stats.getMaxPrice(),
                product.isBelowTarget(),
                product.getLastCheckedAt(),
                product.getLastError(),
                product.getCreatedAt());
    }
}
