package pkqb.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pkqb.common.Result;
import pkqb.pojo.dto.admin.AdminLoginRequest;
import pkqb.pojo.dto.admin.AdminLoginResponse;
import pkqb.service.AdminAuthService;

/**
 * 管理端认证控制器
 * 提供管理员独立登录接口（不与学生端 /api/auth/login 复用）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Tag(name = "管理端-认证", description = "管理员登录接口")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    @Operation(summary = "管理员登录", description = "管理员账号登录，仅 role=1 的账号可登录")
    public Result<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.success(adminAuthService.login(request));
    }
}
