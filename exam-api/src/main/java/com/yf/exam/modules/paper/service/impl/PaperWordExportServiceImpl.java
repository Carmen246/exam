package com.yf.exam.modules.paper.service.impl;

import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.paper.dto.ext.PaperQuAnswerExtDTO;
import com.yf.exam.modules.paper.dto.ext.PaperQuDetailDTO;
import com.yf.exam.modules.paper.dto.request.PaperRandomWordExportReqDTO;
import com.yf.exam.modules.paper.dto.request.PaperWordExportReqDTO;
import com.yf.exam.modules.paper.entity.Paper;
import com.yf.exam.modules.paper.service.PaperQuService;
import com.yf.exam.modules.paper.service.PaperService;
import com.yf.exam.modules.paper.service.PaperWordExportService;
import com.yf.exam.modules.qu.entity.Qu;
import com.yf.exam.modules.qu.entity.QuAnswer;
import com.yf.exam.modules.qu.enums.QuType;
import com.yf.exam.modules.qu.service.QuAnswerService;
import com.yf.exam.modules.qu.service.QuService;
import org.apache.commons.lang3.StringUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.HpsMeasure;
import org.docx4j.wml.Jc;
import org.docx4j.wml.JcEnumeration;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.Text;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

@Service
public class PaperWordExportServiceImpl implements PaperWordExportService {

    private static final int FONT_TITLE = 36;
    private static final int FONT_SECTION = 26;
    private static final int FONT_NORMAL = 22;

    @Autowired
    private PaperService paperService;

    @Autowired
    private PaperQuService paperQuService;

    @Autowired
    private QuService quService;

    @Autowired
    private QuAnswerService quAnswerService;

    @Override
    public void exportWord(PaperWordExportReqDTO reqDTO, HttpServletResponse response) {
        if (reqDTO == null || StringUtils.isBlank(reqDTO.getPaperId())) {
            throw new ServiceException("试卷ID不能为空！");
        }

        Paper paper = paperService.getById(reqDTO.getPaperId());
        if (paper == null) {
            throw new ServiceException("试卷不存在！");
        }

        List<PaperQuDetailDTO> quList = paperQuService.listForPaperResult(reqDTO.getPaperId());
        if (CollectionUtils.isEmpty(quList)) {
            throw new ServiceException("试卷中没有试题，无法导出！");
        }

        try {
            WordprocessingMLPackage document = WordprocessingMLPackage.createPackage();
            writePaper(document, paper.getTitle(), paper.getTotalScore(), paper.getTotalTime(), quList,
                    Boolean.TRUE.equals(reqDTO.getIncludeAnswer()),
                    Boolean.TRUE.equals(reqDTO.getIncludeAnalysis()));
            writeResponse(document, paper.getTitle(), response);
        } catch (Exception e) {
            throw new ServiceException("试卷Word导出失败：" + e.getMessage());
        }
    }

    @Override
    public void exportRandomWord(PaperRandomWordExportReqDTO reqDTO, HttpServletResponse response) {
        checkRandomRequest(reqDTO);

        List<PaperQuDetailDTO> quList = buildRandomQuestionList(reqDTO);
        if (CollectionUtils.isEmpty(quList)) {
            throw new ServiceException("没有抽取到试题，无法导出！");
        }

        try {
            WordprocessingMLPackage document = WordprocessingMLPackage.createPackage();
            String title = StringUtils.defaultIfBlank(reqDTO.getTitle(), "随机试卷");
            writePaper(document, title, calculateTotalScore(quList), reqDTO.getTotalTime(), quList,
                    Boolean.TRUE.equals(reqDTO.getIncludeAnswer()),
                    Boolean.TRUE.equals(reqDTO.getIncludeAnalysis()));
            writeResponse(document, title, response);
        } catch (Exception e) {
            throw new ServiceException("随机试卷Word导出失败：" + e.getMessage());
        }
    }

