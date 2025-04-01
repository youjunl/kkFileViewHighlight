package cn.keking.service;

import cn.keking.model.FileAttribute;
import cn.keking.model.FileType;
import cn.keking.service.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ui.ExtendedModelMap;

import javax.annotation.PostConstruct;
import java.util.concurrent.*;

@Service
public class FileConvertQueueTask {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final FilePreviewFactory previewFactory;
    private final CacheService cacheService;
    private final FileHandlerService fileHandlerService;

    private static final ExecutorService threadPool = new ThreadPoolExecutor(
            4,
            8,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new FileConvertThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy()
    );


    public FileConvertQueueTask(FilePreviewFactory previewFactory, CacheService cacheService, FileHandlerService fileHandlerService) {
        this.previewFactory = previewFactory;
        this.cacheService = cacheService;
        this.fileHandlerService = fileHandlerService;
    }

    @PostConstruct
    public void startTask() {
        threadPool.submit(new QueueConsumerTask());
        logger.info(">>>>>文件转换队列任务启动完成");
    }

    class QueueConsumerTask implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String url = cacheService.takeQueueTask();
                    if (url != null) {
                        processUrlAsync(url);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("队列消费线程被中断");
                } catch (Exception e) {
                    // TODO 发送通知
                    logger.error("队列消费异常", e);
                    sleepOnError();
                }
            }
        }

        private void processUrlAsync(String url) {
            CompletableFuture.runAsync(() -> processTask(url), threadPool)
                    .exceptionally(e -> {
                        logger.error("任务处理异常，url: {}", url, e);
                        return null;
                    });
        }

        private void processTask(String url) {
            try {
                FileAttribute fileAttribute = fileHandlerService.getFileAttribute(url, null);
                FileType fileType = fileAttribute.getType();
                logger.info("线程[{}] 处理任务，url：{}，类型：{}",
                        Thread.currentThread().getName(), url, fileType);

                if (isNeedConvert(fileType)) {
                    FilePreview filePreview = previewFactory.get(fileAttribute);
                    filePreview.filePreviewHandle(url, new ExtendedModelMap(), fileAttribute);
                    logger.info("线程[{}] 处理任务完成，url：{}，类型：{}",
                            Thread.currentThread().getName(), url, fileType);
                }else{
                    logger.info("线程[{}] 不需要转换，url：{}，类型：{}",
                            Thread.currentThread().getName(), url, fileType);
                }
            } catch (Exception e) {
                // TODO 补偿策略 并记录
                logger.error("文件处理异常，url: {}", url, e);
            }
        }

        private void sleepOnError() {
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        private boolean isNeedConvert(FileType fileType) {
            return fileType.equals(FileType.COMPRESS) ||
                    fileType.equals(FileType.OFFICE) ||
                    fileType.equals(FileType.CAD);
        }
    }

}
