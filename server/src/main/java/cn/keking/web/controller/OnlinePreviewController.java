package cn.keking.web.controller;

import cn.keking.model.FileAttribute;
import cn.keking.service.FileHandlerService;
import cn.keking.service.FilePreview;
import cn.keking.service.FilePreviewFactory;
import cn.keking.service.cache.CacheService;
import cn.keking.service.impl.OtherFilePreviewImpl;
import cn.keking.utils.KkFileUtils;
import cn.keking.utils.WebUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.opensagres.xdocreport.core.io.IOUtils;
import fr.opensagres.xdocreport.document.json.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cn.keking.service.FilePreview.PICTURE_FILE_PREVIEW_PAGE;

/**
 * @author yudian-it
 */
@Controller
public class OnlinePreviewController {

    public static final String BASE64_DECODE_ERROR_MSG = "Base64解码失败，请检查你的 %s 是否采用 Base64 + urlEncode 双重编码了！";
    private final Logger logger = LoggerFactory.getLogger(OnlinePreviewController.class);

    private final FilePreviewFactory previewFactory;
    private final CacheService cacheService;
    private final FileHandlerService fileHandlerService;
    private final OtherFilePreviewImpl otherFilePreview;
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
    private static final ObjectMapper mapper = new ObjectMapper();

    public OnlinePreviewController(FilePreviewFactory filePreviewFactory, FileHandlerService fileHandlerService, CacheService cacheService, OtherFilePreviewImpl otherFilePreview) {
        this.previewFactory = filePreviewFactory;
        this.fileHandlerService = fileHandlerService;
        this.cacheService = cacheService;
        this.otherFilePreview = otherFilePreview;
    }

    /**
     * 在线预览文件
     *
     * @param url 文件URL，需要进行Base64解码
     * @param keyword 高亮关键词，可选
     * @param model 用于向视图传递数据的模型对象
     * @param req HTTP请求对象，用于获取请求相关的信息
     * @return 返回视图名称，用于展示预览页面
     */
    @GetMapping("/onlinePreview")
    public String onlinePreview(String url, String keyword, Model model, HttpServletRequest req) {
        String fileUrl;
        try {
            // 解码文件URL
            fileUrl = WebUtils.decodeUrl(url);
        } catch (Exception ex) {
            // 解码出现异常，返回错误信息
            String errorMsg = String.format(BASE64_DECODE_ERROR_MSG, "url");
            return otherFilePreview.notSupportedFile(model, errorMsg);
        }
        // 获取文件属性
        FileAttribute fileAttribute = fileHandlerService.getFileAttribute(fileUrl, req);
        // 将文件属性和解码后的关键词添加到模型中
        model.addAttribute("file", fileAttribute);
        model.addAttribute("keyword", keyword);
        // 根据文件属性获取对应的文件预览处理类
        FilePreview filePreview = previewFactory.get(fileAttribute);
        // 记录日志信息
        logger.info("预览文件url：{}，previewType：{}", fileUrl, fileAttribute.getType());
        // 对文件URL进行编码
        fileUrl = WebUtils.urlEncoderencode(fileUrl);
        // 如果文件URL为空，返回错误信息
        if (ObjectUtils.isEmpty(fileUrl)) {
            return otherFilePreview.notSupportedFile(model, "非法路径,不允许访问");
        }
        // 调用文件预览处理方法，返回视图名称
        return filePreview.filePreviewHandle(fileUrl, model, fileAttribute);
    }


