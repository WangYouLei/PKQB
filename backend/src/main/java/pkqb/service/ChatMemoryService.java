package pkqb.service;

public interface ChatMemoryService {
    
    String SUMMARY_PREFIX = "chat_summary:";
    int WINDOW_SIZE = 10;
    int COMPRESS_THRESHOLD = 20;

    void compressIfNeeded(String userId, String sessionId, String type);

    String getSummary(String userId, String sessionId, String type);

    void clearSummary(String userId, String sessionId, String type);
}
