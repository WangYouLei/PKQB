package pkqb.service.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pkqb.enums.RubricQuestionTypeEnum;
import pkqb.service.strategy.QuestionExtractContext;
import pkqb.service.strategy.QuestionExtractStrategy;

import java.util.regex.Matcher;

@Slf4j
@Component
@Order(4)
public class SingleChoiceQuestionStrategy implements QuestionExtractStrategy {
    
    @Override
    public boolean canHandle(String questionText, Matcher questionMatcher) {
        Matcher answerInBrackets = QuestionExtractContext.getAnswerInBracketsPattern().matcher(questionText);
        if (answerInBrackets.matches()) {
            String answerLetters = answerInBrackets.group(1);
            return answerLetters.length() == 1;
        }
        
        Matcher singleAnswer = QuestionExtractContext.getSingleAnswerPattern().matcher(questionText);
        if (singleAnswer.matches() && !questionText.matches("^[A-D][.、].*")) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public QuestionExtractResult extract(String questionText, Matcher questionMatcher) {
        log.debug("[单选题策略] 处理题目: {}", questionText);
        
        QuestionExtractResult result = new QuestionExtractResult();
        String answer = null;
        String cleanedQuestion = questionText;
        
        Matcher answerInBrackets = QuestionExtractContext.getAnswerInBracketsPattern().matcher(questionText);
        if (answerInBrackets.matches()) {
            String answerLetters = answerInBrackets.group(1);
            answer = String.join(",", answerLetters.split(""));
            cleanedQuestion = questionText.replaceAll("\\([A-D]+\\)\\s*$", "").trim();
        } else {
            Matcher singleAnswer = QuestionExtractContext.getSingleAnswerPattern().matcher(questionText);
            if (singleAnswer.matches() && !questionText.matches("^[A-D][.、].*")) {
                answer = singleAnswer.group(1);
                cleanedQuestion = questionText.replaceAll("\\s*[A-D]\\s*$", "").trim();
            }
        }
        
        result.setQuestion(cleanedQuestion);
        result.setAnswer(answer);
        result.setQuestionType(getQuestionType());
        
        return result;
    }
    
    @Override
    public String getQuestionType() {
        return RubricQuestionTypeEnum.SINGLE_CHOICE.getCode();
    }
}
