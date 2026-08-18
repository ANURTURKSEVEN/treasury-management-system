package com.gtech.treasury.model;

/**
 * error_log tablosundaki bir hata kaydını temsil eder.
 */
public class ErrorLog {

    private int id;
    private String errorType;
    private String errorSource;    // hatanın oluştuğu metot : satır
    private String errorCaller;    // o metodun çağrıldığı yer : satır
    private String errorMessage;
    private String username;
    private String createdAt;

    public ErrorLog(int id, String errorType, String errorSource, String errorCaller,
                    String errorMessage, String username, String createdAt) {
        this.id = id;
        this.errorType = errorType;
        this.errorSource = errorSource;
        this.errorCaller = errorCaller;
        this.errorMessage = errorMessage;
        this.username = username;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getErrorType() { return errorType; }
    public String getErrorSource() { return errorSource; }
    public String getErrorCaller() { return errorCaller; }
    public String getErrorMessage() { return errorMessage; }
    public String getUsername() { return username; }
    public String getCreatedAt() { return createdAt; }
}
