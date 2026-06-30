package com.yf.exam.modules.qu.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public interface QuestionDocumentParseService {

    String parseText(MultipartFile file);

    String parseText(File file);
}