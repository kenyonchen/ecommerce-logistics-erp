package site.hexaarch.ecommerce.logistics.domain.product.repository;

/**
 * 商品查询条件，封装商品列表的分页与过滤参数。
 *
 * @author kenyon
 */
public record ProductQuery(
        String tenantId,
        String keyword,
        String categoryName,
        Boolean active,
        String sourcePlatform,
        String skuCode,
        int page,
        int size,
        String sort
) {
    public static ProductQueryBuilder builder() {
        return new ProductQueryBuilder();
    }

    /**
     * 默认分页（第一页，每页 10 条，按创建时间倒序）
     */
    public static ProductQuery defaults(String tenantId) {
        return new ProductQuery(tenantId, null, null, null, null, null, 0, 10, "createdAt,desc");
    }

    public static final class ProductQueryBuilder {
        private String tenantId;
        private String keyword;
        private String categoryName;
        private Boolean active;
        private String sourcePlatform;
        private String skuCode;
        private int page = 0;
        private int size = 10;
        private String sort = "createdAt,desc";

        public ProductQueryBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public ProductQueryBuilder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public ProductQueryBuilder categoryName(String categoryName) {
            this.categoryName = categoryName;
            return this;
        }

        public ProductQueryBuilder active(Boolean active) {
            this.active = active;
            return this;
        }

        public ProductQueryBuilder sourcePlatform(String sourcePlatform) {
            this.sourcePlatform = sourcePlatform;
            return this;
        }

        public ProductQueryBuilder skuCode(String skuCode) {
            this.skuCode = skuCode;
            return this;
        }

        /**
         * 页码，从 0 开始
         */
        public ProductQueryBuilder page(Integer page) {
            this.page = page == null || page < 0 ? 0 : page;
            return this;
        }

        public ProductQueryBuilder size(Integer size) {
            if (size == null || size <= 0) {
                this.size = 10;
            } else {
                this.size = Math.min(size, 500);
            }
            return this;
        }

        /**
         * 排序，格式：属性名,asc|desc
         */
        public ProductQueryBuilder sort(String sort) {
            this.sort = sort;
            return this;
        }

        public ProductQuery build() {
            return new ProductQuery(tenantId, keyword, categoryName, active, sourcePlatform, skuCode, page, size, sort);
        }
    }
}
