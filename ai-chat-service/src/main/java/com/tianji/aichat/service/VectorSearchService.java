package com.tianji.aichat.service;

import com.tianji.aichat.client.EmbeddingClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.grpc.LoadState;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.grpc.GetLoadStateResponse;
import io.milvus.param.collection.GetLoadStateParam;
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

    // ===== 图像向量集合（以图搜图，VL-Embedding 维度动态） =====
    private static final String IMAGE_COLLECTION = "product_image_vectors";
    private static final String IMAGE_FIELD_ID = "product_id";
    private static final String IMAGE_FIELD_VECTOR = "image_embedding";

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
            preloadImageCollection();
        } catch (Exception e) {
            log.warn("Milvus 客户端初始化失败，向量搜索功能不可用: {}", e.getMessage());
        }
    }

    /**
     * 启动时预加载图片向量集合（若已存在），避免首次搜索触发异步加载导致 collection not loaded。
     */
    private void preloadImageCollection() {
        try {
            R<Boolean> has = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(IMAGE_COLLECTION)
                    .build());
            if (Boolean.TRUE.equals(has.getData())) {
                milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                        .withCollectionName(IMAGE_COLLECTION)
                        .build());
                waitForCollectionLoaded(IMAGE_COLLECTION);
                log.info("图片向量集合已预加载: {}", IMAGE_COLLECTION);
            }
        } catch (Exception e) {
            log.warn("图片向量集合预加载失败: {}", e.getMessage());
        }
    }

    /**
     * 等待 Milvus 集合加载完成：轮询加载状态（替代固定 sleep），
     * 小集合立即就绪、大集合最多等待 10s，避免固定 3s 空等或等待不足。
     */
    private void waitForCollectionLoaded(String collection) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                R<GetLoadStateResponse> st = milvusClient.getLoadState(GetLoadStateParam.newBuilder()
                        .withCollectionName(collection)
                        .build());
                if (st.getData() != null && st.getData().getState() == LoadState.LoadStateLoaded) {
                    return;
                }
                Thread.sleep(300);
            } catch (Exception e) {
                log.warn("查询集合加载状态失败: {}", e.getMessage());
                return; // 查询失败不阻塞，直接继续
            }
        }
        log.warn("等待集合加载超时（10s）: {}", collection);
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
     * 商品图片向量 upsert（以图搜图，按 productId 覆盖）
     */
    public void upsertProductImage(Long productId, String imageUrl) {
        if (milvusClient == null) {
            log.warn("Milvus 客户端未初始化，跳过图片向量同步: productId={}", productId);
            return;
        }
        try {
            float[] vector = embeddingClient.embedImage(imageUrl);
            ensureImageCollection(vector.length);
            List<Float> vec = toFloatList(vector);
            List<InsertParam.Field> fields = List.of(
                    new InsertParam.Field(IMAGE_FIELD_ID, List.of(productId)),
                    new InsertParam.Field(IMAGE_FIELD_VECTOR, List.of(vec)));
            R<MutationResult> response = milvusClient.upsert(UpsertParam.newBuilder()
                    .withCollectionName(IMAGE_COLLECTION)
                    .withFields(fields)
                    .build());
            if (response.getStatus() != R.Status.Success.getCode()) {
                log.warn("商品图片向量同步失败: productId={}, message={}", productId, response.getMessage());
            } else {
                log.info("商品图片向量已同步: productId={}", productId);
            }
        } catch (Exception e) {
            log.warn("商品图片向量同步异常: productId={}", productId, e);
        }
    }

    /**
     * 以图搜图：图片 → VL-Embedding → Milvus 图像集合相似度搜索 → 商品 ID（失败返回空列表）
     */
    public List<Long> searchByImage(String imageUrl, int topK) {
        if (milvusClient == null) {
            log.warn("Milvus 客户端未初始化，跳过图片搜索");
            return Collections.emptyList();
        }
        try {
            float[] vector = embeddingClient.embedImage(imageUrl);
            List<Float> queryVector = toFloatList(vector);

            // 搜索前确保图片集合已加载（Milvus 重启后集合默认未加载，搜索会报 collection not loaded）
            // loadCollection 是异步的，轮询加载状态等待完成再搜索
            milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(IMAGE_COLLECTION)
                    .build());
            waitForCollectionLoaded(IMAGE_COLLECTION);

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(IMAGE_COLLECTION)
                    .withVectorFieldName(IMAGE_FIELD_VECTOR)
                    .withVectors(List.of(queryVector))
                    .withTopK(topK)
                    .withMetricType(MetricType.COSINE)
                    .withParams("{\"nprobe\":16}")
                    .build();

            R<SearchResults> response = milvusClient.search(searchParam);
            if (response.getStatus() != R.Status.Success.getCode() || response.getData() == null) {
                log.warn("图片向量搜索失败: {}", response.getMessage());
                return Collections.emptyList();
            }
            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            return wrapper.getIDScore(0).stream()
                    .map(SearchResultsWrapper.IDScore::getLongID)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("图片向量搜索异常: imageUrl={}", imageUrl, e);
            return Collections.emptyList();
        }
    }

    /**
     * 确保图像集合存在（VL-Embedding 维度动态，按首次向量的长度创建）
     */
    private void ensureImageCollection(int dim) {
        try {
            R<Boolean> has = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(IMAGE_COLLECTION)
                    .build());
            if (has.getStatus() != R.Status.Success.getCode()) {
                log.warn("检查图像集合失败: {}", has.getMessage());
                return;
            }
            if (!Boolean.TRUE.equals(has.getData())) {
                milvusClient.createCollection(CreateCollectionParam.newBuilder()
                        .withCollectionName(IMAGE_COLLECTION)
                        .withDescription("商品图片向量（VL-Embedding）")
                        .addFieldType(FieldType.newBuilder()
                                .withName(IMAGE_FIELD_ID)
                                .withDataType(DataType.Int64)
                                .withPrimaryKey(true)
                                .withAutoID(false)
                                .build())
                        .addFieldType(FieldType.newBuilder()
                                .withName(IMAGE_FIELD_VECTOR)
                                .withDataType(DataType.FloatVector)
                                .withDimension(dim)
                                .build())
                        .build());
                milvusClient.createIndex(CreateIndexParam.newBuilder()
                        .withCollectionName(IMAGE_COLLECTION)
                        .withFieldName(IMAGE_FIELD_VECTOR)
                        .withIndexType(IndexType.IVF_FLAT)
                        .withMetricType(MetricType.COSINE)
                        .withExtraParam("{\"nlist\":128}")
                        .build());
                log.info("图像向量集合已创建: {} dim={}", IMAGE_COLLECTION, dim);
            }
            milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(IMAGE_COLLECTION)
                    .build());
        } catch (Exception e) {
            log.warn("图像集合初始化异常: {}", e.getMessage());
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
