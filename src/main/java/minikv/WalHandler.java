package minikv;

public interface WalHandler {
    void onPut(String key, String value);
    void onDelete(String key);
}

