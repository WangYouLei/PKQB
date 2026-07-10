package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pkqb.common.Result;
import pkqb.pojo.entity.NotificationEntity;
import pkqb.service.NotificationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
@Tag(name = "通知管理", description = "通知相关接口")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/list")
    @Operation(summary = "获取通知列表", description = "获取当前用户的通知列表")
    public Result<List<NotificationEntity>> getNotifications(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        List<NotificationEntity> list = notificationService.getNotifications(userId);
        return Result.success(list);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "获取未读数量", description = "获取当前用户的未读通知数量")
    public Result<Map<String, Long>> getUnreadCount(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        long count = notificationService.getUnreadCount(userId);
        return Result.success(Map.of("unreadCount", count));
    }

    @PutMapping("/read/{id}")
    @Operation(summary = "标记已读", description = "标记指定通知为已读")
    public Result<?> markAsRead(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        notificationService.markAsRead(id, userId);
        return Result.success("标记成功");
    }

    @PutMapping("/read-all")
    @Operation(summary = "全部已读", description = "标记当前用户所有通知为已读")
    public Result<?> markAllAsRead(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        notificationService.markAllAsRead(userId);
        return Result.success("标记成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除通知", description = "删除指定通知")
    public Result<?> deleteNotification(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        notificationService.deleteNotification(id, userId);
        return Result.success("删除成功");
    }

    @PostMapping("/clear")
    @Operation(summary = "清空通知", description = "清空当前用户所有通知")
    public Result<?> clearAll(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        notificationService.clearAll(userId);
        return Result.success("清空成功");
    }
}
