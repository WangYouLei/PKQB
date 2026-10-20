package pkqb.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将AI输出中的LaTeX数学公式转换为Unicode数学符号。
 * 前端以纯文本展示题目内容，模型返回的 $\angle 1 = \angle 3$、180^\circ 等LaTeX语法
 * 会原样暴露给用户形成"乱码"，此工具统一转换为 ∠1 = ∠3、180°。
 */
public final class LatexMathCleaner {

    private LatexMathCleaner() {
    }

    /** LaTeX命令 → Unicode符号（替换时带(?![a-zA-Z])边界，避免\le误伤\left） */
    private static final Map<String, String> SYMBOLS = new LinkedHashMap<>();

    static {
        SYMBOLS.put("\\angle", "∠");
        SYMBOLS.put("\\triangle", "△");
        SYMBOLS.put("\\circ", "°");
        SYMBOLS.put("\\degree", "°");
        SYMBOLS.put("\\times", "×");
        SYMBOLS.put("\\div", "÷");
        SYMBOLS.put("\\pm", "±");
        SYMBOLS.put("\\mp", "∓");
        SYMBOLS.put("\\cdot", "·");
        SYMBOLS.put("\\leq", "≤");
        SYMBOLS.put("\\le", "≤");
        SYMBOLS.put("\\ll", "≪");
        SYMBOLS.put("\\geq", "≥");
        SYMBOLS.put("\\ge", "≥");
        SYMBOLS.put("\\gg", "≫");
        SYMBOLS.put("\\neq", "≠");
        SYMBOLS.put("\\ne", "≠");
        SYMBOLS.put("\\approx", "≈");
        SYMBOLS.put("\\cong", "≅");
        SYMBOLS.put("\\equiv", "≡");
        SYMBOLS.put("\\sim", "∼");
        SYMBOLS.put("\\propto", "∝");
        SYMBOLS.put("\\infty", "∞");
        SYMBOLS.put("\\perp", "⊥");
        SYMBOLS.put("\\parallel", "∥");
        SYMBOLS.put("\\because", "∵");
        SYMBOLS.put("\\therefore", "∴");
        SYMBOLS.put("\\odot", "⊙");
        SYMBOLS.put("\\otimes", "⊗");
        SYMBOLS.put("\\oplus", "⊕");
        SYMBOLS.put("\\in", "∈");
        SYMBOLS.put("\\notin", "∉");
        SYMBOLS.put("\\subset", "⊂");
        SYMBOLS.put("\\subseteq", "⊆");
        SYMBOLS.put("\\supset", "⊃");
        SYMBOLS.put("\\supseteq", "⊇");
        SYMBOLS.put("\\cup", "∪");
        SYMBOLS.put("\\cap", "∩");
        SYMBOLS.put("\\emptyset", "∅");
        SYMBOLS.put("\\varnothing", "∅");
        SYMBOLS.put("\\forall", "∀");
        SYMBOLS.put("\\exists", "∃");
        SYMBOLS.put("\\to", "→");
        SYMBOLS.put("\\rightarrow", "→");
        SYMBOLS.put("\\longrightarrow", "→");
        SYMBOLS.put("\\leftarrow", "←");
        SYMBOLS.put("\\longleftarrow", "←");
        SYMBOLS.put("\\Rightarrow", "⇒");
        SYMBOLS.put("\\Leftrightarrow", "⇔");
        SYMBOLS.put("\\leftrightarrow", "↔");
        SYMBOLS.put("\\cdots", "…");
        SYMBOLS.put("\\ldots", "…");
        SYMBOLS.put("\\dots", "…");
        SYMBOLS.put("\\vdots", "⋮");
        SYMBOLS.put("\\ddots", "⋱");
        // 希腊字母
        SYMBOLS.put("\\alpha", "α");
        SYMBOLS.put("\\beta", "β");
        SYMBOLS.put("\\gamma", "γ");
        SYMBOLS.put("\\delta", "δ");
        SYMBOLS.put("\\epsilon", "ε");
        SYMBOLS.put("\\varepsilon", "ε");
        SYMBOLS.put("\\zeta", "ζ");
        SYMBOLS.put("\\eta", "η");
        SYMBOLS.put("\\theta", "θ");
        SYMBOLS.put("\\vartheta", "θ");
        SYMBOLS.put("\\iota", "ι");
        SYMBOLS.put("\\kappa", "κ");
        SYMBOLS.put("\\lambda", "λ");
        SYMBOLS.put("\\mu", "μ");
        SYMBOLS.put("\\nu", "ν");
        SYMBOLS.put("\\xi", "ξ");
        SYMBOLS.put("\\pi", "π");
        SYMBOLS.put("\\rho", "ρ");
        SYMBOLS.put("\\sigma", "σ");
        SYMBOLS.put("\\tau", "τ");
        SYMBOLS.put("\\upsilon", "υ");
        SYMBOLS.put("\\phi", "φ");
        SYMBOLS.put("\\varphi", "φ");
        SYMBOLS.put("\\chi", "χ");
        SYMBOLS.put("\\psi", "ψ");
        SYMBOLS.put("\\omega", "ω");
        SYMBOLS.put("\\Gamma", "Γ");
        SYMBOLS.put("\\Delta", "Δ");
        SYMBOLS.put("\\Theta", "Θ");
        SYMBOLS.put("\\Lambda", "Λ");
        SYMBOLS.put("\\Xi", "Ξ");
        SYMBOLS.put("\\Pi", "Π");
        SYMBOLS.put("\\Sigma", "Σ");
        SYMBOLS.put("\\Phi", "Φ");
        SYMBOLS.put("\\Psi", "Ψ");
        SYMBOLS.put("\\Omega", "Ω");
    }

