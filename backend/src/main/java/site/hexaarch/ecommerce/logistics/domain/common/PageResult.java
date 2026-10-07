package site.hexaarch.ecommerce.logistics.domain.common;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 分页结果容器，用于在 domain / application / interfaces 各层之间传递分页查询结果。
 *
 * @param <T> 数据元素类型
 * @author kenyon
 */
public class PageResult<T> {
    private final List<T> content;
    private final long total;
    private final int page;
    private final int size;

    public PageResult(List<T> content, long total, int page, int size) {
        this.content = content == null ? new ArrayList<>() : content;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /**
     * 空结果
     */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(new ArrayList<>(), 0L, page, size);
    }

    /**
     * 将当前分页结果中的数据元素映射为另一种类型（保持分页元信息不变）
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = new ArrayList<>(this.content.size());
        for (T item : this.content) {
            mapped.add(mapper.apply(item));
        }
        return new PageResult<>(mapped, this.total, this.page, this.size);
    }

    public List<T> getContent() {
        return content;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    /**
     * 总页数
     */
    public int getTotalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / (double) size);
    }
}
