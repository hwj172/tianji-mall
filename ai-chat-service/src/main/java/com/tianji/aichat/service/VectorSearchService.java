package com.tianji.aichat.service;

import com.tianji.aichat.client.EmbeddingClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 向量搜索核心服务：文本 → Embedding → Milvus 向量相似度搜索 → 商品 ID。
 * 所有方法内部捕获异常，不向上抛出（Milvus 不可用时降级为空结果）。
 */
@Slf4j
@Service
public class VectorSearchService {

    private static final String FIELD_ID = "product_id";
    private static final String FIELD_VECTOR = "embedding";

    private final EmbeddingClient embeddingClient;

    @Value("${milvus.host:192.168.150.11}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.collection-name:product_vectors}")
    private String collectionName;

    /** BAAI/bge-large-zh-v1.5 输出 1024 维向量 */
    @Value("${milvus.dimension:1024}")
    private int dimension;

    private MilvusServiceClient milvusClient;

    public VectorSearchService(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    @PostConstruct
    void init() {
        try {
            milvusClient = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .withConnectTimeout(5, TimeUnit.SECONDS)
                    .withRpcDeadline(10, TimeUnit.SECONDS)
                    .build());
            initCollection();
        } catch (Exception e) {
            log.warn("Milvus 客户端初始化失败，向量搜索功能不可用: {}", e.getMessage());
        }
    }

    /**
     * 向量相似度搜索：返回与查询文本最相似的商品 ID 列表（失败返回空列表）
     */
    public List<Long> searchSimilar(String queryText, int topK) {
        if (milvusClient == null) {
            log.warn("Milvus 客户端未初始化，跳过向量搜索");
            return Collections.emptyList();
        }
        try {
            List<Float> queryVector = toFloatList(embeddingClient.embed(queryText));

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withVectorFieldName(FIELD_VECTOR)
                    .withVectors(List.of(queryVector))
                    .withTopK(topK)
                    .withMetricType(MetricType.COSINE)
                    .withParams("{\"nprobe\":16}")
                    .build();

            R<SearchResults> response = milvusClient.search(searchParam);
            if (response.getStatus() != R.Status.Success.getCode() || response.getData() == null) {
                log.warn("Milvus 向量搜索失败: {}", response.getMessage());
                return Collections.emptyList();
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            return wrapper.getIDScore(0).stream()
                    .map(SearchResultsWrapper.IDScore::getLongID)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("向量搜索异常: query={}", queryText, e);
            return Collections.emptyList();
        }
    }

    /**
     * 同步商品向量到 Milvus（upsert，按 productId 覆盖）
     */
    public void upsertProduct(Long productId, String name, String description) {
        if (milvusClient == null) {
            log.warn("Milvus 客户端未初始化，跳过商品向量同步: productId={}", productId);
            return;
        }
        try {
            String text = (description == null || description.isEmpty()) ? name : name + " " + description;
            List<Float> vector = toFloatList(embeddingClient.embed(text));

            List<InsertParam.Field> fields = List.of(
                    new InsertParam.Field(FIELD_ID, List.of(productId)),
                    new InsertParam.Field(FIELD_VECTOR, List.of(vector)));

            R<MutationResult> response = milvusClient.upsert(UpsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build());
            if (response.getStatus() != R.Status.Success.getCode()) {
                log.warn("商品向量同步失败: productId={}, message={}", productId, response.getMessage());
            } else {
                log.info("商品向量已同步: productId={}", productId);
            }
        } catch (Exception e) {
            log.warn("商品向量同步异常: productId={}", productId, e);
        }
    }

    /**
     * 检查并创建 collection（product_id Int64 主键 + embedding FloatVector），建索引后加载
     */
    void initCollection() {
        try {
            R<Boolean> has = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());
            if (has.getStatus() != R.Status.Success.getCode()) {
                log.warn("Milvus 检查 collection 失败: {}", has.getMessage());
                return;
            }

            if (!Boolean.TRUE.equals(has.getData())) {
                milvusClient.createCollection(CreateCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withDescription("商品向量（name + description embedding）")
                        .addFieldType(FieldType.newBuilder()
                                .withName(FIELD_ID)
                                .withDataType(DataType.Int64)
                                .withPrimaryKey(true)
                                .withAutoID(false)
                                .build())
                        .addFieldType(FieldType.newBuilder()
                                .withName(FIELD_VECTOR)
                                .withDataType(DataType.FloatVector)
                                .withDimension(dimension)
                                .build())
                        .build());

                milvusClient.createIndex(CreateIndexParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFieldName(FIELD_VECTOR)
                        .withIndexType(IndexType.IVF_FLAT)
                        .withMetricType(MetricType.COSINE)
                        .withExtraParam("{\"nlist\":128}")
                        .build());
                log.info("Milvus collection 已创建: {}", collectionName);
            }

            milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());
        } catch (Exception e) {
            log.warn("Milvus collection 初始化异常: {}", e.getMessage());
        }
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }
}
