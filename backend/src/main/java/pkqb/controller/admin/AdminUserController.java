package pkqb.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import pkqb.common.Result;
import pkqb.pojo.dto.admin.AdminResetPasswordRequest;
import pkqb.pojo.dto.admin.AdminUserCreateRequest;
import pkqb.pojo.dto.admin.AdminUserQueryRequest;
import pkqb.pojo.dto.admin.AdminUserUpdateRequest;
import pkqb.pojo.dto.admin.AdminUserVO;
import pkqb.service.AdminUserService;

/**
 * 管理端账号管理控制器
 * 提供账号分页查询、新增、修改、删除（逻辑删除）、重置密码
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
@Tag(name = "管理端-账号管理", description = "账号增删改查、重置密码")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping("/page")
    @Operation(summary = "分页查询账号", description = "支持按用户名/学号关键字、班级、角色筛选")
    public Result<IPage<AdminUserVO>> page(@Valid AdminUserQueryRequest request) {
        return Result.success(adminUserService.page(request));
    }

    @PostMapping
    @Operation(summary = "新增账号", description = "管理员创建账号并设置初始密码")
    public Result<Void> create(@Valid @RequestBody AdminUserCreateRequest request) {
        adminUserService.create(request);
        return Result.success("新增成功", null);
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改账号", description = "修改用户名、学号、班级、角色")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest request) {
        adminUserService.update(id, request);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除账号", description = "逻辑删除（置 deleted=1）")
    public Result<Void> delete(@PathVariable Long id) {
        adminUserService.delete(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "重置密码", description = "管理员重置指定账号密码")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody AdminResetPasswordRequest request) {
        adminUserService.resetPassword(id, request.getNewPassword());
        return Result.success("密码重置成功", null);
    }
}
