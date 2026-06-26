package com.yf.exam.modules.qu.service;

import org.springframework.web.multipart.MultipartFile;

public interface QuestionDocumentParseService {

    String parseText(MultipartFile file);
}