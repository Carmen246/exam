package com.yf.exam.modules.qu.service.impl;

import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.qu.support.DocToDocxConverter;
import com.yf.exam.modules.qu.support.DocxBlankAwareTextExtractor;
import com.yf.exam.modules.qu.support.FillProgramBlankProcessor;
import com.yf.exam.modules.qu.support.PdfTextExtractor;
import com.yf.exam.modules.qu.support.QuestionTextLocalNormalizer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionDocumentParseServiceImplTest {

    @Test
    void extractsOfficeMathTextFromDocx() throws Exception {
        DocxBlankAwareTextExtractor extractor = new DocxBlankAwareTextExtractor();
        inject(extractor, "blankProcessor", new FillProgramBlankProcessor());

        String text = extractor.extract(new ByteArrayInputStream(minimalDocxWithOfficeMath()));

        assertTrue(text.contains("有分段函数如下定义"));
        assertTrue(text.contains("y="));
        assertTrue(text.contains("x<1"));
        assertTrue(text.contains("2x-1"));
        assertTrue(text.contains("x>=10"));
    }

    @Test
    void marksEmbeddedPictureContentThatCannotBeReadAsText() throws Exception {
        DocxBlankAwareTextExtractor extractor = new DocxBlankAwareTextExtractor();
        inject(extractor, "blankProcessor", new FillProgramBlankProcessor());

        String text = extractor.extract(new ByteArrayInputStream(minimalDocxWithInlinePicture()));

        assertTrue(text.contains("有分段函数如下定义"));
        assertTrue(text.contains("图片内容未识别"));
        assertTrue(text.contains("请将图片中的公式"));
    }

    @Test
    void rejectsInvalidLegacyDocFiles() throws Exception {
        QuestionDocumentParseServiceImpl service = new QuestionDocumentParseServiceImpl();
        inject(service, "docToDocxConverter", new DocToDocxConverter());
        inject(service, "docxBlankAwareTextExtractor", new DocxBlankAwareTextExtractor());
        inject(service, "pdfTextExtractor", new PdfTextExtractor());
        inject(service, "localNormalizer", new QuestionTextLocalNormalizer());
        inject(service, "fillProgramBlankProcessor", new FillProgramBlankProcessor());

        File file = File.createTempFile("legacy-word-", ".doc");
        Files.write(file.toPath(), "legacy doc".getBytes(StandardCharsets.UTF_8));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.extractRawText(file));

        assertTrue(ex.getMessage().contains("旧版 Word .doc 文件转换失败"));
        assertFalse(ex.getMessage().contains("暂不支持旧版 Word .doc 文件"));
    }
    private byte[] minimalDocxWithOfficeMath() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            addEntry(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                            + "</Types>");
            addEntry(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                            + "</Relationships>");
            addEntry(zip, "word/document.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
                            + "xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">"
                            + "<w:body><w:p>"
                            + "<w:r><w:t>2. 有分段函数如下定义：</w:t></w:r>"
                            + "<m:oMath><m:r><m:t>y=</m:t></m:r>"
                            + "<m:eqArr>"
                            + "<m:e><m:r><m:t>x</m:t></m:r><m:r><m:t> x&lt;1</m:t></m:r></m:e>"
                            + "<m:e><m:r><m:t>2x-1</m:t></m:r><m:r><m:t> 1&lt;=x&lt;10</m:t></m:r></m:e>"
                            + "<m:e><m:r><m:t>3x-11</m:t></m:r><m:r><m:t> x&gt;=10</m:t></m:r></m:e>"
                            + "</m:eqArr></m:oMath>"
                            + "<w:r><w:t> 编写一个函数 float fx(float x)。</w:t></w:r>"
                            + "</w:p></w:body></w:document>");
        }
        return out.toByteArray();
    }

    private byte[] minimalDocxWithInlinePicture() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            addEntry(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                            + "</Types>");
            addEntry(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                            + "</Relationships>");
            addEntry(zip, "word/document.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
                            + "xmlns:v=\"urn:schemas-microsoft-com:vml\">"
                            + "<w:body><w:p>"
                            + "<w:r><w:t>2. 有分段函数如下定义：</w:t></w:r>"
                            + "<w:r><w:pict><v:shape id=\"formula\"/></w:pict></w:r>"
                            + "<w:r><w:t> 编写一个函数 float fx(float x)。</w:t></w:r>"
                            + "</w:p></w:body></w:document>");
        }
        return out.toByteArray();
    }

    private void addEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
