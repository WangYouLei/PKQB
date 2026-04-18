package pkqb.service.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pkqb.enums.RubricQuestionTypeEnum;
import pkqb.service.strategy.QuestionExtractStrategy;

import java.util.regex.Matcher;

@Slf4j
@Component
@Order(1)
public class TrueFalseQuestionStrategy implements QuestionExtractStrategy {
    
    @Override
    public boolean canHandle(String questionText, Matcher questionMatcher) {
        return questionText.endsWith("√") || questionText.endsWith("×")
                || questionText.endsWith("对") || questionText.endsWith("错")
                || questionText.endsWith("正确") || questionText.endsWith("错误");
    }
    
    @Override
    public QuestionExtractResult extract(String questionText, Matcher questionMatcher) {
        log.debug("[判断题策略] 处理题目: {}", questionText);
        
        QuestionExtractResult result = new QuestionExtractResult();
        String answer = "错误";
        
        if (questionText.endsWith("√") || questionText.endsWith("对") || questionText.endsWith("正确")) {
            answer = "正确";
        }
        
        String cleanedQuestion = questionText.replaceAll("[√×对错正确错误\\s]+$", "").trim();
        result.setQuestion(cleanedQuestion);
        result.setAnswer(answer);
        result.setQuestionType(getQuestionType());
        
        return result;
    }
    
    @Override
    public String getQuestionType() {
        return RubricQuestionTypeEnum.TRUE_FALSE.getCode();
    }
}
