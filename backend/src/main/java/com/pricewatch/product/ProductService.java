package com.pricewatch.product;

import com.pricewatch.account.CurrentRequester;
import com.pricewatch.account.Requester;
import com.pricewatch.account.SignUpRequiredException;
import com.pricewatch.product.PriceRecordRepository.PriceStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service behind the REST controller: CRUD plus mapping entities to API responses.
 * Everything is scoped to the caller, a signed-in account or a guest; anyone else's product is
 * reported as not found, so its existence does not leak. Guests get one reading per product: no
 * re-checks (those need an account) and a small number of products.
 */
@Service
public class ProductService {

    private final ProductRepository products;
    private final PriceRecordRepository records;
    private final PriceCheckService priceChecks;
    private final CurrentRequester requester;
    private final int maxGuestProducts;

    public ProductService(
            ProductRepository products,
            PriceRecordRepository records,
            PriceCheckService priceChecks,
            CurrentRequester requester,
            @Value("${pricewatch.guest.max-products:10}") int maxGuestProducts) {
        this.products = products;
        this.records = records;
        this.priceChecks = priceChecks;
        this.requester = requester;
        this.maxGuestProducts = maxGuestProducts;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        Requester caller = requester.require();
        List<PriceStats> summary = caller.isGuest()
                ? records.summarizeForGuest(caller.guestId())
                : records.summarizeForOwner(caller.account().getId());
        Map<Long, PriceStats> stats = summary.stream()
                .collect(Collectors.toMap(PriceStats::getProductId, Function.identity()));
        List<Product> shelf = caller.isGuest()
                ? products.findByGuestIdOrderByCreatedAtDesc(caller.guestId())
                : products.findByOwnerIdOrderByCreatedAtDesc(caller.account().getId());
        return shelf.stream()
                .map(product -> toResponse(product, stats.get(product.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(find(id));
    }

    public ProductResponse create(CreateProductRequest request) {
        Requester caller = requester.require();
        if (caller.isGuest() && products.countByGuestId(caller.guestId()) >= maxGuestProducts) {
            throw new SignUpRequiredException("Guests can add up to " + maxGuestProducts
                    + " products. Sign up to add more and keep them tracked.");
        }
        Product product = priceChecks.createProduct(
                caller.isGuest() ? null : caller.account().getId(),
                caller.guestId(),
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
        Product product = find(id);
        records.deleteAllForProduct(id);
        products.delete(product);
    }

    @Transactional(readOnly = true)
    public List<PricePointResponse> history(Long id) {
        find(id);
        return records.findByProductIdOrderByCheckedAtAsc(id).stream()
                .map(record -> new PricePointResponse(record.getCheckedAt(), record.getPrice(), record.getAvailability()))
                .toList();
    }

    public ProductResponse checkNow(Long id) {
        Product product = find(id); // only the owner may trigger a check
        if (!product.isTracked()) {
            throw new SignUpRequiredException("Sign up to keep tracking this product and check it again.");
        }
        return toResponse(priceChecks.checkNow(id));
    }

    private Product find(Long id) {
        Requester caller = requester.require();
        return (caller.isGuest()
                ? products.findByIdAndGuestId(id, caller.guestId())
                : products.findByIdAndOwnerId(id, caller.account().getId()))
                .orElseThrow(() -> new ProductNotFoundException(id));
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
                product.isTracked(),
                product.getAvailability(),
                product.getLastCheckedAt(),
                product.getLastError(),
                product.getLastErrorReason(),
                product.getFailedChecks(),
                product.getNextRetryAt(),
                product.getCreatedAt());
    }
}
