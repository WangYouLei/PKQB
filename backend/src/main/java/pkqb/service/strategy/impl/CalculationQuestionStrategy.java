package pkqb.service.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pkqb.enums.RubricQuestionTypeEnum;
import pkqb.service.strategy.QuestionExtractStrategy;

import java.util.regex.Matcher;

@Slf4j
@Component
@Order(6)
public class CalculationQuestionStrategy implements QuestionExtractStrategy {
    
    @Override
    public boolean canHandle(String questionText, Matcher questionMatcher) {
        if (questionText == null) return false;
        return questionText.contains("计算题") || questionText.contains("应用题");
    }
    
    @Override
    public QuestionExtractResult extract(String questionText, Matcher questionMatcher) {
        if (questionText == null) {
            return null;
        }
        log.debug("[计算题策略] 处理题目: {}", questionText);
        
        QuestionExtractResult result = new QuestionExtractResult();
        result.setQuestion(questionText);
        result.setQuestionType(getQuestionType());
        
        return result;
    }
    
    @Override
    public String getQuestionType() {
        return RubricQuestionTypeEnum.CALCULATION.getCode();
    }
}
