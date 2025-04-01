package cn.keking.service.impl;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.ReturnResponse;
import cn.keking.service.FileHandlerService;
import cn.keking.service.FilePreview;
import cn.keking.service.OfficeToPdfService;
import cn.keking.utils.*;
import cn.keking.web.filter.BaseUrlFilter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jodconverter.core.office.OfficeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by kl on 2018/1/17.
 * Content :处理office文件
 */
@Service
public class OfficeFilePreviewImpl implements FilePreview {

    private final Logger logger = LoggerFactory.getLogger(OfficeFilePreviewImpl.class);

    private final String fileDir = ConfigConstants.getFileDir();
    private final String demoDir = "demo";
    private final String demoPath = demoDir + File.separator;

    public static final String OFFICE_PREVIEW_TYPE_IMAGE = "image";
    public static final String OFFICE_PREVIEW_TYPE_ALL_IMAGES = "allImages";
    private static final String OFFICE_PASSWORD_MSG = "password";

    private final FileHandlerService fileHandlerService;
    private final OfficeToPdfService officeToPdfService;
    private final OtherFilePreviewImpl otherFilePreview;

    private final ReentrantLock lock = new ReentrantLock(); // 定义锁

    public OfficeFilePreviewImpl(FileHandlerService fileHandlerService, OfficeToPdfService officeToPdfService, OtherFilePreviewImpl otherFilePreview) {
        this.fileHandlerService = fileHandlerService;
        this.officeToPdfService = officeToPdfService;
        this.otherFilePreview = otherFilePreview;
    }

