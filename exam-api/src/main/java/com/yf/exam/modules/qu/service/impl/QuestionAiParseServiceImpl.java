package com.yf.exam.modules.qu.service.impl;

import com.alibaba.fastjson.JSON;
import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.qu.config.QuestionAiProperties;
import com.yf.exam.modules.qu.service.QuestionAiParseService;
import com.yf.exam.modules.qu.dto.QuestionParseReqDTO;
import com.yf.exam.modules.qu.dto.QuestionParseRespDTO;
import com.yf.exam.modules.qu.dto.QuAnswerDTO;
import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import com.yf.exam.modules.qu.enums.QuType;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import com.yf.exam.modules.qu.dto.QuestionImportReqDTO;
import com.yf.exam.modules.qu.dto.QuestionImportRespDTO;
import com.yf.exam.modules.qu.service.QuService;
import com.yf.exam.modules.qu.support.FillProgramBlankOptionSupport;
import com.yf.exam.modules.qu.support.FillProgramBlankProcessor;
import com.yf.exam.modules.qu.support.ProgramContentFormatter;
import com.yf.exam.modules.qu.support.QuestionBoundaryHelper;
import com.yf.exam.modules.repo.service.RepoService;
import org.springframework.transaction.annotation.Transactional;
import com.yf.exam.modules.qu.dto.QuestionNormalizeTextReqDTO;
import com.yf.exam.modules.qu.dto.QuestionNormalizeTextRespDTO;
import com.yf.exam.modules.qu.support.ImportTaskProgressListener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuestionAiParseServiceImpl implements QuestionAiParseService {

    private static final int DEFAULT_NORMALIZE_BATCH_LENGTH = 10000;
    private static final int DEFAULT_PARSE_BATCH_LENGTH = 1800;
    private static final int FALLBACK_PARSE_BATCH_LENGTH = 1000;
    private static final int NORMALIZE_BATCH_QUESTION_COUNT = 20;
    private static final int PARSE_BATCH_QUESTION_COUNT = 2;
    private static final int FALLBACK_PARSE_BATCH_QUESTION_COUNT = 1;
    private static final int AI_RETRY_TIMES = 2;

    private static final Pattern CODE_START_PATTERN = Pattern.compile(
            "(?m)^\\s*(#include\\s|#import\\s|#define\\s|using\\s+\\w|void\\s+main|int\\s+main|"
                    + "public\\s+class|class\\s+\\w+|def\\s+\\w|function\\s+\\w|package\\s+\\w|"
                    + "(?:int|char|void|float|double|long|short|unsigned)\\s+\\w+\\s*\\()");
    private static final Pattern INLINE_OPTION_PATTERN = Pattern.compile(
            "(?s)([A-D])\\s*[\\.、．]\\s*(.*?)(?=(?:\\s+)?[A-D]\\s*[\\.、．]\\s*|$)");

    private static final String SYSTEM_PROMPT =
            "你是在线考试系统的试题解析器。你只能返回合法 json，不要返回 Markdown，不要解释，不要使用代码块。"
                    + "题型编码固定为：1=单选题，2=多选题，3=判断题，4=填空题，5=程序填空题，"
                    + "6=阅读程序写结果题，7=编程题，8=程序改错题，9=综合应用题。"
                    + "只能按原题型解析，不要把填空题/编程题改造成选择题。"
                    + "\n【最重要的规则】题型完全由题目所在的大题标题(section header)决定！"
                    + "大题标题如\"判断题(本大题共...)\"、\"单选题(本大题共...)\"、\"程序填空题(本大题共...)\"、"
                    + "\"程序阅读题(本大题共...)\"、\"编程题(本大题共...)\"等。"
                    + "单选题部分的题即使含完整程序代码也必须解析为单选题(1)；"
                    + "程序阅读题部分的题即使有A/B/C/D选项也必须解析为阅读程序写结果题(6)，"
                    + "answerList存全部选项(与单选题相同)，content只存题干+程序。"
                    + "绝对禁止把单选题部分的题解析为阅读程序写结果题！\n"
                    + "如果题目有 A/B/C/D 选项且在单选题部分，解析为单选(1)；"
                    + "如果题目在程序阅读题/阅读程序部分，即使有A/B/C/D选项也解析为阅读程序写结果题(6)，"
                    + "answerList存全部选项(与单选题相同)，content只存题干+程序。\n"
                    + "如果题目要求判断正误，解析为判断题。"
                    + "如果题目含“请填空”且无大段程序骨架，解析为填空题(4)。"
                    + "如果题目含函数/程序代码骨架且代码内部有空位，优先解析为程序填空题(5)，"
                    + "即使题干未写“请填空”；不要把程序填空题改造成选择题、普通填空题或编程题。"
                    + "如果题目要求“阅读程序写结果”或给出完整程序要求写输出，解析为阅读程序写结果题(6)；"
                    + "content=题干+完整程序(无空位)，有选项时answerList存全部选项，无选项时answerList=运行结果，不要把程序放进 answerList。"
                    + "如果题目要求“编写程序”且只给需求无现成代码骨架，解析为编程题(7)；"
                    + "content=仅题干，answerList[0]=完整参考程序，禁止把程序写进 content。"
                    + "如果题目要求“改错/改正/找出错误”，解析为程序改错题(8)；"
                    + "content=题干+有错程序，answerList=改正后程序。"
                    + "如果题目属于综合应用，解析为综合应用题(9)。"
                    + "主观题(4-9)的 answerList 存参考答案或评分要点，isRight=true。"
                    + "普通填空题(4)：无大段代码骨架，content=题干，answerList=各空文字答案。"
                    + "程序填空题(5)：content=题干说明+程序代码全文(代码中空位必须用____表示，禁止把答案写进代码)，"
                    + "answerList=每个空的参考答案(按出现顺序)，不要把完整程序放进 answerList。"
                    + "程序填空选择题：代码后的(3)(4)(5)是空位编号不是题号，其后A/B/C/D是该空候选项不是新题；"
                    + "仍按程序填空题解析，answerList只存各空正确答案，候选项写入各空optionList(与单选题展示相同)。"
                    + "阅读程序写结果题(6)：content=题干+完整程序(无空位，不含A/B/C/D选项)；"
                    + "若原题有A/B/C/D选项，answerList存全部选项(与单选题相同)，仅一个isRight=true，"
                    + "content中不要重复放选项。"
                    + "编程题(7)：content=仅题干，answerList[0]=完整参考程序，禁止把程序写进 content。"
                    + "程序改错题(8)：content=题干+有错程序，answerList=改正后程序；关键词“改错/改正/错误”。"
                    + "程序代码必须保留换行与缩进，JSON 中用 \\n 表示换行。"
                    + "返回格式必须是："
                    + "{\"questions\":[{\"quType\":1,\"level\":1,\"content\":\"题干\","
                    + "\"analysis\":\"整体解析，说明本题考查点和正确答案依据，不能为空\",\"image\":\"\",\"remark\":\"\","
                    + "\"repoIds\":[],\"answerList\":[{\"content\":\"选项或参考答案内容\","
                    + "\"isRight\":true,\"image\":\"\",\"analysis\":\"答案解析，说明依据，不能为空\","
                    + "\"optionList\":[{\"letter\":\"A\",\"content\":\"&r\",\"isRight\":true,\"analysis\":\"...\"}]}]}";
    private static final String NORMALIZE_SYSTEM_PROMPT =
            "你是试题文本清洗助手，负责把从 Word、PDF、OCR 或复制粘贴中抽取出的混乱试题文本整理为规范纯文本。"
                    + "要求："
                    + "1. 只整理格式，不解答试题，不改变原答案。"
                    + "2. 不新增题目、选项、答案、解析。"
                    + "3. 合并被 Word 排版拆散的题干、选项、答案。"
                    + "4. 删除无意义空行、制表符、多余空格。"
                    + "5. 题号统一为：1. 题干。"
                    + "6. 选项统一为：A. 选项内容。"
                    + "7. 答案统一为：答案：A 或 答案：ABD。"
                    + "8. 如果选项末尾出现与选项顺序一致的排版残留数字，例如 A. 编程语言1、B. 数据库2、C. 操作系统3、D. 浏览器4，删除这些尾部数字。"
                    + "9. 如果数字属于选项含义，例如 Java 8、HTTP 404、2的倍数，必须保留。"
                    + "10. 只返回合法 JSON，不要返回 Markdown，不要解释。"
                    + "11. 程序填空题若出现(3)(4)(5)空位编号及后续A/B/C/D候选项，属于同一道题，不要拆成多道题。"
                    + "12. 不要把上一题尾部选项(如D. area)与下一题题干(如下面程序的功能...)粘在同一题；新题题干前应换行。"
                    + "返回格式必须是：{\"normalizedText\":\"整理后的试题文本\"}";

    @Autowired
    private QuestionAiProperties properties;

    @Autowired
    @Qualifier("asyncExecutor")
    private ThreadPoolTaskExecutor asyncExecutor;

    @Autowired
    private FillProgramBlankProcessor fillProgramBlankProcessor;

    @Autowired
    private ProgramContentFormatter programContentFormatter;

    private volatile ChatLanguageModel chatModel;

    @Override
    public QuestionNormalizeTextRespDTO normalizeText(QuestionNormalizeTextReqDTO reqDTO) {
        return normalizeText(reqDTO, null);
    }

    @Override
    public QuestionNormalizeTextRespDTO normalizeText(QuestionNormalizeTextReqDTO reqDTO,
            ImportTaskProgressListener listener) {
        if (reqDTO == null || StringUtils.isBlank(reqDTO.getText())) {
            throw new ServiceException("待清洗文本不能为空");
        }

        List<String> chunks = splitQuestionText(reqDTO.getText(), normalizeBatchLength(),
                normalizeBatchQuestionCount());
        if (chunks.size() > 1) {
            String normalizedText = normalizeTextParallel(chunks, listener);
            if (normalizedText.length() == 0) {
                throw new ServiceException("AI未返回清洗后的文本");
            }

            QuestionNormalizeTextRespDTO respDTO = new QuestionNormalizeTextRespDTO();
            respDTO.setRawText(reqDTO.getText());
            respDTO.setNormalizedText(normalizedText);
            return respDTO;
        }

        if (listener != null) {
            listener.onBatchProgress(0, 1, "正在AI清洗文本");
        }
        QuestionNormalizeTextRespDTO respDTO = normalizeTextWithRetry(reqDTO.getText(), "1");
        if (listener != null) {
            listener.onBatchProgress(1, 1, "AI清洗完成");
        }
        respDTO.setRawText(reqDTO.getText());
        return respDTO;
    }

    private String normalizeTextParallel(List<String> chunks, ImportTaskProgressListener listener) {
        int total = chunks.size();
        int concurrency = Math.min(normalizeConcurrency(), total);
        String[] results = new String[total];
        AtomicInteger nextIndex = new AtomicInteger(0);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicReference<ServiceException> firstError = new AtomicReference<>();

        if (listener != null) {
            listener.onBatchProgress(0, total,
                    "正在AI清洗文本（共" + total + "批，" + concurrency + "路并发）");
        }

        List<CompletableFuture<Void>> workers = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            workers.add(CompletableFuture.runAsync(() -> {
                while (!failed.get()) {
                    int index = nextIndex.getAndIncrement();
                    if (index >= total) {
                        return;
                    }
                    try {
                        QuestionNormalizeTextRespDTO part = normalizeTextWithRetry(chunks.get(index),
                                String.valueOf(index + 1));
                        if (StringUtils.isNotBlank(part.getNormalizedText())) {
                            results[index] = part.getNormalizedText().trim();
                        }
                        int done = completed.incrementAndGet();
                        if (listener != null) {
                            listener.onBatchProgress(done, total,
                                    "已完成AI清洗 " + done + "/" + total + " 批");
                        }
                    } catch (ServiceException e) {
                        failed.set(true);
                        firstError.compareAndSet(null, e);
                        return;
                    }
                }
            }, asyncExecutor));
        }

        CompletableFuture.allOf(workers.toArray(new CompletableFuture[0])).join();

        ServiceException error = firstError.get();
        if (error != null) {
            throw error;
        }

        StringBuilder normalizedText = new StringBuilder();
        for (String part : results) {
            if (StringUtils.isNotBlank(part)) {
                if (normalizedText.length() > 0) {
                    normalizedText.append("\n\n");
                }
                normalizedText.append(part);
            }
        }
        return normalizedText.toString();
    }

    private int normalizeBatchLength() {
        Integer configured = properties.getNormalizeBatchLength();
        if (configured != null && configured > 0) {
            return configured;
        }
        return DEFAULT_NORMALIZE_BATCH_LENGTH;
    }

    private int normalizeBatchQuestionCount() {
        Integer configured = properties.getNormalizeBatchQuestionCount();
        if (configured == null || configured <= 0) {
            return NORMALIZE_BATCH_QUESTION_COUNT;
        }
        return configured;
    }

    private int normalizeConcurrency() {
        Integer configured = properties.getNormalizeConcurrency();
        if (configured == null || configured <= 0) {
            return 3;
        }
        return Math.min(configured, 8);
    }

    private QuestionNormalizeTextRespDTO normalizeTextWithRetry(String text, String batchNo) {
        ServiceException lastException = null;
        for (int i = 1; i <= AI_RETRY_TIMES; i++) {
            try {
                return normalizeTextOnce(text);
            } catch (ServiceException e) {
                lastException = e;
            }
        }
        throw new ServiceException("第" + batchNo + "批AI清洗失败：" + lastException.getMessage());
    }

    private QuestionNormalizeTextRespDTO normalizeTextOnce(String text) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(NORMALIZE_SYSTEM_PROMPT));
        messages.add(UserMessage.from("待清洗试题文本：\n" + text));

        Response<AiMessage> response = getChatModel().generate(messages);
        String answer = readAiText(response);
        String json = extractJson(answer);

        QuestionNormalizeTextRespDTO respDTO;
        try {
            respDTO = JSON.parseObject(json, QuestionNormalizeTextRespDTO.class);
        } catch (Exception e) {
            throw new ServiceException("AI清洗返回内容不是合法JSON：" + answer);
        }

        if (respDTO == null || StringUtils.isBlank(respDTO.getNormalizedText())) {
            throw new ServiceException("AI未返回清洗后的文本");
        }

        respDTO.setRawText(text);
        return respDTO;
    }

    @Override
    public List<String> splitParseBatches(String text) {
        if (StringUtils.isBlank(text)) {
            return new ArrayList<>();
        }
        return splitQuestionText(text, maxBatchLength(DEFAULT_PARSE_BATCH_LENGTH), PARSE_BATCH_QUESTION_COUNT);
    }

    @Override
    public QuestionParseRespDTO parseSingleBatch(QuestionParseReqDTO reqDTO, String batchNo) {
        checkRequest(reqDTO);
        return parseQuestionsBatch(reqDTO, reqDTO.getText(), batchNo);
    }

    @Override
    public String normalizeSingleBatch(String text, String batchNo) {
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("待清洗文本不能为空");
        }
        return normalizeTextWithRetry(text, batchNo).getNormalizedText();
    }

    @Override
    public QuestionParseRespDTO parseQuestions(QuestionParseReqDTO reqDTO) {
        return parseQuestions(reqDTO, null);
    }

    @Override
    public QuestionParseRespDTO parseQuestions(QuestionParseReqDTO reqDTO, ImportTaskProgressListener listener) {
        checkRequest(reqDTO);

        List<String> chunks = splitQuestionText(reqDTO.getText(), maxBatchLength(DEFAULT_PARSE_BATCH_LENGTH),
                PARSE_BATCH_QUESTION_COUNT);
        if (chunks.size() > 1) {
            QuestionParseRespDTO result = new QuestionParseRespDTO();
            List<QuDetailDTO> questions = new ArrayList<>();
            List<String> rawJsonList = new ArrayList<>();

            int batchIndex = 1;
            for (String chunk : chunks) {
                if (listener != null) {
                    listener.onBatchProgress(batchIndex - 1, chunks.size(),
                            "正在AI解析第" + batchIndex + "批试题（共" + chunks.size() + "批）");
                }
                QuestionParseRespDTO batchResp = parseQuestionsBatch(reqDTO, chunk, String.valueOf(batchIndex));
                questions.addAll(batchResp.getQuestions());
                rawJsonList.add(batchResp.getRawJson());
                if (listener != null) {
                    listener.onBatchProgress(batchIndex, chunks.size(),
                            "已完成AI解析第" + batchIndex + "批试题（共" + chunks.size() + "批）");
                }
                batchIndex++;
            }

            if (CollectionUtils.isEmpty(questions)) {
                throw new ServiceException("AI未解析出任何试题");
            }

            result.setQuestions(questions);
            result.setCount(questions.size());
            result.setRawJson("{\"batches\":" + JSON.toJSONString(rawJsonList) + "}");
            return result;
        }

        if (listener != null) {
            listener.onBatchProgress(0, 1, "正在AI解析试题");
        }
        QuestionParseRespDTO result = parseQuestionsBatch(reqDTO, reqDTO.getText(), "1");
        if (listener != null) {
            listener.onBatchProgress(1, 1, "AI解析完成");
        }
        return result;
    }

    private static final Pattern QUESTION_NUMBER_LINE = Pattern.compile("(?m)^\\s*(\\d+)[\\.．、]\\s+");

    private QuestionParseRespDTO parseQuestionsBatch(QuestionParseReqDTO sourceReq, String chunk, String batchNo) {
        try {
            QuestionParseRespDTO resp = parseQuestionsWithRetry(copyParseReq(sourceReq, chunk), batchNo);
            // Safety check: if AI returned fewer questions than the number of question-numbered
            // lines in the input, the response was likely truncated — trigger fallback split.
            // Count lines starting with "N." (not section headers or instruction lines).
            int expectedQuestions = 0;
            Matcher qMatcher = QUESTION_NUMBER_LINE.matcher(chunk);
            while (qMatcher.find()) {
                // Skip instruction lines like "1.应将全部答案写在答卷纸"
                String after = chunk.substring(qMatcher.start());
                String lineEnd = after.contains("\n") ? after.substring(0, after.indexOf("\n")) : after;
                if (!lineEnd.contains("应将全部答案") && !lineEnd.contains("编程题应写明")
                        && !lineEnd.contains("考试完成后") && !lineEnd.contains("不要另添")) {
                    expectedQuestions++;
                }
            }
            if (expectedQuestions > 1 && resp.getQuestions().size() < expectedQuestions) {
                throw new ServiceException("第" + batchNo + "批AI仅解析出" + resp.getQuestions().size()
                        + "题，但输入含" + expectedQuestions + "题，可能截断");
            }
            return resp;
        } catch (ServiceException e) {
            List<String> smallerChunks = splitQuestionText(chunk, FALLBACK_PARSE_BATCH_LENGTH,
                    FALLBACK_PARSE_BATCH_QUESTION_COUNT);
            if (smallerChunks.size() <= 1) {
                throw new ServiceException("第" + batchNo + "批AI解析失败：" + e.getMessage());
            }

            QuestionParseRespDTO result = new QuestionParseRespDTO();
            List<QuDetailDTO> questions = new ArrayList<>();
            List<String> rawJsonList = new ArrayList<>();
            int subIndex = 1;
            for (String smallerChunk : smallerChunks) {
                QuestionParseRespDTO part = parseQuestionsWithRetry(copyParseReq(sourceReq, smallerChunk),
                        batchNo + "." + subIndex);
                questions.addAll(part.getQuestions());
                rawJsonList.add(part.getRawJson());
                subIndex++;
            }

            if (CollectionUtils.isEmpty(questions)) {
                throw new ServiceException("第" + batchNo + "批AI未解析出任何试题");
            }

            result.setQuestions(questions);
            result.setCount(questions.size());
            result.setRawJson("{\"batches\":" + JSON.toJSONString(rawJsonList) + "}");
            return result;
        }
    }

    private QuestionParseRespDTO parseQuestionsWithRetry(QuestionParseReqDTO reqDTO, String batchNo) {
        ServiceException lastException = null;
        for (int i = 1; i <= AI_RETRY_TIMES; i++) {
            try {
                return parseQuestionsOnce(reqDTO);
            } catch (ServiceException e) {
                lastException = e;
            }
        }
        throw new ServiceException("第" + batchNo + "批AI解析失败：" + lastException.getMessage());
    }

    private QuestionParseRespDTO parseQuestionsOnce(QuestionParseReqDTO reqDTO) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));
        messages.add(SystemMessage.from(
                "解析题目时必须同时生成解析字段。"
                        + "每道题的analysis字段填写整体解析，说明本题考查点、正确答案依据。"
                        + "每个answerList元素的analysis字段填写该选项的答案解析，说明该选项为什么正确或错误。"
                        + "如果原文已经包含解析，优先保留并整理原解析；如果原文没有解析，可以基于题干、选项和答案生成简洁解析。"
                        + "不要改变题干、选项和正确答案，不要新增题目。"
        ));
        messages.add(UserMessage.from(buildUserPrompt(reqDTO)));

        Response<AiMessage> response = getChatModel().generate(messages);
        String answer = readAiText(response);
        String json = extractJson(answer);

        QuestionParseRespDTO respDTO;
        try {
            respDTO = JSON.parseObject(json, QuestionParseRespDTO.class);
        } catch (Exception e) {
            throw new ServiceException("AI返回内容不是合法JSON：" + answer);
        }

        normalizeAndCheck(respDTO, reqDTO);
        respDTO.setRawJson(json);
        respDTO.setCount(respDTO.getQuestions().size());
        return respDTO;
    }

    private ChatLanguageModel getChatModel() {
        if (chatModel == null) {
            synchronized (this) {
                if (chatModel == null) {
                    if (StringUtils.isBlank(properties.getApiKey())) {
                        throw new ServiceException("未配置 DEEPSEEK_API_KEY");
                    }

                    chatModel = OpenAiChatModel.builder()
                            .baseUrl(properties.getBaseUrl())
                            .apiKey(properties.getApiKey())
                            .modelName(properties.getModelName())
                            .temperature(0.1)
                            .maxTokens(8000)
                            .responseFormat("json_object")
                            .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                            .maxRetries(1)
                            .build();
                }
            }
        }
        return chatModel;
    }

    private void checkRequest(QuestionParseReqDTO reqDTO) {
        if (reqDTO == null || StringUtils.isBlank(reqDTO.getText())) {
            throw new ServiceException("待解析文本不能为空");
        }
    }

    private QuestionParseReqDTO copyParseReq(QuestionParseReqDTO source, String text) {
        QuestionParseReqDTO reqDTO = new QuestionParseReqDTO();
        reqDTO.setText(text);
        reqDTO.setRepoIds(source.getRepoIds());
        reqDTO.setLevel(source.getLevel());
        return reqDTO;
    }

    private int maxBatchLength(int defaultValue) {
        Integer configured = properties.getMaxTextLength();
        if (configured == null || configured <= 0) {
            return defaultValue;
        }
        return Math.min(configured, defaultValue);
    }

    /** 匹配大题标题行，如"二、单选题（本大题共20小题，每小题1分，共20分）" */
    private static final Pattern SECTION_HEADER_LINE = Pattern.compile(
            "(?m)^\\s*(?:得分\\s*)?(?:[一二三四五六七八九十]+、)?\\s*"
                    + "(?:判断题|单选题|多选题|填空题|程序填空题|程序阅读题|阅读程序写结果题|"
                    + "程序设计题|编程题|程序改错题|综合应用题)[^\\n]*本大题共[^\\n]*");

    private List<String> splitQuestionText(String text, int maxLength, int maxQuestionCount) {
        List<String> blocks = splitQuestionBlocks(text);
        if (blocks.size() <= 1) {
            return splitByLength(text, maxLength);
        }

        // Extract section headers with their positions from the original text,
        // so we can propagate them into each new chunk for AI context.
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        List<String> sectionHeaders = new ArrayList<>();
        List<Integer> sectionPositions = new ArrayList<>();
        Matcher hm = SECTION_HEADER_LINE.matcher(normalized);
        while (hm.find()) {
            String header = hm.group().trim().replaceFirst("^(?:得分\\s*)", "");
            sectionHeaders.add(header);
            sectionPositions.add(hm.start());
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int questionCount = 0;
        String currentSectionHeader = "";
        String chunkSectionHeader = "";
        int nextSectionIdx = 0;

        for (String block : blocks) {
            if (StringUtils.isBlank(block)) {
                continue;
            }

            String value = block.trim();

            // If this block IS a section header (not filtered by splitQuestionBlocks),
            // capture it and skip — don't treat it as a question.
            if (SECTION_HEADER_LINE.matcher(value).find() && value.length() < 100
                    && !value.matches("(?s).*\\d+[\\.．、]\\s*.*")) {
                currentSectionHeader = value.replaceFirst("^(?:得分\\s*)", "");
                continue;
            }

            // Determine which section this block belongs to by finding its
            // approximate position in the original text and comparing with
            // section header positions.
            if (!sectionHeaders.isEmpty() && nextSectionIdx < sectionHeaders.size()) {
                int blockPos = findBlockPosition(normalized, value);
                if (blockPos >= 0) {
                    while (nextSectionIdx < sectionPositions.size()
                            && blockPos >= sectionPositions.get(nextSectionIdx)) {
                        currentSectionHeader = sectionHeaders.get(nextSectionIdx);
                        nextSectionIdx++;
                    }
                }
            }

            // Flush when section boundary changes to avoid mixing question types
            boolean sectionChanged = current.length() > 0 && questionCount > 0
                    && !currentSectionHeader.equals(chunkSectionHeader);
            boolean shouldFlush = current.length() > 0
                    && (sectionChanged
                        || current.length() + value.length() + 2 > maxLength
                        || questionCount >= maxQuestionCount);
            if (shouldFlush) {
                chunks.add(current.toString().trim());
                current.setLength(0);
                questionCount = 0;
                // Prepend section header to new chunk so AI knows the question type context
                chunkSectionHeader = currentSectionHeader;
                if (!chunkSectionHeader.isEmpty()) {
                    current.append(chunkSectionHeader).append("\n\n");
                }
            }

            if (value.length() > maxLength) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                    questionCount = 0;
                }
                chunks.addAll(splitByLength(value, maxLength));
                continue;
            }

            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(value);
            questionCount++;
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    /**
     * Find the approximate position of a block in the original text.
     * Uses progressively shorter prefixes for robustness.
     */
    private int findBlockPosition(String text, String block) {
        for (int len = Math.min(block.length(), 60); len >= 15; len -= 10) {
            String key = block.substring(0, len);
            int pos = text.indexOf(key);
            if (pos >= 0) {
                return pos;
            }
        }
        return -1;
    }

    private List<String> splitQuestionBlocks(String text) {
        return QuestionBoundaryHelper.splitQuestionBlocks(text);
    }

    private List<String> splitByLength(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n").trim();
        if (StringUtils.isBlank(normalized)) {
            return chunks;
        }

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + maxLength, normalized.length());
            if (end < normalized.length()) {
                int newline = normalized.lastIndexOf("\n", end);
                if (newline > start + maxLength / 2) {
                    end = newline;
                }
            }

            String chunk = normalized.substring(start, end).trim();
            if (StringUtils.isNotBlank(chunk)) {
                chunks.add(chunk);
            }
            start = end;
        }
        return chunks;
    }

    private String buildUserPrompt(QuestionParseReqDTO reqDTO) {
        return "请把下面的试题文本解析为在线考试系统可导入的 json。\n"
                + "要求：\n"
                + "1. 只能按原题型解析，不要把填空题/编程题改造成选择题。\n"
                + "2. 单选题 quType=1，只能有一个正确答案。\n"
                + "3. 多选题 quType=2，至少有一个正确答案。\n"
                + "4. 判断题 quType=3，选项固定为：正确、错误。\n"
                + "5. 普通填空题 quType=4：无大段程序骨架，content=题干，answerList=各空答案。\n"
                + "6. 程序填空题 quType=5（重点）：\n"
                + "   - 识别特征：题干说明函数/程序功能 + C 代码骨架，代码内部有空位(多空格、下划线、{FILL:答案}、____)。\n"
                + "   - 必须识别代码中全部空位，answerList 数量必须等于代码中 ____ 数量，不允许只输出一个空。\n"
                + "   - content=题干文字 + \\n\\n + 程序代码；代码中的每个空位必须替换为 ____，禁止保留答案表达式。\n"
                + "   - 例如 if( ____ ) 而不是 if( max<a[row][col] )；answerList 按顺序存各空答案。\n"
                + "   - 源文档若出现 {FILL:max<a[row][col]} 表示该处是第1空；多个 {FILL:...} 按顺序对应第1空、第2空...\n"
                + "   - 若原文第二空为 min<max 但按题意应为 min>max，answerList 可写 min>max，并在 analysis 说明原文可能为 min<max。\n"
                + "   - answerList 只存各空应填片段，不要把完整程序放进 answerList。\n"
                + "   - 如果参考答案中同一空出现多种等价答案，例如\"a[num] / a[num]!='\\0'\"，"
                + "必须保留在同一个 answerList 元素的 content 中，不要拆成多个答案项。\n"
                + "   - 程序填空选择题：代码后的(3)(4)(5)或（3）（4）（5）是空位编号，不是新题号；"
                + "紧随其后的A/B/C/D是该空的候选选项，不是整道选择题，不要拆成新题。\n"
                + "   - 此类题仍解析为程序填空题 quType=5，answerList 只存每个空的正确答案；"
                + "若原文提供A/B/C/D候选项，必须写入各空 optionList(展示与单选题相同)，content 不含选项文本。\n"
                + "   - 程序填空题结束后，下一道新题(如「以下函数把b字符串...」)必须单独成题，不要与上一道合并。\n"
                + "   - 与 quType=6(完整程序无空位)、quType=7(只给需求无骨架)、quType=8(改错)区分。\n"
                + "7. 阅读程序写结果题 quType=6（重点）：\n"
                + "   - 识别特征：给出完整程序(无空位)，要求写出运行结果/输出/程序功能分析。\n"
                + "   - 关键词：阅读程序、写出结果、程序运行后、输出结果。\n"
                + "   - content=题干 + \\n\\n + 完整程序代码(不含A/B/C/D选项)。\n"
                + "   - 如果原始文本包含A/B/C/D选项，answerList存全部选项(与单选题相同)，"
                + "仅一个isRight=true，每个选项的analysis不能为空；不要把选项写进 content。\n"
                + "   - 不要把完整程序放进 answerList；与 quType=5(有空位)、quType=8(改错)区分。\n"
                + "8. 编程题 quType=7（重点）：\n"
                + "   - 识别特征：只给题目需求/功能描述，要求学生编写完整程序，无现成代码骨架。\n"
                + "   - 关键词：编写程序、写程序、编程实现。\n"
                + "   - content=仅题干文字，禁止把程序写进 content；answerList[0]=完整参考程序。\n"
                + "9. 程序改错题 quType=8（重点）：\n"
                + "   - 识别特征：给出有错程序，要求找出错误并改正。\n"
                + "   - 关键词：改错、改正、错误、请修改。\n"
                + "   - content=题干 + \\n\\n + 有错程序代码；answerList=改正后完整程序。\n"
                + "   - 不要把改正后程序写进 content；与 quType=5(填空)、quType=6(阅读)区分。\n"
                + "10. 综合应用题 quType=9：content=题干，answerList=参考答案。\n"
                + "11. 每道题必须包含 content、quType。\n"
                + "12. repoIds 使用：" + JSON.toJSONString(reqDTO.getRepoIds()) + "。\n"
                + "13. level 使用：" + (reqDTO.getLevel() == null ? 1 : reqDTO.getLevel()) + "。\n"
                + "14. 必须生成整体解析：每道题的 analysis 不能为空。\n"
                + "15. 客观题 answerList 中每个选项的 analysis 不能为空。\n"
                + "16. 程序代码保留换行，JSON 中用 \\n 表示换行。必须原样保留C代码中的每一个字符，"
                + "不得修改、替换或省略任何符号，特别是三目运算符?和:、比较运算符>=/<=/!=/==、"
                + "逻辑运算符&&/||/!、位运算符&/|/^、指针*和->、取地址&等。\n"
                + "17. 不要编造题目，无法识别的题目不要输出。\n"
                + "18. 上一题选项(如D. area)与下一题题干(如下面程序的功能...)属于不同题目，不要合并。\n\n"
                + "原始文本：\n"
                + reqDTO.getText();
    }

    private String readAiText(Response<AiMessage> response) {
        if (response == null || response.content() == null || StringUtils.isBlank(response.content().text())) {
            throw new ServiceException("AI返回内容为空");
        }
        return response.content().text();
    }

    private String extractJson(String text) {
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("AI返回内容为空");
        }

        String value = text.trim()
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int start = value.indexOf("{");
        int end = value.lastIndexOf("}");

        if (start < 0 || end <= start) {
            throw new ServiceException("AI返回内容中没有找到JSON：" + text);
        }

        return value.substring(start, end + 1);
    }

    private void normalizeAndCheck(QuestionParseRespDTO respDTO, QuestionParseReqDTO reqDTO) {
        if (respDTO == null || CollectionUtils.isEmpty(respDTO.getQuestions())) {
            throw new ServiceException("AI未解析出任何试题");
        }

        int index = 1;
        for (QuDetailDTO qu : respDTO.getQuestions()) {
            if (StringUtils.isNotBlank(qu.getContent())) {
                qu.setContent(QuestionBoundaryHelper.stripInlineFilledAnswer(qu.getContent()));
            }
            if (qu.getLevel() == null) {
                qu.setLevel(reqDTO.getLevel() == null ? 1 : reqDTO.getLevel());
            }
            if (StringUtils.isBlank(qu.getImage())) {
                qu.setImage("");
            }
            if (StringUtils.isBlank(qu.getRemark())) {
                qu.setRemark("");
            }
            if (StringUtils.isBlank(qu.getAnalysis())) {
                qu.setAnalysis(buildQuestionAnalysis(qu));
            }
            if (CollectionUtils.isEmpty(qu.getRepoIds())) {
                qu.setRepoIds(reqDTO.getRepoIds());
            }

            normalizeObjectiveChoiceQuestion(qu, reqDTO.getText(), index);
            normalizeProgramChoiceQuestion(qu, reqDTO.getText(), index);
            normalizeFillProgramQuestion(qu, reqDTO.getText(), index);
            normalizeReadProgramQuestion(qu);
            normalizeFixProgramQuestion(qu);
            splitStemAndReference(qu);
            sanitizeVisibleFillMarkers(qu);
            checkQuestion(qu, index);
            index++;
        }
    }

    /**
     * 程序填空题：content 存题干+代码骨架，answerList 只存各空参考答案
     */
    private void normalizeFillProgramQuestion(QuDetailDTO qu, String sourceText, int questionIndex) {
        if (!QuType.FILL_PROGRAM.equals(qu.getQuType())) {
            return;
        }

        String content = StringUtils.defaultString(qu.getContent()).trim();
        List<QuAnswerDTO> originalAnswers = qu.getAnswerList() == null
                ? new ArrayList<>()
                : new ArrayList<>(qu.getAnswerList());

        Map<Integer, Map<String, String>> blankOptions = FillProgramBlankOptionSupport
                .extractOptionsForQuestion(qu, content, sourceText, questionIndex);
        content = FillProgramBlankOptionSupport.stripFillBlankOptionSection(content);

        List<String> aiAnswers = collectAiBlankAnswers(qu.getAnswerList());
        String programFromAnswer = null;

        if (!CollectionUtils.isEmpty(qu.getAnswerList())) {
            for (QuAnswerDTO answer : qu.getAnswerList()) {
                if (answer == null || StringUtils.isBlank(answer.getContent())) {
                    continue;
                }
                String answerText = answer.getContent().trim();
                if (looksLikeProgramSkeleton(answerText)) {
                    if (programFromAnswer == null) {
                        programFromAnswer = answerText;
                    }
                }
            }
        }

        if (programFromAnswer != null && !containsCodeBlock(content)) {
            content = StringUtils.isBlank(content)
                    ? programFromAnswer
                    : content + "\n\n" + programFromAnswer;
        }

        int codeStart = findCodeBlockStart(content);
        String stem = content;
        String code = "";
        if (codeStart > 0) {
            stem = content.substring(0, codeStart).trim();
            code = content.substring(codeStart).trim();
        } else if (containsCodeBlock(content)) {
            code = content;
            stem = "";
        }
        if (StringUtils.isBlank(code) && StringUtils.isNotBlank(programFromAnswer)) {
            code = programFromAnswer;
        }

        FillProgramBlankProcessor.ProcessResult processed = fillProgramBlankProcessor.process(code, aiAnswers);
        code = programContentFormatter.formatProgramCode(processed.getCode());
        content = programContentFormatter.mergeStemAndCode(stem, code);

        List<QuAnswerDTO> blankAnswers = buildBlankAnswerList(processed.getBlanks());
        FillProgramBlankOptionSupport.resolveFillBlankLetterAnswers(blankAnswers, blankOptions);
        FillProgramBlankOptionSupport.propagateSharedBlankOptions(blankAnswers, blankOptions);
        FillProgramBlankOptionSupport.preserveAiOptionLists(blankAnswers, originalAnswers);
        FillProgramBlankOptionSupport.attachFillBlankOptions(blankAnswers, blankOptions);
        FillProgramBlankOptionSupport.fillOptionAnalysis(blankAnswers);
        appendBlankNotesToAnalysis(qu, processed.getBlanks());

        qu.setContent(content);
        qu.setAnswerList(blankAnswers);
        FillProgramBlankOptionSupport.encodeToRemark(qu);
    }

    private List<String> collectAiBlankAnswers(List<QuAnswerDTO> answers) {
        List<String> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(answers)) {
            return result;
        }
        for (QuAnswerDTO answer : answers) {
            if (answer == null || StringUtils.isBlank(answer.getContent())) {
                continue;
            }
            String text = answer.getContent().trim();
            if (looksLikeProgramSkeleton(text)) {
                continue;
            }
            result.add(text);
        }
        return result;
    }

    private List<QuAnswerDTO> buildBlankAnswerList(List<FillProgramBlankProcessor.BlankSlot> blanks) {
        List<QuAnswerDTO> result = new ArrayList<>();
        if (blanks == null) {
            return result;
        }
        for (FillProgramBlankProcessor.BlankSlot slot : blanks) {
            if (slot == null || StringUtils.isBlank(slot.getContent())) {
                continue;
            }
            QuAnswerDTO answer = new QuAnswerDTO();
            answer.setContent(slot.getContent());
            answer.setIsRight(true);
            answer.setImage("");
            if (StringUtils.isNotBlank(slot.getNote())) {
                answer.setAnalysis(slot.getNote());
            }
            result.add(answer);
        }
        return result;
    }

    private void appendBlankNotesToAnalysis(QuDetailDTO qu, List<FillProgramBlankProcessor.BlankSlot> blanks) {
        if (blanks == null || blanks.isEmpty()) {
            return;
        }
        StringBuilder notes = new StringBuilder();
        int index = 1;
        for (FillProgramBlankProcessor.BlankSlot slot : blanks) {
            if (slot != null && StringUtils.isNotBlank(slot.getNote())) {
                if (notes.length() > 0) {
                    notes.append(" ");
                }
                notes.append("第").append(index).append("空：").append(slot.getNote()).append("。");
            }
            index++;
        }
        if (notes.length() == 0) {
            return;
        }
        String analysis = StringUtils.defaultString(qu.getAnalysis()).trim();
        if (StringUtils.isBlank(analysis)) {
            qu.setAnalysis(notes.toString());
        } else if (!analysis.contains(notes.toString())) {
            qu.setAnalysis(analysis + " " + notes);
        }
    }


    /**
     * 阅读程序题：content 存题干+完整程序，answerList 只存运行结果
     */
    private void normalizeObjectiveChoiceQuestion(QuDetailDTO qu, String sourceText, int questionIndex) {
        if (!QuType.RADIO.equals(qu.getQuType()) && !QuType.MULTI.equals(qu.getQuType())) {
            return;
        }

        ProgramChoiceOptions options = extractObjectiveChoiceOptions(qu.getContent());
        String sourceBlock = "";
        if (options == null || countNonBlankOptions(options.answers) <= countNonBlankAnswers(qu.getAnswerList())) {
            sourceBlock = findSourceQuestionBlockByAnswer(sourceText, qu.getAnswerList());
            if (StringUtils.isBlank(sourceBlock)) {
                sourceBlock = findSourceQuestionBlock(sourceText, questionIndex, qu.getContent());
            }
            ProgramChoiceOptions sourceOptions = extractObjectiveChoiceOptions(sourceBlock);
            if (sourceOptions != null && isBetterOptionSet(sourceOptions, options)) {
                options = sourceOptions;
            }
        }
        if (options == null || countNonBlankOptions(options.answers) < 2) {
            enrichAnswerListFromSource(qu, sourceText, questionIndex);
            return;
        }

        List<Integer> rightIndexes = findRightOptionIndexes(options.answers, qu.getAnswerList());
        if (rightIndexes.isEmpty()) {
            rightIndexes = findAnswerLetterIndexesInText(qu.getContent(), options.answers.size());
        }
        if (rightIndexes.isEmpty()) {
            rightIndexes = findAnswerLetterIndexesInText(sourceBlock, options.answers.size());
        }
        if (rightIndexes.isEmpty()) {
            return;
        }
        if (QuType.RADIO.equals(qu.getQuType()) && rightIndexes.size() != 1) {
            return;
        }

        List<QuAnswerDTO> answerList = new ArrayList<>();
        for (int i = 0; i < options.answers.size(); i++) {
            QuAnswerDTO answer = new QuAnswerDTO();
            String extracted = StringUtils.defaultString(options.answers.get(i)).trim();
            QuAnswerDTO existing = getExistingAnswer(qu.getAnswerList(), i);
            if (StringUtils.isBlank(extracted) && existing != null) {
                extracted = StringUtils.defaultString(existing.getContent()).trim();
            }
            answer.setContent(extracted);
            answer.setIsRight(rightIndexes.contains(i));
            answer.setImage("");
            answer.setAnalysis(existing != null ? StringUtils.defaultString(existing.getAnalysis()) : "");
            answerList.add(answer);
        }

        qu.setContent(formatObjectiveChoiceStem(options.stemCode));
        qu.setAnswerList(answerList);
        enrichAnswerListFromSource(qu, sourceText, questionIndex);
    }

    private QuAnswerDTO getExistingAnswer(List<QuAnswerDTO> answers, int index) {
        if (CollectionUtils.isEmpty(answers) || index < 0 || index >= answers.size()) {
            return null;
        }
        return answers.get(index);
    }

    private int countNonBlankAnswers(List<QuAnswerDTO> answers) {
        if (CollectionUtils.isEmpty(answers)) {
            return 0;
        }
        int count = 0;
        for (QuAnswerDTO answer : answers) {
            if (answer != null && StringUtils.isNotBlank(answer.getContent())) {
                count++;
            }
        }
        return count;
    }

    private int countNonBlankOptions(List<String> options) {
        if (CollectionUtils.isEmpty(options)) {
            return 0;
        }
        int count = 0;
        for (String option : options) {
            if (StringUtils.isNotBlank(option)) {
                count++;
            }
        }
        return count;
    }

    private boolean isBetterOptionSet(ProgramChoiceOptions candidate, ProgramChoiceOptions current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        int candidateFilled = countNonBlankOptions(candidate.answers);
        int currentFilled = countNonBlankOptions(current.answers);
        if (candidateFilled != currentFilled) {
            return candidateFilled > currentFilled;
        }
        return candidate.answers.size() > current.answers.size();
    }

    private void enrichAnswerListFromSource(QuDetailDTO qu, String sourceText, int questionIndex) {
        if (CollectionUtils.isEmpty(qu.getAnswerList()) || StringUtils.isBlank(sourceText)) {
            return;
        }
        String sourceBlock = findSourceQuestionBlockByAnswer(sourceText, qu.getAnswerList());
        if (StringUtils.isBlank(sourceBlock)) {
            sourceBlock = findSourceQuestionBlock(sourceText, questionIndex, qu.getContent());
        }
        ProgramChoiceOptions sourceOptions = extractObjectiveChoiceOptions(sourceBlock);
        if (sourceOptions == null) {
            return;
        }
        fillBlankAnswersFromOptions(qu.getAnswerList(), sourceOptions.answers);
    }

    private void fillBlankAnswersFromOptions(List<QuAnswerDTO> answers, List<String> options) {
        if (CollectionUtils.isEmpty(answers) || CollectionUtils.isEmpty(options)) {
            return;
        }
        int limit = Math.min(answers.size(), options.size());
        for (int i = 0; i < limit; i++) {
            QuAnswerDTO answer = answers.get(i);
            if (answer == null || StringUtils.isNotBlank(answer.getContent())) {
                continue;
            }
            if (StringUtils.isNotBlank(options.get(i))) {
                answer.setContent(options.get(i));
            }
        }
    }

    private ProgramChoiceOptions extractObjectiveChoiceOptions(String content) {
        String text = StringUtils.defaultString(content).trim();
        Matcher firstOption = Pattern.compile("(?s)(?:^|\\n)\\s*A\\s*[\\.\\u3001\\uFF0E]\\s*").matcher(text);
        if (!firstOption.find()) {
            return null;
        }

        String stemCode = text.substring(0, firstOption.start()).trim();
        String optionText = text.substring(firstOption.start()).trim();
        List<String> options = extractLetteredOptions(optionText);
        if (countNonBlankOptions(options) < 2) {
            return null;
        }
        return new ProgramChoiceOptions(stemCode, options);
    }

    private List<String> extractLetteredOptions(String optionText) {
        String[] slots = new String[4];
        int maxIndex = -1;
        Matcher matcher = INLINE_OPTION_PATTERN.matcher(optionText);
        while (matcher.find()) {
            int letterIndex = Character.toUpperCase(matcher.group(1).charAt(0)) - 'A';
            if (letterIndex < 0 || letterIndex > 3) {
                continue;
            }
            String option = cleanChoiceOptionText(matcher.group(2));
            if (StringUtils.isNotBlank(option)) {
                slots[letterIndex] = option;
            }
            maxIndex = Math.max(maxIndex, letterIndex);
        }
        List<String> options = new ArrayList<>();
        for (int i = 0; i <= maxIndex; i++) {
            options.add(StringUtils.defaultString(slots[i]).trim());
        }
        return options;
    }

    private List<Integer> findRightOptionIndexes(List<String> options, List<QuAnswerDTO> answers) {
        List<Integer> indexes = new ArrayList<>();
        if (CollectionUtils.isEmpty(options) || CollectionUtils.isEmpty(answers)) {
            return indexes;
        }
        for (QuAnswerDTO answer : answers) {
            if (answer == null || !Boolean.TRUE.equals(answer.getIsRight())) {
                continue;
            }
            int index = findMatchingOptionIndex(options, answer.getContent());
            if (index >= 0 && !indexes.contains(index)) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private String findSourceQuestionBlockByAnswer(String sourceText, List<QuAnswerDTO> answers) {
        List<String> expectedAnswers = rightAnswerContentsForSourceMatch(answers);
        if (StringUtils.isBlank(sourceText) || expectedAnswers.isEmpty()) {
            return "";
        }

        List<String> blocks = splitQuestionBlocks(sourceText);
        if (CollectionUtils.isEmpty(blocks)) {
            return "";
        }

        String bestBlock = "";
        int bestScore = 0;
        for (String block : blocks) {
            ProgramChoiceOptions options = extractObjectiveChoiceOptions(block);
            if (options == null || CollectionUtils.isEmpty(options.answers)) {
                continue;
            }

            int score = 0;
            for (String expected : expectedAnswers) {
                if (findMatchingOptionContentIndex(options.answers, expected) >= 0) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestBlock = block;
            }
        }
        return bestScore > 0 ? bestBlock : "";
    }

    private List<String> rightAnswerContentsForSourceMatch(List<QuAnswerDTO> answers) {
        List<String> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(answers)) {
            return result;
        }
        for (QuAnswerDTO answer : answers) {
            if (answer == null || !Boolean.TRUE.equals(answer.getIsRight())) {
                continue;
            }
            String content = StringUtils.defaultString(answer.getContent()).trim();
            if (StringUtils.isBlank(content) || optionLetterIndex(content) >= 0) {
                continue;
            }
            result.add(content);
        }
        return result;
    }

    private int findMatchingOptionContentIndex(List<String> options, String expectedAnswer) {
        if (CollectionUtils.isEmpty(options) || StringUtils.isBlank(expectedAnswer)) {
            return -1;
        }
        String normalizedExpected = normalizeChoiceText(expectedAnswer);
        for (int i = 0; i < options.size(); i++) {
            if (StringUtils.equals(normalizeChoiceText(options.get(i)), normalizedExpected)) {
                return i;
            }
        }
        return -1;
    }

    private List<Integer> findAnswerLetterIndexesInText(String text, int optionCount) {
        List<Integer> indexes = new ArrayList<>();
        Matcher matcher = Pattern.compile(
                        "(?im)(?:^|\\n)\\s*\\u7B54\\u6848\\s*[:\\uFF1A]\\s*([A-D](?:\\s*[,/\\uFF0C\\u3001]?\\s*[A-D])*)\\b")
                .matcher(StringUtils.defaultString(text));
        if (!matcher.find()) {
            return indexes;
        }

        Matcher letterMatcher = Pattern.compile("[A-D]", Pattern.CASE_INSENSITIVE).matcher(matcher.group(1));
        while (letterMatcher.find()) {
            int index = Character.toUpperCase(letterMatcher.group().charAt(0)) - 'A';
            if (index >= 0 && index < optionCount && !indexes.contains(index)) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private String formatObjectiveChoiceStem(String stem) {
        String cleaned = QuestionBoundaryHelper.stripInlineFilledAnswer(stem);
        ProgramContentFormatter.StemCodeParts parts = programContentFormatter.splitStemAndCode(cleaned);
        return programContentFormatter.mergeStemAndCode(parts.getStem(), parts.getCode());
    }

    private void normalizeProgramChoiceQuestion(QuDetailDTO qu, String sourceText, int questionIndex) {
        if (!QuType.READ_PROGRAM.equals(qu.getQuType())) {
            return;
        }
        if (StringUtils.isBlank(qu.getContent())) {
            return;
        }

        ProgramChoiceOptions options = extractProgramChoiceOptions(qu.getContent());
        String sourceBlock = "";
        if (options == null || options.answers.size() < 4) {
            sourceBlock = findSourceQuestionBlock(sourceText, questionIndex, qu.getContent());
            options = extractProgramChoiceOptions(sourceBlock);
            if (options == null || options.answers.size() < 4) {
                return;
            }
        }

        String expectedAnswer = firstRightAnswerContent(qu.getAnswerList());
        int rightIndex = findMatchingOptionIndex(options.answers, expectedAnswer);
        if (rightIndex < 0) {
            rightIndex = findAnswerLetterIndexInText(qu.getContent(), options.answers.size());
        }
        if (rightIndex < 0) {
            rightIndex = findAnswerLetterIndexInText(sourceBlock, options.answers.size());
        }
        if (rightIndex < 0) {
            return;
        }

        List<QuAnswerDTO> answerList = new ArrayList<>();
        for (int i = 0; i < options.answers.size(); i++) {
            QuAnswerDTO answer = new QuAnswerDTO();
            answer.setContent(options.answers.get(i));
            answer.setIsRight(i == rightIndex);
            answer.setImage("");
            answer.setAnalysis("");
            answerList.add(answer);
        }

        ProgramContentFormatter.StemCodeParts parts = programContentFormatter.splitStemAndCode(options.stemCode);
        String content = programContentFormatter.mergeStemAndCode(parts.getStem(), parts.getCode());
        qu.setContent(content);
        qu.setAnswerList(answerList);
    }

    private String findSourceQuestionBlock(String sourceText, int questionIndex, String content) {
        List<String> blocks = splitQuestionBlocks(sourceText);
        if (CollectionUtils.isEmpty(blocks)) {
            return "";
        }
        String matchedByCode = findSourceQuestionBlockByCode(blocks, content);
        if (StringUtils.isNotBlank(matchedByCode)) {
            return matchedByCode;
        }

        if (questionIndex > 0 && questionIndex <= blocks.size()) {
            return blocks.get(questionIndex - 1);
        }
        return "";
    }

    private String findSourceQuestionBlockByCode(List<String> blocks, String content) {
        List<String> keys = distinctiveCodeKeys(content);
        if (CollectionUtils.isEmpty(keys)) {
            return "";
        }
        String bestBlock = "";
        int bestScore = 0;
        for (String block : blocks) {
            String normalizedBlock = normalizeCodeKey(block);
            int score = 0;
            for (String key : keys) {
                if (normalizedBlock.contains(key)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestBlock = block;
            }
        }
        return bestScore > 0 ? bestBlock : "";
    }

    private List<String> distinctiveCodeKeys(String text) {
        List<String> keys = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return keys;
        }
        String[] lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        for (String line : lines) {
            String value = line.trim();
            if (!isDistinctiveCodeLine(value)) {
                continue;
            }
            String key = normalizeCodeKey(value);
            if (StringUtils.isNotBlank(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private boolean isDistinctiveCodeLine(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }
        String value = line.trim();
        if (value.length() < 8) {
            return false;
        }
        if (value.startsWith("#include") || value.matches("^(int\\s+)?main\\s*\\(.*")) {
            return false;
        }
        if (value.matches("^(return\\s+0\\s*;?|[{}]+)$")) {
            return false;
        }
        return value.matches(".*\\b(scanf|printf|for|if|while)\\b.*")
                || value.contains("?")
                || value.contains("=")
                || value.contains("%");
    }

    private String normalizeCodeKey(String text) {
        return StringUtils.defaultString(text).replaceAll("\\s+", "");
    }

    private ProgramChoiceOptions extractProgramChoiceOptions(String content) {
        String text = StringUtils.defaultString(content).trim();
        Matcher firstOption = Pattern.compile("(?s)\\sA\\s*[\\.、．]\\s*").matcher(text);
        if (!firstOption.find()) {
            return null;
        }

        String stemCode = text.substring(0, firstOption.start()).trim();
        String optionText = text.substring(firstOption.start()).trim();
        List<String> options = extractLetteredOptions(optionText);
        if (options.size() < 4 || countNonBlankOptions(options) < 4 || !containsCodeBlock(stemCode)) {
            return null;
        }
        return new ProgramChoiceOptions(stemCode, options);
    }

    private String cleanChoiceOptionText(String option) {
        return StringUtils.defaultString(option)
                .replaceAll("(?is)\\s*答案\\s*[:：]\\s*[A-D]\\b.*$", "")
                .trim();
    }

    private String firstRightAnswerContent(List<QuAnswerDTO> answers) {
        if (CollectionUtils.isEmpty(answers)) {
            return "";
        }
        for (QuAnswerDTO answer : answers) {
            if (answer != null && Boolean.TRUE.equals(answer.getIsRight())) {
                return StringUtils.defaultString(answer.getContent()).trim();
            }
        }
        return StringUtils.defaultString(answers.get(0).getContent()).trim();
    }

    private int findMatchingOptionIndex(List<String> options, String expectedAnswer) {
        if (CollectionUtils.isEmpty(options) || StringUtils.isBlank(expectedAnswer)) {
            return -1;
        }
        int optionLetterIndex = optionLetterIndex(expectedAnswer);
        if (optionLetterIndex >= 0 && optionLetterIndex < options.size()) {
            return optionLetterIndex;
        }
        String normalizedExpected = normalizeChoiceText(expectedAnswer);
        for (int i = 0; i < options.size(); i++) {
            if (StringUtils.equals(normalizeChoiceText(options.get(i)), normalizedExpected)) {
                return i;
            }
        }
        return -1;
    }

    private int optionLetterIndex(String expectedAnswer) {
        Matcher matcher = Pattern.compile("(?is)^\\s*(?:答案[:：]?\\s*)?([A-D])\\s*(?:[\\.\\u3001\\uFF0E].*)?$")
                .matcher(StringUtils.defaultString(expectedAnswer));
        if (!matcher.matches()) {
            return -1;
        }
        return Character.toUpperCase(matcher.group(1).charAt(0)) - 'A';
    }

    private String normalizeChoiceText(String text) {
        return StringUtils.defaultString(text)
                .replaceAll("^\\s*[A-Da-d]\\s*[\\.、．]\\s*", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    private int findAnswerLetterIndexInText(String text, int optionCount) {
        Matcher matcher = Pattern.compile("(?im)(?:^|\\n)\\s*答案\\s*[:：]\\s*([A-D])\\b")
                .matcher(StringUtils.defaultString(text));
        if (!matcher.find()) {
            return -1;
        }
        int index = Character.toUpperCase(matcher.group(1).charAt(0)) - 'A';
        return index >= 0 && index < optionCount ? index : -1;
    }

    private boolean isReadProgramChoiceQuestion(QuDetailDTO qu) {
        if (!QuType.READ_PROGRAM.equals(qu.getQuType()) || CollectionUtils.isEmpty(qu.getAnswerList())) {
            return false;
        }
        if (qu.getAnswerList().size() < 2) {
            return false;
        }
        for (QuAnswerDTO answer : qu.getAnswerList()) {
            if (answer == null || StringUtils.isBlank(answer.getContent())) {
                return false;
            }
            if (looksLikeProgramSkeleton(answer.getContent())) {
                return false;
            }
        }
        return true;
    }

    private static class ProgramChoiceOptions {
        private final String stemCode;
        private final List<String> answers;

        private ProgramChoiceOptions(String stemCode, List<String> answers) {
            this.stemCode = stemCode;
            this.answers = answers;
        }
    }

    private void normalizeReadProgramQuestion(QuDetailDTO qu) {
        if (!QuType.READ_PROGRAM.equals(qu.getQuType())) {
            return;
        }
        // Skip if normalizeProgramChoiceQuestion already set up choice-style answers
        if (isReadProgramChoiceQuestion(qu)) {
            ProgramChoiceOptions options = extractProgramChoiceOptions(StringUtils.defaultString(qu.getContent()).trim());
            if (options != null) {
                ProgramContentFormatter.StemCodeParts parts = programContentFormatter.splitStemAndCode(options.stemCode);
                qu.setContent(programContentFormatter.mergeStemAndCode(parts.getStem(), parts.getCode()));
            }
            return;
        }

        String content = StringUtils.defaultString(qu.getContent()).trim();
        List<QuAnswerDTO> resultAnswers = new ArrayList<>();
        String programFromAnswer = null;

        if (!CollectionUtils.isEmpty(qu.getAnswerList())) {
            for (QuAnswerDTO answer : qu.getAnswerList()) {
                if (answer == null || StringUtils.isBlank(answer.getContent())) {
                    continue;
                }
                String answerText = answer.getContent().trim();
                if (looksLikeProgramSkeleton(answerText)) {
                    if (programFromAnswer == null) {
                        programFromAnswer = answerText;
                    }
                } else {
                    answer.setIsRight(true);
                    resultAnswers.add(answer);
                }
            }
        }

        if (programFromAnswer != null && !containsCodeBlock(content)) {
            content = StringUtils.isBlank(content)
                    ? programFromAnswer
                    : content + "\n\n" + programFromAnswer;
        }

        ProgramContentFormatter.StemCodeParts parts = programContentFormatter.splitStemAndCode(content);
        if (StringUtils.isNotBlank(parts.getCode())) {
            content = programContentFormatter.mergeStemAndCode(parts.getStem(), parts.getCode());
        } else {
            content = programContentFormatter.cleanStem(content);
        }

        qu.setContent(content);
        qu.setAnswerList(resultAnswers);
    }

    /**
     * 程序改错题：content 存题干+有错程序，answerList 存改正后程序
     */
    private void normalizeFixProgramQuestion(QuDetailDTO qu) {
        if (!QuType.FIX_PROGRAM.equals(qu.getQuType())) {
            return;
        }

        String content = StringUtils.defaultString(qu.getContent()).trim();

        if (CollectionUtils.isEmpty(qu.getAnswerList())) {
            splitReferenceFromContent(qu, "改正后程序代码。");
            content = StringUtils.defaultString(qu.getContent()).trim();
        }

        List<QuAnswerDTO> fixedAnswers = new ArrayList<>();
        String buggyFromAnswer = null;

        if (!CollectionUtils.isEmpty(qu.getAnswerList())) {
            for (QuAnswerDTO answer : qu.getAnswerList()) {
                if (answer == null || StringUtils.isBlank(answer.getContent())) {
                    continue;
                }
                String answerText = answer.getContent().trim();
                if (containsCodeBlock(answerText)) {
                    answer.setIsRight(true);
                    fixedAnswers.add(answer);
                } else if (buggyFromAnswer == null && looksLikeProgramSkeleton(answerText)) {
                    buggyFromAnswer = answerText;
                }
            }
        }

        if (buggyFromAnswer != null && !containsCodeBlock(content)) {
            content = StringUtils.isBlank(content)
                    ? buggyFromAnswer
                    : content + "\n\n" + buggyFromAnswer;
        }

        if (!fixedAnswers.isEmpty()) {
            QuAnswerDTO fixed = fixedAnswers.get(0);
            String fixedCode = fixed.getContent().trim();
            if (content.endsWith(fixedCode)) {
                content = content.substring(0, content.length() - fixedCode.length()).trim();
            }
        }

        ProgramContentFormatter.StemCodeParts parts = programContentFormatter.splitStemAndCode(content);
        if (StringUtils.isNotBlank(parts.getCode())) {
            content = programContentFormatter.mergeStemAndCode(parts.getStem(), parts.getCode());
        }

        if (!fixedAnswers.isEmpty()) {
            for (QuAnswerDTO answer : fixedAnswers) {
                answer.setContent(programContentFormatter.formatProgramCode(answer.getContent()));
            }
            qu.setAnswerList(fixedAnswers);
        }

        qu.setContent(content);
    }

    private boolean isBlankFillAnswer(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        if (text.contains("{") || text.contains("}")) {
            return false;
        }
        if (text.split("\n").length > 2) {
            return false;
        }
        return text.length() <= 120;
    }

    private boolean looksLikeProgramSkeleton(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        return containsCodeBlock(text) && (text.contains("{") || text.contains("#include"));
    }

    /**
     * 编程题等：若 AI 把题干和程序代码混在 content，自动拆分到 answerList
     */
    private void splitStemAndReference(QuDetailDTO qu) {
        if (qu.getQuType() == null || StringUtils.isBlank(qu.getContent())) {
            return;
        }

        if (QuType.FILL_PROGRAM.equals(qu.getQuType())) {
            return;
        }

        if (QuType.READ_PROGRAM.equals(qu.getQuType())) {
            return;
        }

        if (QuType.PROGRAM.equals(qu.getQuType())) {
            splitProgramQuestion(qu);
            return;
        }

        if (QuType.FIX_PROGRAM.equals(qu.getQuType()) && CollectionUtils.isEmpty(qu.getAnswerList())) {
            splitReferenceFromContent(qu, "改正后程序代码。");
            return;
        }

        if (QuType.FIX_PROGRAM.equals(qu.getQuType())) {
            return;
        }
    }

    private void splitProgramQuestion(QuDetailDTO qu) {
        String content = qu.getContent().trim();

        if (!CollectionUtils.isEmpty(qu.getAnswerList())) {
            boolean hasCodeAnswer = false;
            for (QuAnswerDTO answer : qu.getAnswerList()) {
                if (answer != null && containsCodeBlock(answer.getContent())) {
                    hasCodeAnswer = true;
                    break;
                }
            }
            if (hasCodeAnswer) {
                qu.setContent(stripEmbeddedCode(content));
                return;
            }
        }

        int codeStart = findCodeBlockStart(content);
        if (codeStart <= 0) {
            return;
        }

        String stem = content.substring(0, codeStart).trim();
        String code = content.substring(codeStart).trim();
        if (StringUtils.isBlank(stem) || !containsCodeBlock(code)) {
            return;
        }

        qu.setContent(stem);
        if (CollectionUtils.isEmpty(qu.getAnswerList())) {
            qu.setAnswerList(new ArrayList<>());
        }
        if (qu.getAnswerList().isEmpty()) {
            QuAnswerDTO answer = new QuAnswerDTO();
            answer.setContent(code);
            answer.setIsRight(true);
            answer.setImage("");
            answer.setAnalysis("参考程序代码。");
            qu.getAnswerList().add(answer);
        } else {
            QuAnswerDTO first = qu.getAnswerList().get(0);
            if (StringUtils.isBlank(first.getContent()) || !containsCodeBlock(first.getContent())) {
                first.setContent(code);
                first.setIsRight(true);
            }
        }
    }

    private void splitReferenceFromContent(QuDetailDTO qu, String analysis) {
        String content = qu.getContent().trim();
        int codeStart = findCodeBlockStart(content);
        if (codeStart <= 0) {
            return;
        }

        int secondCodeStart = findCodeBlockStart(content.substring(codeStart + 1));
        if (secondCodeStart < 0) {
            return;
        }
        secondCodeStart = codeStart + 1 + secondCodeStart;

        String stemWithBuggyCode = content.substring(0, secondCodeStart).trim();
        String fixedCode = content.substring(secondCodeStart).trim();
        if (StringUtils.isBlank(fixedCode) || !containsCodeBlock(fixedCode)) {
            return;
        }

        qu.setContent(stemWithBuggyCode);
        QuAnswerDTO answer = new QuAnswerDTO();
        answer.setContent(fixedCode);
        answer.setIsRight(true);
        answer.setImage("");
        answer.setAnalysis(analysis);
        qu.setAnswerList(new ArrayList<>());
        qu.getAnswerList().add(answer);
    }

    private String stripEmbeddedCode(String content) {
        int codeStart = findCodeBlockStart(content);
        if (codeStart > 0) {
            return content.substring(0, codeStart).trim();
        }
        return content;
    }

    private int findCodeBlockStart(String text) {
        return programContentFormatter.findCodeBlockStart(text);
    }

    private boolean containsCodeBlock(String text) {
        return findCodeBlockStart(text) >= 0;
    }

    private int countBlankPlaceholders(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (true) {
            int idx = text.indexOf(FillProgramBlankProcessor.BLANK, from);
            if (idx < 0) {
                break;
            }
            count++;
            from = idx + FillProgramBlankProcessor.BLANK.length();
        }
        return count;
    }

    private void checkQuestion(QuDetailDTO qu, int index) {
        if (StringUtils.isBlank(qu.getContent())) {
            throw new ServiceException("第" + index + "题题干为空");
        }

        if (qu.getQuType() == null) {
            throw new ServiceException("第" + index + "题题型为空");
        }

        if (!QuType.isObjective(qu.getQuType()) && !QuType.isSubjective(qu.getQuType())) {
            throw new ServiceException("第" + index + "题题型错误");
        }

        if (QuType.isObjective(qu.getQuType())) {
            checkObjectiveQuestion(qu, index);
        } else {
            checkSubjectiveQuestion(qu, index);
        }
    }

    private void checkObjectiveQuestion(QuDetailDTO qu, int index) {
        if (CollectionUtils.isEmpty(qu.getAnswerList())) {
            throw new ServiceException("第" + index + "题选项为空");
        }

        int rightCount = 0;
        for (QuAnswerDTO answer : qu.getAnswerList()) {
            answer.setId("");
            answer.setQuId(null);

            if (StringUtils.isBlank(answer.getImage())) {
                answer.setImage("");
            }
            if (StringUtils.isBlank(answer.getAnalysis())) {
                answer.setAnalysis(buildAnswerAnalysis(qu, answer));
            }
            if (StringUtils.isBlank(answer.getContent())) {
                throw new ServiceException("第" + index + "题存在空选项");
            }
            if (answer.getIsRight() == null) {
                throw new ServiceException("第" + index + "题存在未标记正确性的选项");
            }
            if (answer.getIsRight()) {
                rightCount++;
            }
        }

        if (rightCount == 0) {
            throw new ServiceException("第" + index + "题没有正确答案");
        }
        if (QuType.RADIO.equals(qu.getQuType()) && rightCount != 1) {
            throw new ServiceException("第" + index + "题是单选题，但正确答案数量不是1个");
        }
        if (QuType.JUDGE.equals(qu.getQuType()) && rightCount != 1) {
            throw new ServiceException("第" + index + "题是判断题，但正确答案数量不是1个");
        }
    }

    private void checkSubjectiveQuestion(QuDetailDTO qu, int index) {
        if (QuType.FILL_PROGRAM.equals(qu.getQuType())) {
            if (CollectionUtils.isEmpty(qu.getAnswerList())) {
                throw new ServiceException("第" + index + "题程序填空题缺少各空参考答案");
            }
            if (!containsCodeBlock(qu.getContent())) {
                throw new ServiceException("第" + index + "题程序填空题 content 应包含程序代码骨架");
            }
            int blankCount = countBlankPlaceholders(qu.getContent());
            if (blankCount == 0) {
                throw new ServiceException("第" + index + "题程序填空题代码中应使用 ____ 标记空位");
            }
            if (blankCount != qu.getAnswerList().size()) {
                throw new ServiceException("第" + index + "题程序填空题空位数量(" + blankCount
                        + ")与 answerList 数量(" + qu.getAnswerList().size() + ")不一致");
            }
            for (QuAnswerDTO answer : qu.getAnswerList()) {
                if (answer != null && looksLikeProgramSkeleton(answer.getContent())) {
                    throw new ServiceException("第" + index + "题程序填空题的 answerList 应只存各空答案，不应包含完整程序");
                }
            }
        } else if (QuType.FILL.equals(qu.getQuType()) && CollectionUtils.isEmpty(qu.getAnswerList())) {
            throw new ServiceException("第" + index + "题缺少参考答案");
        } else if (QuType.READ_PROGRAM.equals(qu.getQuType())) {
            if (!containsCodeBlock(qu.getContent())) {
                throw new ServiceException("第" + index + "题阅读程序题 content 应包含完整程序");
            }
            if (CollectionUtils.isEmpty(qu.getAnswerList())) {
                throw new ServiceException("第" + index + "题阅读程序题缺少运行结果/参考答案");
            }
            for (QuAnswerDTO answer : qu.getAnswerList()) {
                if (answer != null && looksLikeProgramSkeleton(answer.getContent())) {
                    throw new ServiceException("第" + index + "题阅读程序题的 answerList 应存运行结果，不应包含完整程序");
                }
            }
            if (isReadProgramChoiceQuestion(qu)) {
                int trueCount = 0;
                for (QuAnswerDTO answer : qu.getAnswerList()) {
                    if (answer != null && Boolean.TRUE.equals(answer.getIsRight())) {
                        trueCount += 1;
                    }
                }
                if (trueCount != 1) {
                    throw new ServiceException("第" + index + "题阅读程序选择题只能有一个正确项");
                }
            }
        } else if (QuType.FIX_PROGRAM.equals(qu.getQuType())) {
            if (!containsCodeBlock(qu.getContent())) {
                throw new ServiceException("第" + index + "题程序改错题 content 应包含有错程序");
            }
            if (CollectionUtils.isEmpty(qu.getAnswerList())) {
                throw new ServiceException("第" + index + "题程序改错题缺少改正后程序");
            }
            boolean hasFixedCode = false;
            for (QuAnswerDTO answer : qu.getAnswerList()) {
                if (answer != null && containsCodeBlock(answer.getContent())) {
                    hasFixedCode = true;
                    break;
                }
            }
            if (!hasFixedCode) {
                throw new ServiceException("第" + index + "题程序改错题的参考答案必须是改正后的程序代码");
            }
        }

        if (QuType.PROGRAM.equals(qu.getQuType())) {
            if (CollectionUtils.isEmpty(qu.getAnswerList())) {
                throw new ServiceException("第" + index + "题编程题缺少参考程序代码");
            }
            boolean hasCode = false;
            for (QuAnswerDTO answer : qu.getAnswerList()) {
                if (answer != null && containsCodeBlock(answer.getContent())) {
                    hasCode = true;
                    break;
                }
            }
            if (!hasCode) {
                throw new ServiceException("第" + index + "题编程题的参考答案必须是程序代码");
            }
            qu.setContent(stripEmbeddedCode(qu.getContent()));
        }

        if (CollectionUtils.isEmpty(qu.getAnswerList())) {
            return;
        }

        for (QuAnswerDTO answer : qu.getAnswerList()) {
            answer.setId("");
            answer.setQuId(null);

            if (StringUtils.isBlank(answer.getImage())) {
                answer.setImage("");
            }
            if (StringUtils.isBlank(answer.getAnalysis())) {
                answer.setAnalysis(buildAnswerAnalysis(qu, answer));
            }
            if (StringUtils.isBlank(answer.getContent())) {
                throw new ServiceException("第" + index + "题存在空的参考答案/评分要点");
            }
            if (answer.getIsRight() == null) {
                if (QuType.READ_PROGRAM.equals(qu.getQuType()) && isReadProgramChoiceQuestion(qu)) {
                    answer.setIsRight(Boolean.FALSE);
                } else {
                    answer.setIsRight(Boolean.TRUE);
                }
            }
        }
    }

    private String buildQuestionAnalysis(QuDetailDTO qu) {
        if (QuType.READ_PROGRAM.equals(qu.getQuType()) && isReadProgramChoiceQuestion(qu)) {
            String rightAnswers = buildRightAnswerText(qu);
            if (StringUtils.isBlank(rightAnswers)) {
                return "本题考查程序阅读，请结合题干和选项判断。";
            }
            return "本题考查程序阅读，正确答案为：" + rightAnswers + "。";
        }
        if (QuType.isSubjective(qu.getQuType())) {
            String rightAnswers = buildRightAnswerText(qu);
            if (StringUtils.isBlank(rightAnswers)) {
                return "本题为主观题，请结合题干与参考答案进行作答。";
            }
            return "本题为主观题，参考答案/评分要点：" + rightAnswers + "。";
        }
        String rightAnswers = buildRightAnswerText(qu);
        if (StringUtils.isBlank(rightAnswers)) {
            return "本题考查相关知识点，请结合题干和选项判断。";
        }
        return "本题考查相关知识点，正确答案为：" + rightAnswers + "。";
    }

    private String buildAnswerAnalysis(QuDetailDTO qu, QuAnswerDTO answer) {
        if (QuType.FILL_PROGRAM.equals(qu.getQuType())) {
            return "该空位的参考答案。";
        }
        if (QuType.READ_PROGRAM.equals(qu.getQuType())) {
            if (isReadProgramChoiceQuestion(qu)) {
                if (answer.getIsRight() != null && answer.getIsRight()) {
                    return "该选项为正确答案，符合题干要求。";
                }
                return "该选项不是正确答案，不符合题干要求。";
            }
            return "程序运行结果/参考答案。";
        }
        if (QuType.PROGRAM.equals(qu.getQuType())) {
            return "参考程序代码。";
        }
        if (QuType.FIX_PROGRAM.equals(qu.getQuType())) {
            return "改正后的程序代码。";
        }
        if (QuType.isSubjective(qu.getQuType())) {
            return "参考答案/评分要点。";
        }
        if (answer.getIsRight() != null && answer.getIsRight()) {
            return "该选项为正确答案，符合题干要求。";
        }
        return "该选项不是正确答案，不符合题干要求。";
    }

    private String buildRightAnswerText(QuDetailDTO qu) {
        if (CollectionUtils.isEmpty(qu.getAnswerList())) {
            return "";
        }

        List<String> rightAnswers = new ArrayList<>();
        for (int i = 0; i < qu.getAnswerList().size(); i++) {
            QuAnswerDTO answer = qu.getAnswerList().get(i);
            if (answer.getIsRight() != null && answer.getIsRight()) {
                rightAnswers.add(String.valueOf((char) ('A' + i)) + "." + answer.getContent());
            }
        }
        return StringUtils.join(rightAnswers, "，");
    }
    @Autowired
    private QuService quService;

    @Autowired
    private RepoService repoService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public QuestionImportRespDTO importQuestions(QuestionImportReqDTO reqDTO) {

        if (reqDTO == null || CollectionUtils.isEmpty(reqDTO.getQuestions())) {
            throw new ServiceException("导入试题不能为空");
        }

        int index = 1;
        for (QuDetailDTO qu : reqDTO.getQuestions()) {
            prepareQuestionForInsert(qu, index);
            try {
                quService.save(qu);
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                throw new ServiceException(buildImportSaveErrorMessage(qu, index, e));
            }
            index++;
        }

        QuestionImportRespDTO respDTO = new QuestionImportRespDTO();
        respDTO.setCount(reqDTO.getQuestions().size());
        return respDTO;
    }

    private void prepareQuestionForInsert(QuDetailDTO qu, int index) {

        normalizeObjectiveChoiceQuestion(qu, null, index);
        normalizeProgramChoiceQuestion(qu, null, index);
        normalizeFillProgramQuestion(qu, null, index);
        normalizeReadProgramQuestion(qu);
        normalizeFixProgramQuestion(qu);
        splitStemAndReference(qu);
        sanitizeVisibleFillMarkers(qu);
        checkQuestion(qu, index);

        if (CollectionUtils.isEmpty(qu.getRepoIds())) {
            throw new ServiceException("第" + index + "题没有选择题库");
        }

        for (String repoId : qu.getRepoIds()) {
            if (StringUtils.isBlank(repoId) || repoService.getById(repoId) == null) {
                throw new ServiceException("第" + index + "题题库ID不存在：" + repoId);
            }
        }

        qu.setId(null);
        qu.setCreateTime(null);
        qu.setUpdateTime(null);

        if (StringUtils.isBlank(qu.getImage())) {
            qu.setImage("");
        }
        if (StringUtils.isBlank(qu.getRemark())) {
            qu.setRemark("");
        }
        if (StringUtils.isBlank(qu.getAnalysis())) {
            qu.setAnalysis(buildQuestionAnalysis(qu));
        }

        for (QuAnswerDTO answer : qu.getAnswerList()) {
            answer.setId(null);
            answer.setQuId(null);

            if (StringUtils.isBlank(answer.getImage())) {
                answer.setImage("");
            }
            if (StringUtils.isBlank(answer.getAnalysis())) {
                answer.setAnalysis(buildAnswerAnalysis(qu, answer));
            } else {
                answer.setAnalysis(restoreFillMarkers(answer.getAnalysis()));
            }
        }
    }

    private String buildImportSaveErrorMessage(QuDetailDTO qu, int index, Exception e) {
        String reason = deepestExceptionMessage(e);
        if (StringUtils.isBlank(reason)) {
            reason = e.getClass().getSimpleName();
        }

        StringBuilder message = new StringBuilder("第").append(index).append("题入库失败：").append(reason);
        if (StringUtils.containsIgnoreCase(reason, "data too long")
                || StringUtils.containsIgnoreCase(reason, "too long")
                || StringUtils.containsIgnoreCase(reason, "truncat")) {
            message.append("。请检查题干/解析是否超过数据库字段长度");
        }
        if (qu != null) {
            message.append("（题型=").append(qu.getQuType())
                    .append("，题干长度=").append(StringUtils.length(qu.getContent()))
                    .append("，解析长度=").append(StringUtils.length(qu.getAnalysis()))
                    .append("）");
        }
        return message.toString();
    }

    private String deepestExceptionMessage(Throwable throwable) {
        Throwable cursor = throwable;
        String message = "";
        while (cursor != null) {
            if (StringUtils.isNotBlank(cursor.getMessage())) {
                message = cursor.getMessage();
            }
            cursor = cursor.getCause();
        }
        return message;
    }

    private void sanitizeVisibleFillMarkers(QuDetailDTO qu) {
        if (qu == null) {
            return;
        }
        qu.setContent(FillProgramBlankProcessor.hideFillMarkersForDisplay(qu.getContent()));
        qu.setAnalysis(FillProgramBlankProcessor.hideFillMarkersForDisplay(qu.getAnalysis()));
        qu.setRemark(FillProgramBlankProcessor.hideFillMarkersForDisplay(qu.getRemark()));

        if (CollectionUtils.isEmpty(qu.getAnswerList())) {
            return;
        }
        for (QuAnswerDTO answer : qu.getAnswerList()) {
            if (answer == null) {
                continue;
            }
            answer.setContent(FillProgramBlankProcessor.hideFillMarkersForDisplay(answer.getContent()));
            answer.setAnalysis(FillProgramBlankProcessor.hideFillMarkersForDisplay(answer.getAnalysis()));
        }
    }

    private String restoreFillMarkers(String text) {
        return FillProgramBlankProcessor.restoreFillMarkers(text);
    }
}
