package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pkqb.common.Result;
import pkqb.pojo.dto.LoginRequest;
import pkqb.pojo.dto.LoginResponse;
import pkqb.pojo.dto.RegisterRequest;
import pkqb.service.UserService;

/**
 * 认证控制器
 * 提供用户认证相关的REST API接口，包括用户注册、登录和登出功能
 * 
 * @author pkqb
 * @version 1.0
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户注册、登录接口")
public class AuthController {

    private final UserService userService;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    /**
     * 用户注册
     * 接收用户注册请求，创建新用户账号
     * 
     * @param request 注册请求参数，包含用户名、密码等信息
     * @return 注册结果
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "新用户注册账号")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return Result.success("注册成功", null);
    }

    /**
     * 用户登录
     * 验证用户身份并返回JWT令牌，同时将令牌写入Cookie
     * 
     * @param request 登录请求参数，包含用户名和密码
     * @param response HTTP响应对象，用于设置Cookie
     * @return 登录结果，包含JWT令牌和用户信息
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录并获取JWT令牌")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResponse loginResponse = userService.login(request);
        
        Cookie tokenCookie = new Cookie("token", loginResponse.getToken());
        tokenCookie.setHttpOnly(true);
        tokenCookie.setSecure(false);
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge((int) (jwtExpiration / 1000));
        response.addCookie(tokenCookie);
        
        return Result.success("登录成功", loginResponse);
    }
    
    /**
     * 用户登出
     * 清除用户的登录状态，删除Cookie中的JWT令牌
     * 
     * @param response HTTP响应对象，用于清除Cookie
     * @return 登出结果
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "清除登录状态")
    public Result<Void> logout(HttpServletResponse response) {
        Cookie tokenCookie = new Cookie("token", null);
        tokenCookie.setHttpOnly(true);
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge(0);
        response.addCookie(tokenCookie);
        return Result.success("登出成功", null);
    }
}