    @Override
    public String filePreviewHandle(String url, Model model, FileAttribute fileAttribute) {
        // 预览Type，参数传了就取参数的，没传取系统默认
        String officePreviewType = fileAttribute.getOfficePreviewType();
        // pdf和word全部按照pdf处理，kkfile默认这两个类型都使用image，不能实现关键词高亮
        if ("doc".equalsIgnoreCase(fileAttribute.getSuffix()) || "docx".equalsIgnoreCase(fileAttribute.getSuffix()) || "pdf".equalsIgnoreCase(fileAttribute.getSuffix())) {
            officePreviewType = "pdf";
        }
        String keywords = Optional.ofNullable(model.getAttribute("keyword"))
                .map(Object::toString)
                .orElse(null);
        // 处理表格高亮交给apache-poi
        if (("xlsx".equalsIgnoreCase(fileAttribute.getSuffix()) || "xls".equalsIgnoreCase(fileAttribute.getSuffix())) && (keywords != null)) {
            try {
                // 下载文件内容
                byte[] fileContent = downloadFileFromUrl(url);

                // 加载并处理Excel文件
                byte[] processedContent;
                try (ByteArrayInputStream bis = new ByteArrayInputStream(fileContent)) {
                    Workbook workbook = new XSSFWorkbook(bis);
                    // 处理高亮
                    StringTokenizer tokens = new StringTokenizer(keywords, " ");
                    while (tokens.hasMoreTokens()) {
                        String keyword = tokens.nextToken().trim();
                        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                            Sheet sheet = workbook.getSheetAt(sheetIndex);
                            highlightKeyword(sheet, keyword);
                        }
                    }
                    // 将处理后的内容保存到字节数组
                    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                        workbook.write(bos);
                        processedContent = bos.toByteArray();
                    }
                }
                // 保存处理后的文件
                String highlightedFileName = "highlighted_" + fileAttribute.getName();
                File outFile = new File(fileDir + demoPath);
                if (!outFile.exists() && !outFile.mkdirs()) {
                    logger.error("创建文件夹【{}】失败，请检查目录权限！", fileDir + demoPath);
                }
                try (OutputStream out = Files.newOutputStream(Paths.get(fileDir + demoPath + highlightedFileName))) {
                    out.write(processedContent);
                }
                // 更新url为新文件的地址
                url = BaseUrlFilter.getBaseUrl() + demoDir + "/" + highlightedFileName;
            } catch (IOException e) {
                logger.error("Excel高亮处理失败", e);
                return otherFilePreview.notSupportedFile(model, fileAttribute, "出错了，请联系管理员处理~");
            }
        }
        boolean userToken = fileAttribute.getUsePasswordCache();
        String baseUrl = BaseUrlFilter.getBaseUrl();
        //获取文件后缀
        String suffix = fileAttribute.getSuffix();
        //获取文件原始名称
        String fileName = fileAttribute.getName();
        //获取密码
        String filePassword = fileAttribute.getFilePassword();
        //是否启用强制更新命令
        boolean forceUpdatedCache = fileAttribute.forceUpdatedCache();
        //xlsx  转换成html
        boolean isHtmlView = fileAttribute.isHtmlView();
        //转换后的文件名
        String cacheName = fileAttribute.getCacheName();
        //转换后生成文件的路径
        String outFilePath = fileAttribute.getOutFilePath();

        boolean needDownload = forceUpdatedCache ||
                !fileHandlerService.listConvertedFiles().containsKey(cacheName) ||
                !ConfigConstants.isCacheEnabled();

        if (needDownload) {
            lock.lock(); // 加锁
            try {
                // 再次检查缓存状态，避免重复下载
                if (forceUpdatedCache ||
                        !fileHandlerService.listConvertedFiles().containsKey(cacheName) ||
                        !ConfigConstants.isCacheEnabled()) {

                    int retryCount = 3; // 最大重试次数
                    boolean downloadSuccess = false;
                    Exception lastException = null;

                    for (int i = 0; i < retryCount; i++) {
                        try {
                            // 下载远程文件到本地
                            ReturnResponse<String> response = DownloadUtils.downLoad(fileAttribute, fileName);
                            if (response.isFailure()) {
                                return otherFilePreview.notSupportedFile(model, fileAttribute, response.getMsg());
                            }

                            String filePath = response.getContent();
                            boolean isPwdProtectedOffice = OfficeUtils.isPwdProtected(filePath);

                            if (isPwdProtectedOffice && !StringUtils.hasLength(filePassword)) {
                                model.addAttribute("needFilePassword", true);
                                return EXEL_FILE_PREVIEW_PAGE;
                            } else {
                                if (StringUtils.hasText(outFilePath)) {
                                    try {
                                        officeToPdfService.openOfficeToPDF(filePath, outFilePath, fileAttribute);
                                    } catch (OfficeException e) {
                                        if (isPwdProtectedOffice && !OfficeUtils.isCompatible(filePath, filePassword)) {
                                            model.addAttribute("needFilePassword", true);
                                            model.addAttribute("filePasswordError", true);
                                            return EXEL_FILE_PREVIEW_PAGE;
                                        }
                                        return otherFilePreview.notSupportedFile(model, fileAttribute, "抱歉，该文件版本不兼容，文件版本错误。");
                                    }
                                    if (isHtmlView) {
                                        // 对转换后的文件进行操作(改变编码方式)
                                        fileHandlerService.doActionConvertedFile(outFilePath);
                                    }
                                    // 是否保留OFFICE源文件
                                    if (!fileAttribute.isCompressFile() && ConfigConstants.getDeleteSourceFile()) {
                                        KkFileUtils.deleteFileByPath(filePath);
                                    }
                                    if (userToken || !isPwdProtectedOffice) {
                                        // 加入缓存
                                        fileHandlerService.addConvertedFile(cacheName, fileHandlerService.getRelativePath(outFilePath));
                                    }
                                }
                            }
                            downloadSuccess = true;
                            break; // 下载成功，退出重试循环
                        } catch (Exception e) {
                            lastException = e;
                            logger.warn("文件下载失败，正在尝试第 {} 次重试...", i + 1, e);
                        }
                    }

                    if (!downloadSuccess) {
                        logger.error("文件下载失败，已达到最大重试次数", lastException);
                        return otherFilePreview.notSupportedFile(model, fileAttribute, "文件下载失败，请稍后重试或联系管理员。");
                    }
                }
            } finally {
                lock.unlock(); // 释放锁
            }
        }

        if (!isHtmlView && baseUrl != null && (OFFICE_PREVIEW_TYPE_IMAGE.equals(officePreviewType) || OFFICE_PREVIEW_TYPE_ALL_IMAGES.equals(officePreviewType))) {
            return getPreviewType(model, fileAttribute, officePreviewType, cacheName, outFilePath, fileHandlerService, OFFICE_PREVIEW_TYPE_IMAGE, otherFilePreview);
        }
        model.addAttribute("pdfUrl", WebUtils.encodeFileName(cacheName));  // 输出转义文件名 方便url识别
        return isHtmlView ? EXEL_FILE_PREVIEW_PAGE : PDF_FILE_PREVIEW_PAGE;
    }

    static String getPreviewType(Model model, FileAttribute fileAttribute, String officePreviewType, String pdfName, String outFilePath, FileHandlerService fileHandlerService, String officePreviewTypeImage, OtherFilePreviewImpl otherFilePreview) {
        String suffix = fileAttribute.getSuffix();
        boolean isPPT = suffix.equalsIgnoreCase("ppt") || suffix.equalsIgnoreCase("pptx");
        List<String> imageUrls = null;
        try {
            imageUrls = fileHandlerService.pdf2jpg(outFilePath, outFilePath, pdfName, fileAttribute);
        } catch (Exception e) {
            Throwable[] throwableArray = ExceptionUtils.getThrowables(e);
            for (Throwable throwable : throwableArray) {
                if (throwable instanceof IOException || throwable instanceof EncryptedDocumentException) {
                    if (e.getMessage().toLowerCase().contains(OFFICE_PASSWORD_MSG)) {
                        model.addAttribute("needFilePassword", true);
                        return EXEL_FILE_PREVIEW_PAGE;
                    }
                }
            }
        }
        if (imageUrls == null || imageUrls.size() < 1) {
            return otherFilePreview.notSupportedFile(model, fileAttribute, "office转图片异常，请联系管理员");
        }
        model.addAttribute("imgUrls", imageUrls);
        model.addAttribute("currentUrl", imageUrls.get(0));
        if (officePreviewTypeImage.equals(officePreviewType)) {
            // PPT 图片模式使用专用预览页面
            return (isPPT ? PPT_FILE_PREVIEW_PAGE : OFFICE_PICTURE_FILE_PREVIEW_PAGE);
        } else {
            return PICTURE_FILE_PREVIEW_PAGE;
        }
    }

    /**
     * 将原有的表格文件进行下载转字节
     */
    private byte[] downloadFileFromUrl(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] dataChunk = new byte[4096];
                int bytesRead;
                while ((bytesRead = httpConn.getInputStream().read(dataChunk, 0, dataChunk.length)) != -1) {
                    buffer.write(dataChunk, 0, bytesRead);
                }
                return buffer.toByteArray();
            }
        } else {
            throw new IOException("Failed to download file from URL: HTTP response code is " + responseCode);
        }
    }

    /**
     * poi处理表格高亮
     */
    private void highlightKeyword(Sheet sheet, String keyword) {
        Workbook workbook = sheet.getWorkbook();
        CellStyle yellowCellStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(IndexedColors.BLACK.getIndex());
        yellowCellStyle.setFont(font);
        yellowCellStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        yellowCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().toLowerCase().contains(keyword.toLowerCase())) {
                    cell.setCellStyle(yellowCellStyle);
                }
            }
        }
    }
}
