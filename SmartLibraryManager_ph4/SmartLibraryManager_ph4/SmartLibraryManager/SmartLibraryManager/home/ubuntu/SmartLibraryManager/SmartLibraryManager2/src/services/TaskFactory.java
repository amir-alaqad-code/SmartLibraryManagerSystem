package services;

import javafx.concurrent.Task;
import java.util.function.Supplier;

/** Helper: ينشئ Task لأي عملية ثقيلة بدون تكرار كود */
public class TaskFactory {
    public static <T> Task<T> background(String message, Supplier<T> supplier) {
        return new Task<>() {
            { updateMessage(message); }
            @Override 
            protected T call() throws Exception {
                // ملاحظة: supplier لازم يفتح/يغلق EntityManager داخله
                return supplier.get();
            }
        };
    }
}
