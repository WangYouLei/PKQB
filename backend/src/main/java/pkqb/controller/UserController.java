package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import pkqb.common.Result;
import pkqb.service.UserService;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户信息修改接口")
public class UserController {

    private final UserService userService;

    @PutMapping("/username")
    @Operation(summary = "修改用户名", description = "修改用户的昵称/用户名")
    public Result<Void> updateUsername(
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId,
            @Parameter(description = "新用户名") @RequestParam String username) {
        try {
            userService.updateUsername(userId, username);
            return Result.success("用户名修改成功", null);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "修改用户密码")
    public Result<Void> updatePassword(
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId,
            @Parameter(description = "原密码") @RequestParam String oldPassword,
            @Parameter(description = "新密码") @RequestParam String newPassword) {
        try {
            userService.updatePassword(userId, oldPassword, newPassword);
            return Result.success("密码修改成功", null);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            if (e.getMessage().contains("原密码错误")) {
                return Result.error("原密码错误");
            }
            log.error("修改密码失败: {}", e.getMessage());
            return Result.error("修改密码失败");
        }
    }
}