    /** 简单操作数：纯数字/字母/小数点，分数转换时无需加括号 */
    private static final Pattern SIMPLE_OPERAND = Pattern.compile("[0-9a-zA-Z.．]{1,6}");
    private static final Pattern FRACTION = Pattern.compile("\\\\[dt]?frac\\s*\\{([^{}]*)\\}\\s*\\{([^{}]*)\\}");
    private static final Pattern SQRT = Pattern.compile("\\\\sqrt\\s*\\{([^{}]*)\\}");
    private static final Pattern NTH_ROOT = Pattern.compile("\\\\sqrt\\s*\\[(\\d+)\\]\\s*\\{([^{}]*)\\}");

    public static String clean(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 没有任何LaTeX痕迹时原样返回，避免误伤普通文本
        if (!text.contains("\\") && !text.contains("$") && !text.contains("^") && !text.contains("_")) {
            return text;
        }
        String s = text;

        // 1. 去掉公式定界符 $$..$$、\[..\]、\(..\)、$..$（保留内部内容）
        s = s.replaceAll("\\$\\$(.+?)\\$\\$", "$1");
        s = s.replaceAll("\\\\\\[(.+?)\\\\\\]", "$1");
        s = s.replaceAll("\\\\\\((.+?)\\\\\\)", "$1");
        s = s.replaceAll("\\$(.+?)\\$", "$1");

        // 2. 度数上标：^\circ、^{\circ}、^\degree → °
        s = s.replaceAll("\\^\\s*\\{?\\\\(?:circ|degree)\\}?", "°");

        // 3. \text{..}、\mathrm{..}等包裹 → 内部内容
        s = s.replaceAll("\\\\(?:text|mathrm|mathbf|mathit|mathsf|operatorname)\\s*\\{([^{}]*)\\}", "$1");

        // 4. 分数 \frac{a}{b} → a/b（复杂时加括号）
        s = replaceAll(FRACTION.matcher(s), m -> {
            String a = m.group(1).trim();
            String b = m.group(2).trim();
            boolean simple = SIMPLE_OPERAND.matcher(a).matches() && SIMPLE_OPERAND.matcher(b).matches();
            return simple ? a + "/" + b : "(" + a + ")/(" + b + ")";
        });

        // 5. 根号 \sqrt{x} → √x，\sqrt[n]{x} → n√(x)
        s = replaceAll(NTH_ROOT.matcher(s), m -> m.group(1) + "√(" + m.group(2).trim() + ")");
        s = replaceAll(SQRT.matcher(s), m -> {
            String a = m.group(1).trim();
            return SIMPLE_OPERAND.matcher(a).matches() ? "√" + a : "√(" + a + ")";
        });

        // 6. \overline{AB}、\widehat{AB}等 → AB
        s = s.replaceAll("\\\\(?:overline|widehat|widetilde|overrightarrow|overleftrightarrow|hat|vec)\\s*\\{([^{}]*)\\}", "$1");

        // 7. 上标/下标转Unicode（x^2 → x²、2^{10} → 2¹⁰、a_1 → a₁）
        s = replaceBracedScript(s, true);
        s = replaceSingleScript(s, true);
        s = replaceBracedScript(s, false);
        s = replaceSingleScript(s, false);

        // 8. 符号命令替换
        for (Map.Entry<String, String> e : SYMBOLS.entrySet()) {
            if (s.contains(e.getKey())) {
                s = s.replaceAll(Pattern.quote(e.getKey()) + "(?![a-zA-Z])", Matcher.quoteReplacement(e.getValue()));
            }
        }

        // 9. 括号缩放、空白命令、转义字符
        s = s.replaceAll("\\\\(?:left|right|big|Big|bigg|Bigg|displaystyle|limits)\\b", " ");
        s = s.replaceAll("\\\\(?:quad|qquad)", " ");
        s = s.replaceAll("\\\\[,;:!]", " ");
        s = s.replaceAll("\\\\ ", " ");
        s = s.replaceAll("\\\\%", "%");
        s = s.replaceAll("\\\\_", "_");
        s = s.replaceAll("\\\\&", "&");
        s = s.replaceAll("\\\\#", "#");

        // 10. 残留命令去掉反斜杠（如\sin → sin）
        s = s.replaceAll("\\\\([a-zA-Z]+)", "$1");

        // 11. 残留定界符与花括号
        s = s.replace("$", "");
        s = s.replace("{", "").replace("}", "");

        // 12. 几何符号后多余空格（∠ 1 → ∠1）
        s = s.replaceAll("(?<=[∠△])\\s+", "");

        // 13. 合并连续空格
        return s.replaceAll("\\s{2,}", " ").trim();
    }

