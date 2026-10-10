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
@Order(3)
public class MultipleChoiceQuestionStrategy implements QuestionExtractStrategy {
    
    @Override
    public boolean canHandle(String questionText, Matcher questionMatcher) {
        if (questionText == null) return false;
        boolean isMultipleChoice = questionText.contains("(多选题)")
                || questionText.contains("【多选题]")
                || questionText.contains("[多选题]");
        
        if (isMultipleChoice) {
            return true;
        }
        
        Matcher answerInBrackets = QuestionExtractContext.getAnswerInBracketsPattern().matcher(questionText);
        if (answerInBrackets.matches()) {
            String answerLetters = answerInBrackets.group(1);
            return answerLetters.length() > 1;
        }
        
        Matcher multiAnswerNoBrackets = QuestionExtractContext.getMultiAnswerNoBracketsPattern().matcher(questionText);
        if (multiAnswerNoBrackets.matches()) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public QuestionExtractResult extract(String questionText, Matcher questionMatcher) {
        if (questionText == null) {
            return null;
        }
        log.debug("[多选题策略] 处理题目: {}", questionText);
        
        QuestionExtractResult result = new QuestionExtractResult();
        String answer = null;
        String cleanedQuestion = questionText;
        
        Matcher answerInBrackets = QuestionExtractContext.getAnswerInBracketsPattern().matcher(questionText);
        if (answerInBrackets.matches()) {
            String answerLetters = answerInBrackets.group(1);
            answer = String.join(",", answerLetters.split(""));
            cleanedQuestion = questionText.replaceAll("\\([A-D]+\\)\\s*$", "").trim();
        } else {
            Matcher multiAnswerNoBrackets = QuestionExtractContext.getMultiAnswerNoBracketsPattern().matcher(questionText);
            if (multiAnswerNoBrackets.matches()) {
                String answerLetters = multiAnswerNoBrackets.group(1);
                answer = String.join(",", answerLetters.split(""));
                cleanedQuestion = questionText.replaceAll("\\s*[A-D]{2,}\\s*$", "").trim();
            }
        }
        
        result.setQuestion(cleanedQuestion);
        result.setAnswer(answer);
        result.setQuestionType(getQuestionType());
        
        return result;
    }
    
    @Override
    public String getQuestionType() {
        return RubricQuestionTypeEnum.MULTIPLE_CHOICE.getCode();
    }
}
