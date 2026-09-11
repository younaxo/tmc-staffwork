package su.twomc.staffwork.service;

public record OperationResult<T>(boolean success, String messageKey, T value) {
    public static <T> OperationResult<T> success(String messageKey, T value) {
        return new OperationResult<>(true, messageKey, value);
    }

    public static <T> OperationResult<T> failure(String messageKey) {
        return new OperationResult<>(false, messageKey, null);
    }
}
