package controller;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Chạy việc chậm (truy vấn CSDL) trên luồng nền, rồi trả kết quả về luồng giao diện.
 * Dùng chung cho các controller để giao diện không bị treo.
 */
final class Async {

    // Luồng nền (daemon) để không chặn việc thoát ứng dụng
    private static final ExecutorService SERIAL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "db-serial");
        t.setDaemon(true);
        return t;
    });

    private Async() {
    }

    static <T> void run(Callable<T> job, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return job.call();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException e) {
                    onError.accept(e.getCause() == null ? e : e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    /**
     * Như run(), nhưng các việc được xếp hàng và chạy lần lượt trên một luồng duy nhất.
     * Dùng cho thao tác ghi CSDL cần giữ đúng thứ tự người dùng bấm.
     */
    static <T> void runSerial(Callable<T> job, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        SERIAL.execute(() -> {
            try {
                T result = job.call();
                SwingUtilities.invokeLater(() -> onSuccess.accept(result));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> onError.accept(e));
            }
        });
    }

    /** Lấy câu thông báo ngắn gọn từ một lỗi. */
    static String message(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