    @GetMapping("/picturesPreview")
    public String picturesPreview(String urls, Model model, HttpServletRequest req) {
        String fileUrls;
        try {
            fileUrls = WebUtils.decodeUrl(urls);
            // 防止XSS攻击
            fileUrls = KkFileUtils.htmlEscape(fileUrls);
        } catch (Exception ex) {
            String errorMsg = String.format(BASE64_DECODE_ERROR_MSG, "urls");
            return otherFilePreview.notSupportedFile(model, errorMsg);
        }
        logger.info("预览文件url：{}，urls：{}", fileUrls, urls);
        // 抽取文件并返回文件列表
        String[] images = fileUrls.split("\\|");
        List<String> imgUrls = Arrays.asList(images);
        model.addAttribute("imgUrls", imgUrls);
        String currentUrl = req.getParameter("currentUrl");
        if (StringUtils.hasText(currentUrl)) {
            String decodedCurrentUrl = new String(Base64.decodeBase64(currentUrl));
            decodedCurrentUrl = KkFileUtils.htmlEscape(decodedCurrentUrl);   // 防止XSS攻击
            model.addAttribute("currentUrl", decodedCurrentUrl);
        } else {
            model.addAttribute("currentUrl", imgUrls.get(0));
        }
        return PICTURE_FILE_PREVIEW_PAGE;
    }

    /**
     * 根据url获取文件内容
     * 当pdfjs读取存在跨域问题的文件时将通过此接口读取
     *
     * @param urlPath  url
     * @param response response
     */
    @GetMapping("/getCorsFile")
    public void getCorsFile(String urlPath, HttpServletResponse response, FileAttribute fileAttribute) throws IOException {
        URL url;
        try {
            urlPath = WebUtils.decodeUrl(urlPath);
            url = WebUtils.normalizedURL(urlPath);
        } catch (Exception ex) {
            logger.error(String.format(BASE64_DECODE_ERROR_MSG, urlPath), ex);
            return;
        }
        assert urlPath != null;
        if (!urlPath.toLowerCase().startsWith("http") && !urlPath.toLowerCase().startsWith("https") && !urlPath.toLowerCase().startsWith("ftp")) {
            logger.info("读取跨域文件异常，可能存在非法访问，urlPath：{}", urlPath);
            return;
        }
        InputStream inputStream = null;
        logger.info("读取跨域pdf文件url：{}", urlPath);
        if (!urlPath.toLowerCase().startsWith("ftp:")) {
            factory.setConnectionRequestTimeout(2000);
            factory.setConnectTimeout(10000);
            factory.setReadTimeout(72000);
            HttpClient httpClient = HttpClientBuilder.create().setRedirectStrategy(new DefaultRedirectStrategy()).build();
            factory.setHttpClient(httpClient);
            restTemplate.setRequestFactory(factory);
            RequestCallback requestCallback = request -> {
                request.getHeaders().setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
                String proxyAuthorization = fileAttribute.getKkProxyAuthorization();
                if (StringUtils.hasText(proxyAuthorization)) {
                    Map<String, String> proxyAuthorizationMap = mapper.readValue(proxyAuthorization, Map.class);
                    proxyAuthorizationMap.forEach((key, value) -> request.getHeaders().set(key, value));
                }
            };
            try {
                restTemplate.execute(url.toURI(), HttpMethod.GET, requestCallback, fileResponse -> {
                    IOUtils.copy(fileResponse.getBody(), response.getOutputStream());
                    return null;
                });
            } catch (Exception e) {
                System.out.println(e);
            }
        } else {
            try {
                if (urlPath.contains(".svg")) {
                    response.setContentType("image/svg+xml");
                }
                inputStream = (url).openStream();
                IOUtils.copy(inputStream, response.getOutputStream());
            } catch (IOException e) {
                logger.error("读取跨域文件异常，url：{}", urlPath);
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        }
    }

    /**
     * 通过api接口入队
     *
     * @param url 请编码后在入队
     */
    @GetMapping("/addTask")
    @ResponseBody
    public String addQueueTask(String url) {
        logger.info("添加转码队列url：{}", url);
        cacheService.addQueueTask(url);
        return "success";
    }

    @PostMapping("/batchAddTask")
    @SuppressWarnings("unchecked")
    @ResponseBody
    public String batchAddTask(@RequestBody JSONObject req){
        List<String> urls = (List<String>) req.get("urls");
        logger.info("批量添加转码队列url大小：{}", urls.size());
        cacheService.batchAddQueueTask(urls);
        return "success";
    }


}
