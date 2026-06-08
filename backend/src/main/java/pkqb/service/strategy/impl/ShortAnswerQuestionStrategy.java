package pkqb.service.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pkqb.enums.RubricQuestionTypeEnum;
import pkqb.service.strategy.QuestionExtractStrategy;

import java.util.regex.Matcher;

@Slf4j
@Component
@Order(5)
public class ShortAnswerQuestionStrategy implements QuestionExtractStrategy {
    
    @Override
    public boolean canHandle(String questionText, Matcher questionMatcher) {
        if (questionText == null) return false;
        return questionText.contains("简答题") || questionText.contains("论述题")
                || questionText.contains("问答");
    }
    
    @Override
    public QuestionExtractResult extract(String questionText, Matcher questionMatcher) {
        if (questionText == null) {
            return null;
        }
        log.debug("[简答题策略] 处理题目: {}", questionText);
        
        QuestionExtractResult result = new QuestionExtractResult();
        result.setQuestion(questionText);
        result.setQuestionType(getQuestionType());
        
        return result;
    }
    
    @Override
    public String getQuestionType() {
        return RubricQuestionTypeEnum.SHORT_ANSWER.getCode();
    }
}
