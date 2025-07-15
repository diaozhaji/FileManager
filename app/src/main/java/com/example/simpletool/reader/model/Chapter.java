package com.example.simpletool.reader.model;

public class Chapter {
    private long id;
    private long bookId; // 关联的书籍ID
    private String title;
    private int startPosition; // 章节起始字符位置
    private int endPosition;   // 章节结束字符位置
    private int pageNumber;    // 起始页码
    private String volume;     // 所属分卷

    // 全参数构造方法
    public Chapter(long id, long bookId, String title,
                   int startPosition, int endPosition,
                   int pageNumber, String volume) {
        this.id = id;
        this.bookId = bookId;
        this.title = title;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.pageNumber = pageNumber;
        this.volume = volume;
    }

    // 简化构造方法（用于解析章节）
    public Chapter(String title, int startPosition, int endPosition) {
        this(0, 0, title, startPosition, endPosition, 0, null);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getBookId() {
        return bookId;
    }

    public void setBookId(long bookId) {
        this.bookId = bookId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public void setEndPosition(int endPosition) {
        this.endPosition = endPosition;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }
}