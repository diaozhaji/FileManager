package com.example.simpletool.reader.model;

public class Bookmark {
    private long id;
    private long bookId; // 关联的书籍ID
    private int position; // 字符位置
    private long timestamp; // 添加时间
    private String note;    // 用户备注（可选）
    private int startPage;  // 页码范围起始（可选）
    private int endPage;    // 页码范围结束（可选）

    // 全参数构造方法
    public Bookmark(long id, long bookId, int position,
                    long timestamp, String note,
                    int startPage, int endPage) {
        this.id = id;
        this.bookId = bookId;
        this.position = position;
        this.timestamp = timestamp;
        this.note = note;
        this.startPage = startPage;
        this.endPage = endPage;
    }

    // 简化构造方法（快速添加书签）


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

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public int getStartPage() {
        return startPage;
    }

    public void setStartPage(int startPage) {
        this.startPage = startPage;
    }

    public int getEndPage() {
        return endPage;
    }

    public void setEndPage(int endPage) {
        this.endPage = endPage;
    }
}