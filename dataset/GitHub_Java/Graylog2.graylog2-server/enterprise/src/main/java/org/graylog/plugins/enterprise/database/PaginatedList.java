package org.graylog.plugins.enterprise.database;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ForwardingList;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

public class PaginatedList<E> extends ForwardingList<E> {

    private final List<E> delegate;

    private final PaginationInfo paginationInfo;

    public PaginatedList(@Nonnull List<E> delegate, int globalTotal, int page, int perPage) {
        this.delegate = delegate;
        this.paginationInfo = new PaginationInfo(globalTotal, page, perPage);
    }

    @Override
    public List<E> delegate() {
        return delegate;
    }

    public PaginationInfo pagination() {
        return paginationInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaginatedList)) return false;
        PaginatedList<?> that = (PaginatedList<?>) o;
        return Objects.equals(pagination(), that.pagination()) &&
                Objects.equals(delegate(), that.delegate());
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate(), pagination());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("content", delegate)
                .add("pagination_info", pagination())
                .toString();
    }

    @JsonAutoDetect
    public class PaginationInfo {
        private final int globalTotal;
        private final int page;
        private final int perPage;

        public PaginationInfo(int globalTotal, int page, int perPage) {
            this.globalTotal = globalTotal;
            this.page = page;
            this.perPage = perPage;
        }

        @JsonProperty("count")
        public int getCount() {
            return delegate().size();
        }

        @JsonProperty("total")
        public int getGlobalTotal() {
            return globalTotal;
        }

        @JsonProperty("page")
        public int getPage() {
            return page;
        }

        @JsonProperty("per_page")
        public int getPerPage() {
            return perPage;
        }

        public ImmutableMap<String, Object> asMap() {
            return ImmutableMap.of(
                    "total", globalTotal,
                    "page", page,
                    "per_page", perPage,
                    "count", getCount()
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PaginatedList.PaginationInfo)) return false;
            @SuppressWarnings("unchecked")
            PaginationInfo that = (PaginationInfo) o;
            return globalTotal == that.globalTotal &&
                    page == that.page &&
                    perPage == that.perPage;
        }

        @Override
        public int hashCode() {
            return Objects.hash(globalTotal, page, perPage);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("globalTotal", globalTotal)
                    .add("page", page)
                    .add("perPage", perPage)
                    .toString();
        }
    }
}
