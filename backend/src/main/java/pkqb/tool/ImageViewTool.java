package pkqb.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;
import pkqb.service.MinioService;

import java.util.function.BiFunction;

@Slf4j
public class ImageViewTool implements BiFunction<String, ToolContext, String> {

    private final MinioService minioService;
    private final ChatModel visionChatModel;

    public ImageViewTool(MinioService minioService, ChatModel visionChatModel) {
        this.minioService = minioService;
        this.visionChatModel = visionChatModel;
    }

    @Override
    public String apply(String imageUrl, ToolContext toolContext) {
        log.info("[ImageViewTool] 查看图片: {}", imageUrl);
        try {
            String objectKey = extractObjectKey(imageUrl);
            if (objectKey == null) {
                return "无法解析图片地址";
            }

            byte[] imageBytes = minioService.getFile(objectKey);
            if (imageBytes == null || imageBytes.length == 0) {
                return "图片不存在或无法获取";
            }

            String mimeType = guessMimeType(objectKey);
            ChatClient chatClient = ChatClient.builder(visionChatModel).build();
            String description = chatClient.prompt()
                    .user(u -> {
                        u.text("请详细描述这张图片的内容，特别关注：1.图片中是否有题目文字 2.图片是否是某个选项的内容 3.图片中的数学公式或图形");
                        u.media(new Media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes)));
                    })
                    .call()
                    .content();

            log.info("[ImageViewTool] 图片分析完成，描述长度: {}", description != null ? description.length() : 0);
            return description != null ? description : "无法识别图片内容";
        } catch (Exception e) {
            log.error("[ImageViewTool] 查看图片失败: {}", e.getMessage());
            return "查看图片失败: " + e.getMessage();
        }
    }

    private String extractObjectKey(String url) {
        if (url == null || url.isEmpty()) return null;
        int bucketEnd = url.indexOf("/", url.indexOf("//") + 2);
        if (bucketEnd < 0) return null;
        String path = url.substring(bucketEnd + 1);
        int slashIdx = path.indexOf("/");
        if (slashIdx < 0) return null;
        return path.substring(slashIdx + 1);
    }

    private String guessMimeType(String objectKey) {
        if (objectKey.endsWith(".png")) return "image/png";
        if (objectKey.endsWith(".jpg") || objectKey.endsWith(".jpeg")) return "image/jpeg";
        if (objectKey.endsWith(".gif")) return "image/gif";
        if (objectKey.endsWith(".webp")) return "image/webp";
        return "image/png";
    }

    public static FunctionToolCallback createTool(MinioService minioService, ChatModel visionChatModel) {
        ImageViewTool tool = new ImageViewTool(minioService, visionChatModel);
        return FunctionToolCallback.builder("image_view", tool)
                .description("查看指定图片的详细内容。当需要确认图片属于哪道题、哪个选项，或需要理解图片内容时调用此工具。参数imageUrl是图片的访问地址。")
                .inputType(String.class)
                .build();
    }
}
