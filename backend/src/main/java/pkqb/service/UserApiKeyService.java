package pkqb.service;

import pkqb.enums.ApiKeyMode;

public interface UserApiKeyService {

    void saveApiKey(Long userId, String apiKey);

    void deleteApiKey(Long userId);

    String getPlainApiKey(Long userId);

    ApiKeyMode getApiKeyMode(Long userId);

    boolean hasUserOwnApiKey(Long userId);

    void saveModel(Long userId, String model);

    String getModel(Long userId);
}
