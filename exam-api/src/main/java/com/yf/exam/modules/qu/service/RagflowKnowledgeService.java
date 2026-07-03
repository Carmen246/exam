package com.yf.exam.modules.qu.service;

import com.yf.exam.modules.qu.dto.RagflowDocumentUploadResultDTO;

import java.io.File;

public interface RagflowKnowledgeService {

    boolean isAutoUploadRequested();

    boolean isReadyForUpload();

    String getUploadSkipReason();

    String getDatasetId();

    RagflowDocumentUploadResultDTO uploadAndParse(File file, String originalFileName);
}
