package com.example.simpletool.reader.model;

public class Page {
    public final String text;
    public final int start;
    public final int end;

    public Page(String text, int start, int end) {
        this.text = text;
        this.start = start;
        this.end = end;
    }
}