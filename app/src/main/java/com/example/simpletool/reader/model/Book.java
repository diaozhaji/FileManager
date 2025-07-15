package com.example.simpletool.reader.model;

public class Book {
    private long id; // 数据库自增ID
    private String title; // 书名
    private String filePath; // 文件路径
    private String coverImage; // 封面图片路径（可选）
    private int totalPages; // 总页数
    private int currentPosition; // 当前阅读位置（字符位置）
    private long lastReadTime; // 最后阅读时间（时间戳）
    private String volume; // 分卷信息（可选）

    // 全参数构造方法（用于数据库查询）
    public Book(long id, String title, String filePath, String coverImage,
                int totalPages, int currentPosition, long lastReadTime, String volume) {
        this.id = id;
        this.title = title;
        this.filePath = filePath;
        this.coverImage = coverImage;
        this.totalPages = totalPages;
        this.currentPosition = currentPosition;
        this.lastReadTime = lastReadTime;
        this.volume = volume;
    }

    // 简化构造方法（用于新建书籍）
    public Book(String title, String filePath) {
        this(0, title, filePath, null, 0, 0, System.currentTimeMillis(), null);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }

    public long getLastReadTime() {
        return lastReadTime;
    }

    public void setLastReadTime(long lastReadTime) {
        this.lastReadTime = lastReadTime;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }
}