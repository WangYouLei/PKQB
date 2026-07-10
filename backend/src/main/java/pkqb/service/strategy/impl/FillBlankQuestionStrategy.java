package pkqb.service.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pkqb.enums.RubricQuestionTypeEnum;
import pkqb.service.strategy.QuestionExtractStrategy;

import java.util.regex.Matcher;

@Slf4j
@Component
@Order(2)
public class FillBlankQuestionStrategy implements QuestionExtractStrategy {
    
    @Override
    public boolean canHandle(String questionText, Matcher questionMatcher) {
        if (questionText == null) return false;
        return questionText.contains("___") || questionText.contains("____")
                || questionText.contains("（  ）") || questionText.contains("（）")
                || questionText.contains("[]") || questionText.contains("（ ）");
    }
    
    @Override
    public QuestionExtractResult extract(String questionText, Matcher questionMatcher) {
        if (questionText == null) {
            return null;
        }
        log.debug("[填空题策略] 处理题目: {}", questionText);
        
        QuestionExtractResult result = new QuestionExtractResult();
        result.setQuestion(questionText);
        result.setQuestionType(getQuestionType());
        
        return result;
    }
    
    @Override
    public String getQuestionType() {
        return RubricQuestionTypeEnum.FILL_IN_THE_BLANKS.getCode();
    }
}
