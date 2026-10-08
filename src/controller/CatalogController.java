package controller;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

import dao.DtcDao;
import model.Dtc;
import model.DtcInfo;

/**
 * Controller cho UC10 (Tra cứu mô tả DTC). Truy vấn CSDL chạy trong SwingWorker
 * để giao diện không bị treo; kết quả trả về trên luồng giao diện.
 */
public class CatalogController {

    private final DtcDao dtcDao;

    public CatalogController(DtcDao dtcDao) {
        this.dtcDao = dtcDao;
    }

    public void search(String keyword, Consumer<List<Dtc>> onSuccess, Consumer<String> onError) {
        new SwingWorker<List<Dtc>, Void>() {
            @Override
            protected List<Dtc> doInBackground() throws Exception {
                return dtcDao.search(keyword);
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException e) {
                    onError.accept(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    /** Thông tin chi tiết một mã (mô tả, nguồn, luật liên quan, số lần gặp) đọc trên luồng nền. */
    public void info(String code, Consumer<DtcInfo> onSuccess, Consumer<String> onError) {
        new SwingWorker<DtcInfo, Void>() {
            @Override
            protected DtcInfo doInBackground() throws Exception {
                return dtcDao.info(code);
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException e) {
                    onError.accept(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }
}
