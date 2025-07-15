package com.example.simpletool.reader.core;

import com.example.simpletool.reader.model.Chapter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChapterParser {
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^\\s*第[\\d零一二三四五六七八九十百千]+章\\s+.*$");

    public List<Chapter> parseChapters(String content) {
        List<Chapter> chapters = new ArrayList<>();
        Matcher matcher = CHAPTER_PATTERN.matcher(content);

        int lastEnd = 0;
        while (matcher.find()) {
            int start = matcher.start();
            if (start > lastEnd) {
                chapters.add(new Chapter("前言", lastEnd, start));
            }
            chapters.add(new Chapter(matcher.group().trim(), start, -1));
            lastEnd = matcher.end();
        }
        return chapters;
    }
}