package com.tianji.aichat.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.IDs;
import io.milvus.grpc.LongArray;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.index.CreateIndexParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorSearchServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private MilvusServiceClient milvusClient;

    private VectorSearchService vectorSearchService;

    @BeforeEach
    void setUp() {
        vectorSearchService = new VectorSearchService(embeddingModel);
        ReflectionTestUtils.setField(vectorSearchService, "milvusClient", milvusClient);
        ReflectionTestUtils.setField(vectorSearchService, "collectionName", "product_vectors");
        ReflectionTestUtils.setField(vectorSearchService, "dimension", 4);
    }

    // ==================== searchSimilar ====================

    @Test
    void shouldReturnProductIdsWhenSearchSucceeds() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f, 0.4f});
        when(milvusClient.search(any(SearchParam.class))).thenReturn(buildSearchResults(101L, 102L, 103L));

        List<Long> result = vectorSearchService.searchSimilar("适合学生的轻薄笔记本", 3);

        assertThat(result).containsExactly(101L, 102L, 103L);
    }

    @Test
    void shouldReturnEmptyListWhenSearchReturnsFailedStatus() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f, 0.4f});
        when(milvusClient.search(any(SearchParam.class)))
                .thenReturn(R.failed(new RuntimeException("collection not loaded")));

        List<Long> result = vectorSearchService.searchSimilar("手机", 5);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenSearchThrows() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f, 0.4f});
        when(milvusClient.search(any(SearchParam.class))).thenThrow(new RuntimeException("connect failed"));

        List<Long> result = vectorSearchService.searchSimilar("手机", 5);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenEmbeddingFails() {
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("embedding api error"));

        List<Long> result = vectorSearchService.searchSimilar("手机", 5);

        assertThat(result).isEmpty();
        verify(milvusClient, never()).search(any(SearchParam.class));
    }

    @Test
    void shouldReturnEmptyListWhenMilvusClientNotInitialized() {
        ReflectionTestUtils.setField(vectorSearchService, "milvusClient", null);

        List<Long> result = vectorSearchService.searchSimilar("手机", 5);

        assertThat(result).isEmpty();
    }

    // ==================== upsertProduct ====================

    @Test
    void shouldUpsertProductWithoutError() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f, 0.4f});
        when(milvusClient.upsert(any(UpsertParam.class)))
                .thenReturn(R.success(MutationResult.getDefaultInstance()));

        assertThatCode(() -> vectorSearchService.upsertProduct(101L, "iPhone 15", "苹果旗舰手机"))
                .doesNotThrowAnyException();

        verify(milvusClient).upsert(any(UpsertParam.class));
    }

    @Test
    void shouldNotThrowWhenUpsertFails() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f, 0.4f});
        when(milvusClient.upsert(any(UpsertParam.class))).thenThrow(new RuntimeException("connect failed"));

        assertThatCode(() -> vectorSearchService.upsertProduct(101L, "iPhone 15", "苹果旗舰手机"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowWhenUpsertWithNullDescription() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f, 0.4f});
        when(milvusClient.upsert(any(UpsertParam.class)))
                .thenReturn(R.success(MutationResult.getDefaultInstance()));

        assertThatCode(() -> vectorSearchService.upsertProduct(101L, "iPhone 15", null))
                .doesNotThrowAnyException();
    }

    // ==================== initCollection ====================

    @Test
    void shouldSkipCreateWhenCollectionExists() {
        when(milvusClient.hasCollection(any(HasCollectionParam.class))).thenReturn(R.success(Boolean.TRUE));
        when(milvusClient.loadCollection(any(LoadCollectionParam.class)))
                .thenReturn(R.success(new RpcStatus("ok")));

        vectorSearchService.initCollection();

        verify(milvusClient, never()).createCollection(any(CreateCollectionParam.class));
        verify(milvusClient).loadCollection(any(LoadCollectionParam.class));
    }

    @Test
    void shouldCreateCollectionAndIndexWhenNotExists() {
        when(milvusClient.hasCollection(any(HasCollectionParam.class))).thenReturn(R.success(Boolean.FALSE));
        when(milvusClient.createCollection(any(CreateCollectionParam.class)))
                .thenReturn(R.success(new RpcStatus("ok")));
        when(milvusClient.createIndex(any(CreateIndexParam.class)))
                .thenReturn(R.success(new RpcStatus("ok")));
        when(milvusClient.loadCollection(any(LoadCollectionParam.class)))
                .thenReturn(R.success(new RpcStatus("ok")));

        vectorSearchService.initCollection();

        verify(milvusClient).createCollection(any(CreateCollectionParam.class));
        verify(milvusClient).createIndex(any(CreateIndexParam.class));
        verify(milvusClient).loadCollection(any(LoadCollectionParam.class));
    }

    // ==================== helpers ====================

    /**
     * 构造 Milvus 搜索结果：单条查询，返回指定 ID 列表
     */
    private R<SearchResults> buildSearchResults(Long... ids) {
        LongArray.Builder longArray = LongArray.newBuilder();
        SearchResultData.Builder data = SearchResultData.newBuilder()
                .setNumQueries(1)
                .setTopK(ids.length)
                .addTopks(ids.length);
        for (Long id : ids) {
            longArray.addData(id);
            data.addScores(0.9f);
        }
        data.setIds(IDs.newBuilder().setIntId(longArray));
        return R.success(SearchResults.newBuilder().setResults(data).build());
    }
}