    private interface Replacement {
        String replace(Matcher m);
    }

    private static String replaceAll(Matcher m, Replacement fn) {
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(fn.replace(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 带花括号的上/下标：x^{2} → x²，无法映射时保留原文 */
    private static String replaceBracedScript(String s, boolean sup) {
        char op = sup ? '^' : '_';
        Pattern p = Pattern.compile("\\" + op + "\\s*\\{([^{}]+)\\}");
        return replaceAll(p.matcher(s), m -> {
            String converted = sup ? toSuperscript(m.group(1)) : toSubscript(m.group(1));
            return converted != null ? converted : op + m.group(1);
        });
    }

    /** 不带花括号的上/下标：x^2 → x²、a_1 → a₁，无法映射时保持原样 */
    private static String replaceSingleScript(String s, boolean sup) {
        char op = sup ? '^' : '_';
        Pattern p = Pattern.compile("\\" + op + "\\s*([0-9]+|[a-zA-Z()+=-])");
        return replaceAll(p.matcher(s), m -> {
            String converted = sup ? toSuperscript(m.group(1)) : toSubscript(m.group(1));
            return converted != null ? converted : m.group(0);
        });
    }

    private static String toSuperscript(String s) {
        return mapChars(s, LatexMathCleaner::supChar);
    }

    private static String toSubscript(String s) {
        return mapChars(s, LatexMathCleaner::subChar);
    }

    private interface CharMap {
        String map(char c);
    }

    private static String mapChars(String s, CharMap fn) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            String m = fn.map(c);
            if (m == null) {
                return null;
            }
            sb.append(m);
        }
        return sb.toString();
    }

    private static String supChar(char c) {
        switch (c) {
            case '+': return "⁺";
            case '-': return "⁻";
            case '=': return "⁼";
            case '(': return "⁽";
            case ')': return "⁾";
            case 'n': case 'N': return "ⁿ";
            case 'i': case 'I': return "ⁱ";
            default:
                return (c >= '0' && c <= '9') ? String.valueOf("⁰¹²³⁴⁵⁶⁷⁸⁹".charAt(c - '0')) : null;
        }
    }

    private static String subChar(char c) {
        switch (c) {
            case '+': return "₊";
            case '-': return "₋";
            case '=': return "₌";
            case '(': return "₍";
            case ')': return "₎";
            case 'a': return "ₐ";
            case 'e': return "ₑ";
            case 'h': return "ₕ";
            case 'i': return "ᵢ";
            case 'j': return "ⱼ";
            case 'k': return "ₖ";
            case 'l': return "ₗ";
            case 'm': return "ₘ";
            case 'n': return "ₙ";
            case 'o': return "ₒ";
            case 'p': return "ₚ";
            case 'r': return "ᵣ";
            case 's': return "ₛ";
            case 't': return "ₜ";
            case 'u': return "ᵤ";
            case 'v': return "ᵥ";
            case 'x': return "ₓ";
            default:
                return (c >= '0' && c <= '9') ? String.valueOf("₀₁₂₃₄₅₆₇₈₉".charAt(c - '0')) : null;
        }
    }
}
