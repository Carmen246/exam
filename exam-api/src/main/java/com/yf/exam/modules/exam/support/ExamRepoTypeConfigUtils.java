package com.yf.exam.modules.exam.support;

import com.alibaba.fastjson.JSON;
import com.yf.exam.modules.exam.dto.ExamRepoDTO;
import com.yf.exam.modules.qu.enums.QuType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ExamRepoTypeConfigUtils {

    private static final List<Integer> QUESTION_TYPE_ORDER = Arrays.asList(
            QuType.RADIO,
            QuType.MULTI,
            QuType.JUDGE,
            QuType.FILL,
            QuType.FILL_PROGRAM,
            QuType.READ_PROGRAM,
            QuType.PROGRAM,
            QuType.FIX_PROGRAM,
            QuType.COMPREHENSIVE);

    private ExamRepoTypeConfigUtils() {
    }

    public static List<ExamRepoDTO.QuestionTypeConfig> normalize(ExamRepoDTO repo) {
        List<ExamRepoDTO.QuestionTypeConfig> source = sourceTypes(repo);
        List<ExamRepoDTO.QuestionTypeConfig> result = new ArrayList<>();

        for (Integer quType : QUESTION_TYPE_ORDER) {
            for (ExamRepoDTO.QuestionTypeConfig item : source) {
                if (item == null || !quType.equals(item.getQuType())) {
                    continue;
                }
                int count = safeNumber(item.getCount());
                if (count <= 0) {
                    continue;
                }

                ExamRepoDTO.QuestionTypeConfig config = new ExamRepoDTO.QuestionTypeConfig();
                config.setQuType(quType);
                config.setCount(count);
                config.setScore(safeNumber(item.getScore()));
                result.add(config);
            }
        }

        return result;
    }

    public static void syncBeforeSave(ExamRepoDTO repo) {
        List<ExamRepoDTO.QuestionTypeConfig> configs = normalize(repo);
        repo.setTypes(configs);
        repo.setTypeConfig(JSON.toJSONString(configs));

        repo.setRadioCount(0);
        repo.setRadioScore(0);
        repo.setMultiCount(0);
        repo.setMultiScore(0);
        repo.setJudgeCount(0);
        repo.setJudgeScore(0);

        for (ExamRepoDTO.QuestionTypeConfig config : configs) {
            if (QuType.RADIO.equals(config.getQuType())) {
                repo.setRadioCount(config.getCount());
                repo.setRadioScore(config.getScore());
            }
            if (QuType.MULTI.equals(config.getQuType())) {
                repo.setMultiCount(config.getCount());
                repo.setMultiScore(config.getScore());
            }
            if (QuType.JUDGE.equals(config.getQuType())) {
                repo.setJudgeCount(config.getCount());
                repo.setJudgeScore(config.getScore());
            }
        }
    }

    public static void fillTypesAfterLoad(ExamRepoDTO repo) {
        repo.setTypes(normalize(repo));
    }

    public static int scoreForType(ExamRepoDTO repo, Integer quType) {
        for (ExamRepoDTO.QuestionTypeConfig config : normalize(repo)) {
            if (quType.equals(config.getQuType())) {
                return safeNumber(config.getScore());
            }
        }
        return 0;
    }

    private static List<ExamRepoDTO.QuestionTypeConfig> sourceTypes(ExamRepoDTO repo) {
        if (repo == null) {
            return new ArrayList<>();
        }
        if (!CollectionUtils.isEmpty(repo.getTypes())) {
            return repo.getTypes();
        }
        if (StringUtils.isNotBlank(repo.getTypeConfig())) {
            List<ExamRepoDTO.QuestionTypeConfig> parsed = JSON.parseArray(
                    repo.getTypeConfig(), ExamRepoDTO.QuestionTypeConfig.class);
            if (!CollectionUtils.isEmpty(parsed)) {
                return parsed;
            }
        }
        return legacyTypes(repo);
    }

    private static List<ExamRepoDTO.QuestionTypeConfig> legacyTypes(ExamRepoDTO repo) {
        List<ExamRepoDTO.QuestionTypeConfig> result = new ArrayList<>();
        addLegacy(result, QuType.RADIO, repo.getRadioCount(), repo.getRadioScore());
        addLegacy(result, QuType.MULTI, repo.getMultiCount(), repo.getMultiScore());
        addLegacy(result, QuType.JUDGE, repo.getJudgeCount(), repo.getJudgeScore());
        return result;
    }

    private static void addLegacy(List<ExamRepoDTO.QuestionTypeConfig> result, Integer quType, Integer count, Integer score) {
        if (safeNumber(count) <= 0) {
            return;
        }

        ExamRepoDTO.QuestionTypeConfig config = new ExamRepoDTO.QuestionTypeConfig();
        config.setQuType(quType);
        config.setCount(safeNumber(count));
        config.setScore(safeNumber(score));
        result.add(config);
    }

    private static int safeNumber(Integer value) {
        return value == null ? 0 : value;
    }
}
