package com.yf.exam.modules.qu.service.impl;

import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.qu.service.QuestionDocumentParseService;
import com.yf.exam.modules.qu.support.DocxBlankAwareTextExtractor;
import com.yf.exam.modules.qu.support.FillProgramBlankProcessor;
import com.yf.exam.modules.qu.support.QuestionTextLocalNormalizer;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Service
public class QuestionDocumentParseServiceImpl implements QuestionDocumentParseService {

    @Autowired
    private QuestionTextLocalNormalizer localNormalizer;

    @Autowired
    private DocxBlankAwareTextExtractor docxBlankAwareTextExtractor;

    @Autowired
    private FillProgramBlankProcessor fillProgramBlankProcessor;

    @Override
    public String parseText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空！");
        }
        return normalizeLocally(readFileContent(file.getOriginalFilename(), () -> file.getInputStream(),
                () -> file.getBytes()));
    }

    @Override
    public String parseText(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new ServiceException("上传文件不能为空！");
        }
        return normalizeLocally(readFileContent(file.getName(), () -> new FileInputStream(file),
                () -> Files.readAllBytes(file.toPath())));
    }

    @Override
    public String extractRawText(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new ServiceException("上传文件不能为空！");
        }
        return readFileContent(file.getName(), () -> new FileInputStream(file), () -> Files.readAllBytes(file.toPath()));
    }

    @Override
    public String normalizeLocally(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String marked = fillProgramBlankProcessor.markSpacePaddedBlanks(text);
        return localNormalizer.normalize(marked);
    }

    @Override
    public int countQuestionBlocks(String text) {
        return localNormalizer.countQuestionBlocks(text);
    }

    private String readFileContent(String fileName, InputStreamSupplier inputStreamSupplier, BytesSupplier bytesSupplier) {
        String ext = FilenameUtils.getExtension(fileName);
        if (StringUtils.isBlank(ext)) {
            throw new ServiceException("无法识别文件类型！");
        }

        try {
            if ("docx".equalsIgnoreCase(ext)) {
                return docxBlankAwareTextExtractor.extract(inputStreamSupplier.get());
            }
            if ("txt".equalsIgnoreCase(ext)) {
                return new String(bytesSupplier.get(), StandardCharsets.UTF_8);
            }
            throw new ServiceException("暂只支持 docx、txt 文件！");
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("试题文档解析失败：" + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface InputStreamSupplier {
        java.io.InputStream get() throws Exception;
    }

    @FunctionalInterface
    private interface BytesSupplier {
        byte[] get() throws Exception;
    }
}
