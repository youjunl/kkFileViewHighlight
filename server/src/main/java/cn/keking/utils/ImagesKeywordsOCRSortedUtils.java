package cn.keking.utils;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * 传入图片地址数组，和关键词，返回排序后的图片地址数组，以及最符合的图片下标
 * 用于PPT预览 定位到关键页
 */
public class ImagesKeywordsOCRSortedUtils {

    public static List<Integer> getSortedImages(List<String> imageUrls, String keywords) {
        ITesseract tesseract = new Tesseract(); // 创建Tesseract对象
        // 如果tesseract-ocr不是安装在默认路径下，则设置路径
        // tesseract.setDatapath("/path/to/tessdata");

        List<ImageInfo> imagesWithText = new LinkedList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            try {
                String imageUrl = imageUrls.get(i);
                // 假设这里有一个方法可以将imageUrl转换为File对象或输入流
                // File imageFile = convertImageUrlToFile(imageUrl);

                // 进行OCR识别，提取文本
                String ocrResult = tesseract.doOCR(new File(imageUrl));
                // 计算匹配度，这里只是简单地计算包含关键词的数量
                int matchCount = countKeywordOccurrences(ocrResult, keywords);

                // 存储图像索引和匹配度
                imagesWithText.add(new ImageInfo(i, matchCount));
            } catch (TesseractException e) {
                // 处理异常情况
                System.err.println("Error during OCR processing: " + e.getMessage());
            }
        }

        // 按照匹配度降序排序
        imagesWithText.sort((img1, img2) -> Integer.compare(img2.matchCount, img1.matchCount));

        // 提取排序后的索引
        List<Integer> sortedIndices = imagesWithText.stream()
                .map(ImageInfo::getIndex)
                .collect(Collectors.toList());

        // 找到最符合的图片下标（假设是第一个元素）
//        if (!sortedIndices.isEmpty()) {
//            // 你可以选择返回最符合的图片下标或者做一些其他处理
//        }

        return sortedIndices;
    }

    private static int countKeywordOccurrences(String text, String keywords) {
        // 实现关键词匹配逻辑，比如简单的包含检查、正则表达式匹配等
        // 这里仅提供一个非常简单的实现
        int occurrences = 0;
        String[] words = keywords.split("\\s+");
        for (String word : words) {
            occurrences += text.toLowerCase().split(word.toLowerCase(), -1).length - 1;
        }
        return occurrences;
    }

    // 辅助类，用于存储图像索引和匹配度
    private static class ImageInfo {
        private final int index;
        private final int matchCount;

        public ImageInfo(int index, int matchCount) {
            this.index = index;
            this.matchCount = matchCount;
        }

        public int getIndex() {
            return index;
        }

        public int getMatchCount() {
            return matchCount;
        }
    }
}