    private void checkRandomRequest(PaperRandomWordExportReqDTO reqDTO) {
        if (reqDTO == null || CollectionUtils.isEmpty(reqDTO.getRepoIds())) {
            throw new ServiceException("题库ID不能为空！");
        }

        int radioCount = safeNumber(reqDTO.getRadioCount());
        int multiCount = safeNumber(reqDTO.getMultiCount());
        int judgeCount = safeNumber(reqDTO.getJudgeCount());
        if (radioCount + multiCount + judgeCount <= 0) {
            throw new ServiceException("至少需要设置一种题型的抽题数量！");
        }
    }

    private List<PaperQuDetailDTO> buildRandomQuestionList(PaperRandomWordExportReqDTO reqDTO) {
        List<PaperQuDetailDTO> result = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        excludes.add("none");

        collectRandomQuestions(result, excludes, reqDTO.getRepoIds(), QuType.RADIO,
                safeNumber(reqDTO.getRadioCount()), safeNumber(reqDTO.getRadioScore()));
        collectRandomQuestions(result, excludes, reqDTO.getRepoIds(), QuType.MULTI,
                safeNumber(reqDTO.getMultiCount()), safeNumber(reqDTO.getMultiScore()));
        collectRandomQuestions(result, excludes, reqDTO.getRepoIds(), QuType.JUDGE,
                safeNumber(reqDTO.getJudgeCount()), safeNumber(reqDTO.getJudgeScore()));

        return result;
    }

    private void collectRandomQuestions(List<PaperQuDetailDTO> result, List<String> excludes, List<String> repoIds,
                                        Integer quType, int count, int score) {
        if (count <= 0) {
            return;
        }

        int remain = count;
        for (String repoId : repoIds) {
            if (remain <= 0) {
                break;
            }

            List<Qu> quList = quService.listByRandom(repoId, quType, excludes, remain);
            if (CollectionUtils.isEmpty(quList)) {
                continue;
            }

            for (Qu qu : quList) {
                PaperQuDetailDTO item = buildQuestion(qu, score);
                result.add(item);
                excludes.add(qu.getId());
                remain--;
                if (remain <= 0) {
                    break;
                }
            }
        }

        if (remain > 0) {
            throw new ServiceException(typeName(quType) + "数量不足，还缺少" + remain + "题！");
        }
    }

    private PaperQuDetailDTO buildQuestion(Qu qu, int score) {
        PaperQuDetailDTO item = new PaperQuDetailDTO();
        item.setQuId(qu.getId());
        item.setQuType(qu.getQuType());
        item.setContent(qu.getContent());
        item.setImage(qu.getImage());
        item.setAnalysis(qu.getAnalysis());
        item.setScore(score);
        item.setAnswerList(buildAnswerList(qu.getId()));
        return item;
    }

    private List<PaperQuAnswerExtDTO> buildAnswerList(String quId) {
        List<QuAnswer> answerList = quAnswerService.listAnswerByRandom(quId);
        List<PaperQuAnswerExtDTO> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(answerList)) {
            return result;
        }

