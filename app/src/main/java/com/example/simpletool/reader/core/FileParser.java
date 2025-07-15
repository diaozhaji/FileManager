package com.example.simpletool.reader.core;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.BufferedInputStream;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class FileParser {

    private static final int BUFFER_SIZE = 4096;
    private static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * 检测文件编码格式
     *
     * @param file 需要检测的文件
     * @return 检测到的编码格式，默认返回GBK
     * @throws IOException 文件读取异常
     */
    public static String detectEncoding(File file) throws IOException {
        UniversalDetector detector = new UniversalDetector(null);
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = bis.read(buffer)) > 0 && !detector.isDone()) {
                detector.handleData(buffer, 0, bytesRead);
            }
        } finally {
            detector.dataEnd();
            detector.reset();
        }

        String detectedEncoding = detector.getDetectedCharset();
        return isValidEncoding(detectedEncoding) ? detectedEncoding : DEFAULT_ENCODING;
    }

    /**
     * 读取文件内容（自动处理编码）
     *
     * @param filePath 文件路径
     * @param encoding 指定的编码格式
     * @return 文件内容字符串
     * @throws IOException 文件读取异常
     */
    public static String readFileContent(String filePath, String encoding) throws IOException {
        return readFileContent(new File(filePath), encoding);
    }

    /**
     * 读取文件内容（重载方法）
     *
     * @param file     文件对象
     * @param encoding 指定的编码格式
     * @return 文件内容字符串
     * @throws IOException 文件读取异常
     */
    public static String readFileContent(File file, String encoding) throws IOException {
        Charset charset = isValidEncoding(encoding) ?
                Charset.forName(encoding) : StandardCharsets.UTF_8;

        StringBuilder content = new StringBuilder((int) file.length());

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), charset))) {

            char[] buffer = new char[BUFFER_SIZE];
            int charsRead;

            while ((charsRead = reader.read(buffer)) != -1) {
                content.append(buffer, 0, charsRead);
            }
        }
        return content.toString().replaceAll("\\r\\n?", "\n"); // 统一换行符
    }

    /**
     * 自动检测编码并读取文件（二合一操作）
     *
     * @param filePath 文件路径
     * @return 文件内容字符串
     * @throws IOException 文件读取异常
     */
    public static String readFileAutoEncoding(String filePath) throws IOException {
        File file = new File(filePath);
        String encoding = detectEncoding(file);
        return readFileContent(file, encoding);
    }

    private static boolean isValidEncoding(String encoding) {
        if (encoding == null) return false;
        try {
            return Charset.isSupported(encoding);
        } catch (Exception e) {
            return false;
        }
    }

}