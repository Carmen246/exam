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
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import com.yf.exam.modules.qu.dto.QuestionImportReqDTO;
import com.yf.exam.modules.qu.dto.QuestionImportRespDTO;
import com.yf.exam.modules.qu.service.QuService;
import com.yf.exam.modules.repo.service.RepoService;
import org.springframework.transaction.annotation.Transactional;
import com.yf.exam.modules.qu.dto.QuestionNormalizeTextReqDTO;
import com.yf.exam.modules.qu.dto.QuestionNormalizeTextRespDTO;
import com.yf.exam.modules.qu.support.ImportTaskProgressListener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class QuestionAiParseServiceImpl implements QuestionAiParseService {

    private static final int DEFAULT_NORMALIZE_BATCH_LENGTH = 3500;
    private static final int DEFAULT_PARSE_BATCH_LENGTH = 1800;
    private static final int FALLBACK_PARSE_BATCH_LENGTH = 1000;
    private static final int NORMALIZE_BATCH_QUESTION_COUNT = 6;
    private static final int PARSE_BATCH_QUESTION_COUNT = 2;
    private static final int FALLBACK_PARSE_BATCH_QUESTION_COUNT = 1;
    private static final int AI_RETRY_TIMES = 2;
    private static final Pattern QUESTION_START_PATTERN = Pattern.compile("(?m)(?=^\\s*\\d+[\\.．、]\\s*)");

    private static final String SYSTEM_PROMPT =
            "你是在线考试系统的试题解析器。你只能返回合法 json，不要返回 Markdown，不要解释，不要使用代码块。"
                    + "题型编码固定为：1=单选题，2=多选题，3=判断题。"
                    + "只解析客观题，无法确定答案的题目不要输出。"
                  + "返回格式必须是："
                  + "{\"questions\":[{\"quType\":1,\"level\":1,\"content\":\"题干\","
                  + "\"analysis\":\"整体解析，说明本题考查点和正确答案依据，不能为空\",\"image\":\"\",\"remark\":\"\","
                  + "\"repoIds\":[],\"answerList\":[{\"content\":\"选项内容\","
                  + "\"isRight\":true,\"image\":\"\",\"analysis\":\"答案解析，说明该选项为什么正确或错误，不能为空\"}]}]}";
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
                    + "返回格式必须是：{\"normalizedText\":\"整理后的试题文本\"}";

    @Autowired
    private QuestionAiProperties properties;

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

        List<String> chunks = splitQuestionText(reqDTO.getText(), maxBatchLength(DEFAULT_NORMALIZE_BATCH_LENGTH),
                NORMALIZE_BATCH_QUESTION_COUNT);
        if (chunks.size() > 1) {
            StringBuilder normalizedText = new StringBuilder();
            int batchIndex = 1;
            for (String chunk : chunks) {
                if (listener != null) {
                    listener.onBatchProgress(batchIndex - 1, chunks.size(),
                            "正在AI清洗第" + batchIndex + "批文本（共" + chunks.size() + "批）");
                }
                QuestionNormalizeTextRespDTO part = normalizeTextWithRetry(chunk, String.valueOf(batchIndex));
                if (StringUtils.isNotBlank(part.getNormalizedText())) {
                    if (normalizedText.length() > 0) {
                        normalizedText.append("\n\n");
                    }
                    normalizedText.append(part.getNormalizedText().trim());
                }
                if (listener != null) {
                    listener.onBatchProgress(batchIndex, chunks.size(),
                            "已完成AI清洗第" + batchIndex + "批文本（共" + chunks.size() + "批）");
                }
                batchIndex++;
            }

            if (normalizedText.length() == 0) {
                throw new ServiceException("AI未返回清洗后的文本");
            }

            QuestionNormalizeTextRespDTO respDTO = new QuestionNormalizeTextRespDTO();
            respDTO.setRawText(reqDTO.getText());
            respDTO.setNormalizedText(normalizedText.toString());
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

    private QuestionParseRespDTO parseQuestionsBatch(QuestionParseReqDTO sourceReq, String chunk, String batchNo) {
        try {
            return parseQuestionsWithRetry(copyParseReq(sourceReq, chunk), batchNo);
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
                            .maxTokens(5000)
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

    private List<String> splitQuestionText(String text, int maxLength, int maxQuestionCount) {
        List<String> blocks = splitQuestionBlocks(text);
        if (blocks.size() <= 1) {
            return splitByLength(text, maxLength);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int questionCount = 0;

        for (String block : blocks) {
            if (StringUtils.isBlank(block)) {
                continue;
            }

            String value = block.trim();
            boolean shouldFlush = current.length() > 0
                    && (current.length() + value.length() + 2 > maxLength || questionCount >= maxQuestionCount);
            if (shouldFlush) {
                chunks.add(current.toString().trim());
                current.setLength(0);
                questionCount = 0;
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

    private List<String> splitQuestionBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] parts = QUESTION_START_PATTERN.split(normalized);
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                blocks.add(part.trim());
            }
        }
        return blocks;
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
                + "1. 单选题 quType=1，只能有一个正确答案。\n"
                + "2. 多选题 quType=2，可以有多个正确答案。\n"
                + "3. 判断题 quType=3，选项固定为：正确、错误。\n"
                + "4. 每道题必须包含 content、quType、answerList。\n"
                + "5. repoIds 使用：" + JSON.toJSONString(reqDTO.getRepoIds()) + "。\n"
                + "6. level 使用：" + (reqDTO.getLevel() == null ? 1 : reqDTO.getLevel()) + "。\n"
                + "7. 必须生成整体解析：每道题的 analysis 字段不能为空，要说明本题考查点、正确答案依据。\n"
                + "8. 必须生成答案解析：answerList 中每个选项的 analysis 字段不能为空，要说明该选项为什么正确或错误。\n"
                + "9. 如果原文没有解析，也要根据题干、选项和答案生成简洁解析。\n"
                + "10. 不允许把 analysis 返回为空字符串。\n"
                + "11. 不要编造题目，不要输出无法判断答案的题目。\n\n"
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

            checkQuestion(qu, index);
            index++;
        }
    }

    private void checkQuestion(QuDetailDTO qu, int index) {
        if (StringUtils.isBlank(qu.getContent())) {
            throw new ServiceException("第" + index + "题题干为空");
        }

        if (!QuType.RADIO.equals(qu.getQuType())
                && !QuType.MULTI.equals(qu.getQuType())
                && !QuType.JUDGE.equals(qu.getQuType())) {
            throw new ServiceException("第" + index + "题题型错误");
        }

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

    private String buildQuestionAnalysis(QuDetailDTO qu) {
        String rightAnswers = buildRightAnswerText(qu);
        if (StringUtils.isBlank(rightAnswers)) {
            return "本题考查相关知识点，请结合题干和选项判断。";
        }
        return "本题考查相关知识点，正确答案为：" + rightAnswers + "。";
    }

    private String buildAnswerAnalysis(QuDetailDTO qu, QuAnswerDTO answer) {
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
            quService.save(qu);
            index++;
        }

        QuestionImportRespDTO respDTO = new QuestionImportRespDTO();
        respDTO.setCount(reqDTO.getQuestions().size());
        return respDTO;
    }

    private void prepareQuestionForInsert(QuDetailDTO qu, int index) {

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
            }
        }
    }
}
