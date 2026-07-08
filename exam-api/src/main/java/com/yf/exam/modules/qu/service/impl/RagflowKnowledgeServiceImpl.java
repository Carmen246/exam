package com.yf.exam.modules.qu.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.qu.config.QuestionAiConfigProvider;
import com.yf.exam.modules.qu.config.QuestionAiProperties;
import com.yf.exam.modules.qu.dto.RagflowDocumentUploadResultDTO;
import com.yf.exam.modules.qu.service.RagflowKnowledgeService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class RagflowKnowledgeServiceImpl implements RagflowKnowledgeService {

    private static final int DEFAULT_DATASET_PAGE_SIZE = 10;

    @Autowired
    private QuestionAiConfigProvider questionAiConfigProvider;

    private QuestionAiProperties properties() {
        return questionAiConfigProvider.getEffective();
    }

    private volatile String resolvedDatasetId;
    private volatile String datasetResolveError;

    @Override
    public boolean isAutoUploadRequested() {
        return Boolean.TRUE.equals(properties().getRagflowAutoUpload());
    }

    @Override
    public boolean isReadyForUpload() {
        return isAutoUploadRequested()
                && StringUtils.isNotBlank(ragflowBaseUrl())
                && StringUtils.isNotBlank(ragflowApiKey())
                && StringUtils.isNotBlank(resolveDatasetIdQuietly());
    }

    @Override
    public String getUploadSkipReason() {
        if (!isAutoUploadRequested()) {
            return "RAGFlow 知识库自动上传未开启";
        }
        List<String> missing = new ArrayList<>();
        if (StringUtils.isBlank(ragflowBaseUrl())) {
            missing.add("exam.ai.ragflow-base-url");
        }
        if (StringUtils.isBlank(ragflowApiKey())) {
            missing.add("exam.ai.ragflow-api-key");
        }
        if (missing.isEmpty()) {
            String datasetId = resolveDatasetIdQuietly();
            if (StringUtils.isNotBlank(datasetId)) {
                return "";
            }
            return StringUtils.defaultIfBlank(datasetResolveError,
                    "RAGFlow 知识库自动上传配置不完整：未配置 exam.ai.ragflow-dataset-id，且未从知识库列表接口获取到可用知识库");
        }
        return "RAGFlow 知识库自动上传配置不完整：" + String.join("、", missing);
    }

    @Override
    public String getDatasetId() {
        String configuredDatasetId = StringUtils.trimToEmpty(properties().getRagflowDatasetId());
        if (StringUtils.isNotBlank(configuredDatasetId)) {
            return configuredDatasetId;
        }
        return StringUtils.trimToEmpty(resolvedDatasetId);
    }

    @Override
    public RagflowDocumentUploadResultDTO uploadAndParse(File file, String originalFileName) {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new ServiceException("RAGFlow 上传文件不存在");
        }
        if (!isReadyForUpload()) {
            throw new ServiceException(getUploadSkipReason());
        }

        String datasetId = resolveDatasetId();
        RagflowDocumentUploadResultDTO result = uploadDocument(file, originalFileName, datasetId);
        startParse(datasetId, result.getDocumentIds());
        result.setMessage("RAGFlow 知识库已上传并触发解析，文档ID：" + String.join(",", result.getDocumentIds()));
        return result;
    }

    private RagflowDocumentUploadResultDTO uploadDocument(File file, String originalFileName, String datasetId) {
        String url = apiUrl("api", "v1", "datasets", datasetId, "documents");

        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", namedFileResource(file, originalFileName));

        ResponseEntity<String> response = restTemplate().exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        JSONObject json = parseResponse(response.getBody(), "上传 RAGFlow 文档失败");
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new ServiceException("上传 RAGFlow 文档失败：未返回文档ID");
        }

        RagflowDocumentUploadResultDTO result = new RagflowDocumentUploadResultDTO();
        result.setDatasetId(datasetId);
        List<String> documentIds = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            String documentId = item == null ? null : item.getString("id");
            if (StringUtils.isNotBlank(documentId)) {
                documentIds.add(documentId);
            }
        }
        if (documentIds.isEmpty()) {
            throw new ServiceException("上传 RAGFlow 文档失败：未返回文档ID");
        }
        result.setDocumentIds(documentIds);
        return result;
    }

    private void startParse(String datasetId, List<String> documentIds) {
        String url = apiUrl("api", "v1", "datasets", datasetId, "chunks");

        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        JSONObject payload = new JSONObject();
        payload.put("document_ids", documentIds);

        ResponseEntity<String> response = restTemplate().exchange(url, HttpMethod.POST,
                new HttpEntity<>(payload.toJSONString(), headers), String.class);
        parseResponse(response.getBody(), "触发 RAGFlow 文档解析失败");
    }

    private String resolveDatasetIdQuietly() {
        try {
            return resolveDatasetId();
        } catch (Exception e) {
            datasetResolveError = e instanceof ServiceException
                    ? e.getMessage()
                    : "获取 RAGFlow 知识库列表失败：" + e.getMessage();
            return "";
        }
    }

    private String resolveDatasetId() {
        String configuredDatasetId = StringUtils.trimToEmpty(properties().getRagflowDatasetId());
        if (StringUtils.isNotBlank(configuredDatasetId)) {
            resolvedDatasetId = configuredDatasetId;
            datasetResolveError = "";
            return configuredDatasetId;
        }
        if (StringUtils.isNotBlank(resolvedDatasetId)) {
            return resolvedDatasetId;
        }

        JSONObject json = queryDatasetList();
        JSONArray datasets = extractDatasetArray(json);
        if (datasets == null || datasets.isEmpty()) {
            throw new ServiceException("RAGFlow 知识库列表为空，请先在 RAGFlow 创建知识库，或配置 exam.ai.ragflow-dataset-id");
        }

        JSONObject selected = selectDataset(datasets);
        String datasetId = getFirstString(selected, "id", "dataset_id", "datasetId", "kb_id");
        if (StringUtils.isBlank(datasetId)) {
            throw new ServiceException("RAGFlow 知识库列表未返回可用 ID，请检查 /api/v1/datasets 接口响应");
        }

        resolvedDatasetId = datasetId;
        datasetResolveError = "";
        return datasetId;
    }

    private JSONObject queryDatasetList() {
        String url = datasetListUrl();
        ResponseEntity<String> response = restTemplate().exchange(url, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);
        return parseResponse(response.getBody(), "获取 RAGFlow 知识库列表失败");
    }

    private JSONArray extractDatasetArray(JSONObject json) {
        if (json == null) {
            return new JSONArray();
        }
        Object data = json.get("data");
        if (data instanceof JSONArray) {
            return (JSONArray) data;
        }
        if (data instanceof JSONObject) {
            JSONObject dataObject = (JSONObject) data;
            JSONArray datasets = firstArray(dataObject, "datasets", "items", "list", "records", "rows");
            return datasets == null ? new JSONArray() : datasets;
        }
        return new JSONArray();
    }

    private JSONObject selectDataset(JSONArray datasets) {
        String datasetName = StringUtils.trimToEmpty(properties().getRagflowDatasetName());
        JSONObject firstWithId = null;
        for (int i = 0; i < datasets.size(); i++) {
            JSONObject item = toJsonObject(datasets.get(i));
            if (item == null) {
                continue;
            }
            String id = getFirstString(item, "id", "dataset_id", "datasetId", "kb_id");
            if (StringUtils.isBlank(id)) {
                continue;
            }
            if (firstWithId == null) {
                firstWithId = item;
            }
            if (StringUtils.isNotBlank(datasetName)
                    && StringUtils.equalsIgnoreCase(datasetName, StringUtils.trimToEmpty(item.getString("name")))) {
                return item;
            }
        }
        if (StringUtils.isNotBlank(datasetName)) {
            throw new ServiceException("未找到名称为 " + datasetName + " 的 RAGFlow 知识库，请检查 exam.ai.ragflow-dataset-name");
        }
        if (firstWithId == null) {
            throw new ServiceException("RAGFlow 知识库列表未返回可用 ID，请检查 /api/v1/datasets 接口响应");
        }
        return firstWithId;
    }

    private JSONObject toJsonObject(Object value) {
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        try {
            return JSON.parseObject(JSON.toJSONString(value));
        } catch (Exception e) {
            return null;
        }
    }

    private JSONArray firstArray(JSONObject object, String... keys) {
        for (String key : keys) {
            JSONArray array = object.getJSONArray(key);
            if (array != null) {
                return array;
            }
        }
        return null;
    }

    private String getFirstString(JSONObject object, String... keys) {
        if (object == null) {
            return "";
        }
        for (String key : keys) {
            String value = StringUtils.trimToEmpty(object.getString(key));
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private FileSystemResource namedFileResource(File file, String originalFileName) {
        final String uploadName = StringUtils.defaultIfBlank(originalFileName, file.getName());
        return new FileSystemResource(file) {
            @Override
            public String getFilename() {
                return uploadName;
            }
        };
    }

    private JSONObject parseResponse(String body, String action) {
        JSONObject json;
        try {
            json = JSON.parseObject(body);
        } catch (Exception e) {
            throw new ServiceException(action + "：返回内容不是合法 JSON");
        }
        Integer code = json == null ? null : json.getInteger("code");
        if (code != null && code != 0) {
            throw new ServiceException(action + "：" + StringUtils.defaultIfBlank(json.getString("message"), body));
        }
        return json == null ? new JSONObject() : json;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String apiKey = ragflowApiKey().trim();
        if (StringUtils.startsWithIgnoreCase(apiKey, "Bearer ")) {
            headers.set(HttpHeaders.AUTHORIZATION, apiKey);
        } else {
            headers.setBearerAuth(apiKey);
        }
        return headers;
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = properties().getRagflowUploadTimeoutSeconds() == null
                ? 120
                : properties().getRagflowUploadTimeoutSeconds();
        factory.setConnectTimeout(timeout * 1000);
        factory.setReadTimeout(timeout * 1000);
        return new RestTemplate(factory);
    }

    private String apiUrl(String... segments) {
        String root = trimTrailingSlash(ragflowBaseUrl());
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(root);
        for (String segment : segments) {
            builder.pathSegment(segment);
        }
        return builder.build().toUriString();
    }

    private String datasetListUrl() {
        int pageSize = properties().getRagflowDatasetPageSize() == null || properties().getRagflowDatasetPageSize() <= 0
                ? DEFAULT_DATASET_PAGE_SIZE
                : properties().getRagflowDatasetPageSize();
        return UriComponentsBuilder.fromHttpUrl(trimTrailingSlash(ragflowBaseUrl()))
                .pathSegment("api", "v1", "datasets")
                .queryParam("page", 1)
                .queryParam("page_size", pageSize)
                .build()
                .toUriString();
    }

    private String ragflowBaseUrl() {
        String dedicated = StringUtils.trimToEmpty(properties().getRagflowBaseUrl());
        if (StringUtils.isNotBlank(dedicated)) {
            return dedicated;
        }
        if ("ragflow".equalsIgnoreCase(StringUtils.trimToEmpty(properties().getProvider()))) {
            return StringUtils.trimToEmpty(properties().getBaseUrl());
        }
        return "";
    }

    private String ragflowApiKey() {
        String dedicated = StringUtils.trimToEmpty(properties().getRagflowApiKey());
        if (StringUtils.isNotBlank(dedicated)) {
            return dedicated;
        }
        if ("ragflow".equalsIgnoreCase(StringUtils.trimToEmpty(properties().getProvider()))) {
            return StringUtils.trimToEmpty(properties().getApiKey());
        }
        return "";
    }

    private String trimTrailingSlash(String url) {
        String value = StringUtils.trimToEmpty(url);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
