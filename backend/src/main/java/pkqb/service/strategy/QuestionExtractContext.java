package pkqb.service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pkqb.enums.RubricQuestionTypeEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class QuestionExtractContext {
    
    private static final Pattern QUESTION_NUMBER_PATTERN = Pattern.compile("^(\\d+|[（(]\\d+[）)]|[（(][A-Z][）)]|\\([A-Z]\\))([.、.．])\\s*(.+)");
    private static final Pattern ANSWER_IN_BRACKETS = Pattern.compile(".*\\(([A-D]+)\\)\\s*$");
    private static final Pattern MULTI_ANSWER_NO_BRACKETS = Pattern.compile(".*[？?]?\\s*([A-D]{2,})\\s*$");
    private static final Pattern SINGLE_ANSWER = Pattern.compile(".*[？?]?\\s*([A-D])\\s*$");
    private static final Pattern OPTION_PATTERN = Pattern.compile("^([A-D])[.、、]\\s*(.+)");
    private static final Pattern MULTI_OPTION_LINE = Pattern.compile("([A-D])[.、]\\s*([^A-D]+?)(?=\\s+[A-D][.、]|$)");
    private static final Pattern ANSWER_LINE = Pattern.compile("(答案|Answer|参考答案)[:：]\\s*(.+)");
    private static final Pattern EXPLANATION_LINE = Pattern.compile("(解析|Explanation|解析如下)[:：]\\s*(.+)");
    private static final Pattern CALCULATION_STEP = Pattern.compile("(步骤\\d*|第\\d*步)[:：.]?\\s*(.+)");
    
    private final List<QuestionExtractStrategy> strategies;
    
    public QuestionExtractContext(List<QuestionExtractStrategy> strategies) {
        this.strategies = strategies;
        log.info("[题目提取策略] 已加载 {} 个策略", strategies.size());
    }
    
    public List<Map<String, Object>> extractQuestions(String text) {
        log.info("[题目提取] 开始提取题目，文本长度={}", text != null ? text.length() : 0);
        List<Map<String, Object>> questions = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return questions;
        }
        
        String[] lines = text.split("\n");
        
        QuestionExtractStrategy.QuestionExtractResult currentResult = null;
        List<String> currentOptions = new ArrayList<>();
        String currentAnswer = null;
        String currentExplanation = null;
        List<String> calculationSteps = new ArrayList<>();
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                if (currentResult != null && currentResult.getQuestion() != null) {
                    addQuestionToList(questions, currentResult, currentOptions, currentAnswer, currentExplanation, calculationSteps);
                    currentResult = null;
                    currentOptions = new ArrayList<>();
                    currentAnswer = null;
                    currentExplanation = null;
                    calculationSteps = new ArrayList<>();
                }
                continue;
            }
            
            Matcher qm = QUESTION_NUMBER_PATTERN.matcher(line);
            if (qm.find()) {
                if (currentResult != null && currentResult.getQuestion() != null) {
                    addQuestionToList(questions, currentResult, currentOptions, currentAnswer, currentExplanation, calculationSteps);
                }
                
                String questionText = qm.group(3);
                currentOptions = new ArrayList<>();
                currentAnswer = null;
                currentExplanation = null;
                calculationSteps = new ArrayList<>();
                
                currentResult = extractQuestionWithStrategy(questionText, qm);
                continue;
            }
            
            Matcher om = OPTION_PATTERN.matcher(line);
            if (om.find()) {
                String optionLetter = om.group(1);
                String optionText = om.group(2).trim();
                currentOptions.add(optionLetter + ". " + optionText);
                continue;
            }
            
            Matcher multiOptionLine = MULTI_OPTION_LINE.matcher(line);
            if (multiOptionLine.find()) {
                Matcher m = MULTI_OPTION_LINE.matcher(line);
                while (m.find()) {
                    String optionLetter = m.group(1);
                    String optionText = m.group(2).trim();
                    if (!optionText.isEmpty()) {
                        currentOptions.add(optionLetter + ". " + optionText);
                    }
                }
                continue;
            }
            
            Matcher am = ANSWER_LINE.matcher(line);
            if (am.find()) {
                currentAnswer = am.group(2).trim();
                continue;
            }
            
            Matcher em = EXPLANATION_LINE.matcher(line);
            if (em.find()) {
                currentExplanation = em.group(2).trim();
                continue;
            }
            
            Matcher cs = CALCULATION_STEP.matcher(line);
            if (cs.find()) {
                calculationSteps.add(cs.group(2).trim());
            }
        }
        
        if (currentResult != null && currentResult.getQuestion() != null) {
            addQuestionToList(questions, currentResult, currentOptions, currentAnswer, currentExplanation, calculationSteps);
        }
        
        log.info("[题目提取] 提取完成，共提取 {} 道题目", questions.size());
        return questions;
    }
    
    private QuestionExtractStrategy.QuestionExtractResult extractQuestionWithStrategy(String questionText, Matcher questionMatcher) {
        for (QuestionExtractStrategy strategy : strategies) {
            if (strategy.canHandle(questionText, questionMatcher)) {
                log.debug("[题目提取] 使用策略 {} 处理题目", strategy.getClass().getSimpleName());
                QuestionExtractStrategy.QuestionExtractResult result = strategy.extract(questionText, questionMatcher);
                if (result != null) {
                    return result;
                }
            }
        }
        
        log.debug("[题目提取] 无匹配策略，使用默认策略");
        QuestionExtractStrategy.QuestionExtractResult defaultResult = new QuestionExtractStrategy.QuestionExtractResult();
        defaultResult.setQuestion(questionText);
        defaultResult.setQuestionType(RubricQuestionTypeEnum.SINGLE_CHOICE.getCode());
        return defaultResult;
    }
    
    private void addQuestionToList(List<Map<String, Object>> questions, QuestionExtractStrategy.QuestionExtractResult result,
                                    List<String> currentOptions, String currentAnswer, 
                                    String currentExplanation, List<String> calculationSteps) {
        Map<String, Object> questionMap = result.toMap();
        
        if (currentAnswer != null && !currentAnswer.isEmpty()) {
            questionMap.put("answer", currentAnswer);
        }
        
        if (currentExplanation != null && !currentExplanation.isEmpty()) {
            questionMap.put("explanation", currentExplanation);
        }
        
        if (currentOptions != null && !currentOptions.isEmpty()) {
            questionMap.put("options", currentOptions);
        }
        
        String questionType = result.getQuestionType();
        if (RubricQuestionTypeEnum.CALCULATION.getCode().equals(questionType) && !calculationSteps.isEmpty()) {
            questionMap.put("calculationSteps", calculationSteps);
        }
        
        questions.add(questionMap);
    }
    
    public static Pattern getQuestionNumberPattern() {
        return QUESTION_NUMBER_PATTERN;
    }
    
    public static Pattern getAnswerInBracketsPattern() {
        return ANSWER_IN_BRACKETS;
    }
    
    public static Pattern getMultiAnswerNoBracketsPattern() {
        return MULTI_ANSWER_NO_BRACKETS;
    }
    
    public static Pattern getSingleAnswerPattern() {
        return SINGLE_ANSWER;
    }
}
