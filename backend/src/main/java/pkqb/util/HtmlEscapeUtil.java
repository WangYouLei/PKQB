package pkqb.util;

import org.springframework.web.util.HtmlUtils;

/**
 * HTML转义工具：仅转义HTML结构字符（& < > " '），保留Unicode数学符号。
 * HtmlUtils.htmlEscape 会把 ° × ÷ ∠ 等符号转成 &deg; &times; &ang; 之类HTML实体，
 * 前端以纯文本展示时会直接露出实体字符串形成乱码，故题目类文本统一使用本工具。
 */
public final class HtmlEscapeUtil {

    private HtmlEscapeUtil() {
    }

    public static String escapeBasic(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** 反转义：用于把历史入库的HTML实体（&deg; 等）恢复为原文再返回前端 */
    public static String unescape(String s) {
        return s == null ? null : HtmlUtils.htmlUnescape(s);
    }
}