        int index = 0;
        for (QuAnswer answer : answerList) {
            PaperQuAnswerExtDTO item = new PaperQuAnswerExtDTO();
            item.setAnswerId(answer.getId());
            item.setQuId(answer.getQuId());
            item.setIsRight(answer.getIsRight());
            item.setImage(answer.getImage());
            item.setContent(answer.getContent());
            item.setAnalysis(answer.getAnalysis());
            item.setSort(index);
            item.setAbc(abc(index));
            result.add(item);
            index++;
        }
        return result;
    }

    private int calculateTotalScore(List<PaperQuDetailDTO> quList) {
        int totalScore = 0;
        for (PaperQuDetailDTO item : quList) {
            if (item.getScore() != null) {
                totalScore += item.getScore();
            }
        }
        return totalScore;
    }

    private int safeNumber(Integer value) {
        return value == null ? 0 : value;
    }

    private String typeName(Integer quType) {
        if (QuType.RADIO.equals(quType)) {
            return "单选题";
        }
        if (QuType.MULTI.equals(quType)) {
            return "多选题";
        }
        if (QuType.JUDGE.equals(quType)) {
            return "判断题";
        }
        return "试题";
    }

    private String abc(int index) {
        if (index < 0 || index >= 26) {
            return "";
        }
        return String.valueOf((char) ('A' + index));
    }

    private void writePaper(WordprocessingMLPackage document, String title, Integer totalScore, Integer totalTime,
                            List<PaperQuDetailDTO> quList, boolean includeAnswer, boolean includeAnalysis) throws Exception {
        MainDocumentPart main = document.getMainDocumentPart();
        ObjectFactory factory = Context.getWmlObjectFactory();

        addParagraph(main, factory, StringUtils.defaultIfBlank(title, "试卷"), true, true, FONT_TITLE);
        addMetaLine(main, factory, totalScore, totalTime);
        addBlankLine(main, factory);

        List<PaperQuDetailDTO> radioList = filterByType(quList, QuType.RADIO);
        List<PaperQuDetailDTO> multiList = filterByType(quList, QuType.MULTI);
        List<PaperQuDetailDTO> judgeList = filterByType(quList, QuType.JUDGE);

        int index = 1;
        index = addQuestionSection(main, factory, "一、单选题", radioList, index);
        index = addQuestionSection(main, factory, "二、多选题", multiList, index);
        addQuestionSection(main, factory, "三、判断题", judgeList, index);

        if (includeAnswer) {
            addBlankLine(main, factory);
            addAnswerSection(main, factory, quList);
        }

        if (includeAnalysis) {
            addBlankLine(main, factory);
            addAnalysisSection(main, factory, quList);
        }
    }

    private List<PaperQuDetailDTO> filterByType(List<PaperQuDetailDTO> quList, Integer quType) {
        List<PaperQuDetailDTO> result = new ArrayList<>();
        for (PaperQuDetailDTO item : quList) {
            if (quType.equals(item.getQuType())) {
                result.add(item);
            }
        }
        return result;
    }

    private int addQuestionSection(MainDocumentPart main, ObjectFactory factory, String title,
                                   List<PaperQuDetailDTO> list, int startIndex) throws Exception {
        if (CollectionUtils.isEmpty(list)) {
            return startIndex;
        }

        int totalScore = 0;
        for (PaperQuDetailDTO item : list) {
            if (item.getScore() != null) {
                totalScore += item.getScore();
            }
        }

        addParagraph(main, factory, title + "（共" + list.size() + "题，共" + totalScore + "分）", true, false, FONT_SECTION);

        int index = startIndex;
        for (PaperQuDetailDTO qu : list) {
            String scoreText = qu.getScore() == null ? "" : "（" + qu.getScore() + "分）";
            addParagraph(main, factory, index + ". " + clean(qu.getContent()) + scoreText, true, false, FONT_NORMAL);

            if (!CollectionUtils.isEmpty(qu.getAnswerList())) {
                for (PaperQuAnswerExtDTO answer : qu.getAnswerList()) {
                    addParagraph(main, factory, "    " + clean(answer.getAbc()) + ". " + clean(answer.getContent()),
                            false, false, FONT_NORMAL);
                }
            }
            addBlankLine(main, factory);
            index++;
        }
        return index;
    }

    private void addAnswerSection(MainDocumentPart main, ObjectFactory factory, List<PaperQuDetailDTO> quList) throws Exception {
        addParagraph(main, factory, "参考答案", true, false, FONT_SECTION);
        int index = 1;
        for (PaperQuDetailDTO qu : quList) {
            addParagraph(main, factory, index + ". " + buildRightAnswer(qu), false, false, FONT_NORMAL);
            index++;
        }
    }

    private void addAnalysisSection(MainDocumentPart main, ObjectFactory factory, List<PaperQuDetailDTO> quList) throws Exception {
        addParagraph(main, factory, "答案解析", true, false, FONT_SECTION);
        int index = 1;
        for (PaperQuDetailDTO qu : quList) {
            addParagraph(main, factory, index + ". " + clean(qu.getContent()), true, false, FONT_NORMAL);

            if (StringUtils.isNotBlank(qu.getAnalysis())) {
                addParagraph(main, factory, "    整体解析：" + clean(qu.getAnalysis()), false, false, FONT_NORMAL);
            }

            if (!CollectionUtils.isEmpty(qu.getAnswerList())) {
                for (PaperQuAnswerExtDTO answer : qu.getAnswerList()) {
                    if (StringUtils.isNotBlank(answer.getAnalysis())) {
                        addParagraph(main, factory, "    " + clean(answer.getAbc()) + ". " + clean(answer.getAnalysis()),
                                false, false, FONT_NORMAL);
                    }
                }
            }
            addBlankLine(main, factory);
            index++;
        }
    }

    private String buildRightAnswer(PaperQuDetailDTO qu) {
        if (CollectionUtils.isEmpty(qu.getAnswerList())) {
            return "";
        }

        List<String> rightAnswers = new ArrayList<>();
        for (PaperQuAnswerExtDTO answer : qu.getAnswerList()) {
            if (Boolean.TRUE.equals(answer.getIsRight())) {
                rightAnswers.add(clean(answer.getAbc()));
            }
        }
        return StringUtils.join(rightAnswers, "、");
    }

    private void addMetaLine(MainDocumentPart main, ObjectFactory factory, Integer totalScore, Integer totalTime) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("姓名：__________    班级：__________    得分：__________");
        if (totalScore != null) {
            builder.append("    总分：").append(totalScore).append("分");
        }
        if (totalTime != null) {
            builder.append("    时间：").append(totalTime).append("分钟");
        }
        addParagraph(main, factory, builder.toString(), false, true, FONT_NORMAL);
    }

    private void addParagraph(MainDocumentPart main, ObjectFactory factory, String text,
                              boolean bold, boolean centered, int fontSizeHalfPoints) throws Exception {
        P paragraph = factory.createP();

        if (centered) {
            PPr pPr = factory.createPPr();
            Jc jc = factory.createJc();
            jc.setVal(JcEnumeration.CENTER);
            pPr.setJc(jc);
            paragraph.setPPr(pPr);
        }

        R run = factory.createR();
        if (bold || fontSizeHalfPoints > 0) {
            RPr rPr = factory.createRPr();
            if (bold) {
                BooleanDefaultTrue boldFlag = factory.createBooleanDefaultTrue();
                boldFlag.setVal(true);
                rPr.setB(boldFlag);
            }
            if (fontSizeHalfPoints > 0) {
                HpsMeasure size = factory.createHpsMeasure();
                size.setVal(BigInteger.valueOf(fontSizeHalfPoints));
                rPr.setSz(size);
                rPr.setSzCs(size);
            }
            run.setRPr(rPr);
        }

        Text textNode = factory.createText();
        textNode.setValue(text);
        textNode.setSpace("preserve");
        run.getContent().add(textNode);
        paragraph.getContent().add(run);
        main.addObject(paragraph);
    }

    private void addBlankLine(MainDocumentPart main, ObjectFactory factory) throws Exception {
        addParagraph(main, factory, "", false, false, 0);
    }

    private void writeResponse(WordprocessingMLPackage document, String title, HttpServletResponse response) throws Exception {
        String fileName = StringUtils.defaultIfBlank(title, "试卷") + ".docx";
        String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");

        response.resetBuffer();
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);

        ServletOutputStream outputStream = response.getOutputStream();
        try {
            document.save(outputStream);
            outputStream.flush();
        } finally {
            outputStream.close();
        }
    }

    private String clean(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        return text.replace("\r", "")
                .replace("\n", " ")
                .trim();
    }
}
