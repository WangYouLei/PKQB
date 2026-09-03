package pkqb.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import pkqb.mapper.UserMapper;
import pkqb.pojo.entity.UserEntity;

/**
 * 管理端权限拦截器
 * 校验当前登录用户是否为管理员（role=1）
 * 需在 JwtInterceptor 之后执行，依赖其设置的 userId
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
            response.getWriter().flush();
            return false;
        }

        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getRole() == null || user.getRole() != 1) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无管理员权限\"}");
            response.getWriter().flush();
            return false;
        }

        return true;
    }
}
