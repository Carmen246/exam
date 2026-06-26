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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionAiParseServiceImpl implements QuestionAiParseService {

    private static final String SYSTEM_PROMPT =
            "你是在线考试系统的试题解析器。你只能返回合法 json，不要返回 Markdown，不要解释，不要使用代码块。"
                    + "题型编码固定为：1=单选题，2=多选题，3=判断题。"
                    + "只解析客观题，无法确定答案的题目不要输出。"
                    + "返回格式必须是："
                    + "{\"questions\":[{\"quType\":1,\"level\":1,\"content\":\"题干\","
                    + "\"analysis\":\"解析\",\"image\":\"\",\"remark\":\"\","
                    + "\"repoIds\":[],\"answerList\":[{\"content\":\"选项内容\","
                    + "\"isRight\":true,\"image\":\"\",\"analysis\":\"\"}]}]}";

    @Autowired
    private QuestionAiProperties properties;

    private volatile ChatLanguageModel chatModel;

    @Override
    public QuestionParseRespDTO parseQuestions(QuestionParseReqDTO reqDTO) {
        checkRequest(reqDTO);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));
        messages.add(UserMessage.from(buildUserPrompt(reqDTO)));

        Response<AiMessage> response = getChatModel().generate(messages);
        String answer = response.content().text();
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
                            .maxTokens(3000)
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

        if (properties.getMaxTextLength() != null
                && reqDTO.getText().length() > properties.getMaxTextLength()) {
            throw new ServiceException("文本过长，请拆分后再解析");
        }
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
                + "7. 不要编造题目，不要输出无法判断答案的题目。\n\n"
                + "原始文本：\n"
                + reqDTO.getText();
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
                qu.setAnalysis("");
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
                answer.setAnalysis("");
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
            qu.setAnalysis("");
        }

        for (QuAnswerDTO answer : qu.getAnswerList()) {
            answer.setId(null);
            answer.setQuId(null);

            if (StringUtils.isBlank(answer.getImage())) {
                answer.setImage("");
            }
            if (StringUtils.isBlank(answer.getAnalysis())) {
                answer.setAnalysis("");
            }
        }
    }
}