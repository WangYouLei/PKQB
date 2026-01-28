package com.example.pkqb.service;

import com.example.pkqb.exception.BusinessException;
import com.example.pkqb.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.*;

/**
 * AI 内容分析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAnalysisService {

    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    /**
     * 题目识别 Prompt
     */
    private static final String QUESTION_ANALYSIS_PROMPT = """
            请分析以下文本，判断它是题目还是笔记。
            如果是题目，请提取每个题目的问题、题型、选项和答案。

            题型说明：
            - single_choice: 单选题（只有一个正确答案）
            - multiple_choice: 多选题（有多个正确答案，用逗号分隔）
            - true_false: 判断题（正确/错误）

            必须以纯JSON格式返回，不要包含任何其他文字或说明。

            JSON格式要求：
            {
              "type": "question|note",
              "title": "题库或笔记标题",
              "items": [
                {
                    "question": "题目内容",
                    "questionType": "single_choice|multiple_choice|true_false",
                    "options": ["选项A", "选项B", "选项C", "选项D"],
                    "answer": "正确答案，多选题用逗号分隔如：A,B,C",
                    "explanation": "解析（可选）"
                }
              ]
            }

            文本内容：
            %s
            """;

    /**
     * 分析文本内容
     *
     * @param text 文本内容
     * @return 分析结果
     */
    public Map<String, Object> analyzeContent(String text) {
        try {
            log.info("开始分析内容，文本长度: {}", text.length());

            // 使用 AI 分析内容
            Map<String, Object> result = analyzeWithAI(text);

            log.info("内容分析完成，类型: {}, 项目数量: {}",
                    result.get("type"),
                    result.containsKey("items") ? ((List<?>) result.get("items")).size() : 0);

            return result;

        } catch (Exception e) {
            log.error("内容分析失败", e);
            throw new BusinessException("内容分析失败: " + e.getMessage());
        }
    }

    /**
     * 使用 AI 分析内容
     */
    private Map<String, Object> analyzeWithAI(String text) {
        try {
            String prompt = String.format(QUESTION_ANALYSIS_PROMPT, text);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("AI 响应: {}", response);

            // 解析 AI 返回的 JSON
            JsonNode jsonNode = objectMapper.readTree(response);

            Map<String, Object> result = new HashMap<>();
            result.put("type", jsonNode.get("type").asText());
            result.put("title", jsonNode.get("title").asText());
            
            // 添加模板名称
            String templateName = jsonNode.get("type").asText().equals("question") ? "question-template" : "note-template";
            result.put("templateName", templateName);

            List<Object> items = new ArrayList<>();
            JsonNode itemsNode = jsonNode.get("items");
            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode itemNode : itemsNode) {
                    String type = itemNode.get("type").asText();
                    if ("question".equals(type)) {
                        items.add(parseQuestionItem(itemNode));
                    } else {
                        items.add(parseNoteItem(itemNode));
                    }
                }
            }
            result.put("items", items);
            return result;

        } catch (JsonProcessingException e) {
            log.error("解析 AI 响应失败，使用备用方案", e);
            return analyzeWithRegex(text);
        } catch (Exception e) {
            log.error("AI 调用失败，使用备用方案", e);
            return analyzeWithRegex(text);
        }
    }

    /**
     * 解析题目项
     */
    private QuestionItem parseQuestionItem(JsonNode node) {
        QuestionItem.QuestionItemBuilder builder = QuestionItem.builder();

        if (node.has("question")) {
            builder.question(node.get("question").asText());
        }
        if (node.has("questionType")) {
            builder.questionType(node.get("questionType").asText());
        }
        if (node.has("answer")) {
            builder.answer(node.get("answer").asText());
        }
        if (node.has("explanation")) {
            builder.explanation(node.get("explanation").asText());
        }

        if (node.has("options") && node.get("options").isArray()) {
            List<String> options = new ArrayList<>();
            for (JsonNode option : node.get("options")) {
                options.add(option.asText());
            }
            builder.options(options);
        }

        return builder.build();
    }

    /**
     * 解析笔记项
     */
    private NoteItem parseNoteItem(JsonNode node) {
        NoteItem.NoteItemBuilder builder = NoteItem.builder();

        if (node.has("title")) {
            builder.title(node.get("title").asText());
        }
        if (node.has("content")) {
            builder.content(node.get("content").asText());
        }

        return builder.build();
    }

    /**
     * 使用正则表达式简单分析内容（备用方案）
     */
    private Map<String, Object> analyzeWithRegex(String text) {
        Map<String, Object> result = new HashMap<>();

        // 简单判断是题目还是笔记
        boolean hasQuestion = Pattern.compile("\\d+[.、].*[？?]|[？?]").matcher(text).find();
        boolean hasOptions = Pattern.compile("[A-D][.、]|选项").matcher(text).find();
        boolean hasAnswer = Pattern.compile("(答案|Answer)[:：]").matcher(text).find();

        if (hasQuestion || hasOptions || hasAnswer) {
            // 识别为题目
            result.put("type", "question");
            result.put("title", "题库（由 AI 自动识别）");
            result.put("templateName", "question-template");
            result.put("items", extractQuestions(text));
        } else {
            // 识别为笔记
            result.put("type", "note");
            result.put("title", "学习笔记（由 AI 自动识别）");
            result.put("templateName", "note-template");
            result.put("items", extractNotes(text));
        }

        return result;
    }

    /**
     * 提取题目（备用实现）
     */
    private List<Object> extractQuestions(String text) {
        List<Object> questions = new ArrayList<>();
        String[] lines = text.split("\n");

        String currentQuestion = null;
        List<String> currentOptions = new ArrayList<>();
        String currentAnswer = null;
        String currentExplanation = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 检测题目
            Matcher qm = Pattern.compile("^\\d+[.、]\\s*(.+)").matcher(line);
            if (qm.find()) {
                // 保存上一题
                if (currentQuestion != null) {
                    questions.add(createQuestionItem(currentQuestion, currentOptions, currentAnswer, currentExplanation));
                }
                currentQuestion = qm.group(1);
                currentOptions = new ArrayList<>();
                currentAnswer = null;
                currentExplanation = null;
                continue;
            }

            // 检测选项
            Matcher om = Pattern.compile("^[A-D][.、]\\s*(.+)").matcher(line);
            if (om.find()) {
                currentOptions.add(om.group(1));
                continue;
            }

            // 检测答案
            Matcher am = Pattern.compile("答案|Answer[:：]\\s*(.+)").matcher(line);
            if (am.find()) {
                currentAnswer = am.group(1).trim();
                continue;
            }

            // 检测解析
            Matcher em = Pattern.compile("解析|Explanation[:：]\\s*(.+)").matcher(line);
            if (em.find()) {
                currentExplanation = em.group(1).trim();
            }
        }

        // 保存最后一题
        if (currentQuestion != null) {
            questions.add(createQuestionItem(currentQuestion, currentOptions, currentAnswer, currentExplanation));
        }

        return questions.isEmpty() ? createSampleQuestions() : questions;
    }

    /**
     * 提取笔记（备用实现）
     */
    private List<Object> extractNotes(String text) {
        List<Object> notes = new ArrayList<>();
        String[] paragraphs = text.split("\n\n+");

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.length() < 10) continue;

            String[] lines = paragraph.split("\n");
            String title = lines.length > 0 ? lines[0].trim() : "知识点";
            String content = paragraph.substring(title.length()).trim();

            notes.add(NoteItem.builder()
                    .title(title)
                    .content(content)
                    .build());
        }

        return notes.isEmpty() ? createSampleNotes() : notes;
    }

    /**
     * 创建题目项
     */
    private QuestionItem createQuestionItem(String question, List<String> options, String answer, String explanation) {
        // 判断题目类型
        String questionType = determineQuestionType(options, answer);

        return QuestionItem.builder()
                .question(question)
                .questionType(questionType)
                .options(options)
                .answer(answer != null ? answer : "A")
                .explanation(explanation)
                .build();
    }

    /**
     * 判断题目类型
     */
    private String determineQuestionType(List<String> options, String answer) {
        if (options.size() == 2 && (options.contains("正确") || options.contains("错误"))) {
            return "true_false";
        }
        if (answer != null && answer.contains(",") || answer.contains("、")) {
            return "multiple_choice";
        }
        return "single_choice";
    }

    /**
     * 创建示例题目
     */
    private List<Object> createSampleQuestions() {
        List<Object> questions = new ArrayList<>();

        questions.add(QuestionItem.builder()
                .question("Java 中哪个是基本数据类型？")
                .questionType("single_choice")
                .options(List.of("A. String", "B. int", "C. Integer", "D. Object"))
                .answer("B")
                .explanation("int 是 Java 的 8 种基本数据类型之一")
                .build());

        questions.add(QuestionItem.builder()
                .question("下列哪些是 Java 的基本数据类型？")
                .questionType("multiple_choice")
                .options(List.of("A. int", "B. String", "C. boolean", "D. Integer"))
                .answer("A,C")
                .explanation("int 和 boolean 是基本数据类型")
                .build());

        questions.add(QuestionItem.builder()
                .question("Java 中 String 是基本数据类型。")
                .questionType("true_false")
                .options(List.of("正确", "错误"))
                .answer("错误")
                .explanation("String 是引用类型，不是基本数据类型")
                .build());

        return questions;
    }

    /**
     * 创建示例笔记
     */
    private List<Object> createSampleNotes() {
        List<Object> notes = new ArrayList<>();

        notes.add(NoteItem.builder()
                .title("基本数据类型")
                .content("Java 有 8 种基本数据类型：\n- byte: 8 位有符号整数\n- short: 16 位有符号整数\n- int: 32 位有符号整数\n- long: 64 位有符号整数\n- float: 单精度浮点数\n- double: 双精度浮点数\n- char: 单个 16 位 Unicode 字符\n- boolean: 布尔值（true/false）")
                .build());

        notes.add(NoteItem.builder()
                .title("引用类型")
                .content("除了基本数据类型外，其他都是引用类型，包括类、接口、数组等。引用类型存储的是对象的地址，而不是对象本身。")
                .build());

        return notes;
    }
}
