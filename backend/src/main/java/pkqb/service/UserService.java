package pkqb.service;

import pkqb.pojo.dto.LoginRequest;
import pkqb.pojo.dto.LoginResponse;
import pkqb.pojo.dto.RegisterRequest;

public interface UserService {

    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    String updateAvatar(Long userId, String objectKey);

    void updateUsername(Long userId, String username);

    void updatePassword(Long userId, String oldPassword, String newPassword);
}
