package pkqb.service.strategy;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public interface QuestionExtractStrategy {
    
    boolean canHandle(String questionText, Matcher questionMatcher);
    
    QuestionExtractResult extract(String questionText, Matcher questionMatcher);
    
    String getQuestionType();
    
    class QuestionExtractResult {
        private String question;
        private String questionType;
        private String answer;
        private List<String> options;
        private String explanation;
        private List<String> calculationSteps;
        
        public QuestionExtractResult() {
            this.options = List.of();
            this.calculationSteps = List.of();
        }
        
        public String getQuestion() {
            return question;
        }
        
        public void setQuestion(String question) {
            this.question = question;
        }
        
        public String getQuestionType() {
            return questionType;
        }
        
        public void setQuestionType(String questionType) {
            this.questionType = questionType;
        }
        
        public String getAnswer() {
            return answer;
        }
        
        public void setAnswer(String answer) {
            this.answer = answer;
        }
        
        public List<String> getOptions() {
            return options;
        }
        
        public void setOptions(List<String> options) {
            this.options = options != null ? options : List.of();
        }
        
        public String getExplanation() {
            return explanation;
        }
        
        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }
        
        public List<String> getCalculationSteps() {
            return calculationSteps;
        }
        
        public void setCalculationSteps(List<String> calculationSteps) {
            this.calculationSteps = calculationSteps != null ? calculationSteps : List.of();
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("question", question != null ? question : "");
            map.put("questionType", questionType != null ? questionType : "");
            map.put("answer", answer != null ? answer : "");
            map.put("explanation", explanation != null ? explanation : "");
            map.put("options", options != null ? options : List.of());
            map.put("calculationSteps", calculationSteps != null ? calculationSteps : List.of());
            return map;
        }
    }
}
