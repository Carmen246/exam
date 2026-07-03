package com.yf.exam.modules.qu.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class RagflowDocumentUploadResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String datasetId;

    private List<String> documentIds = new ArrayList<>();

    private String message;
}
