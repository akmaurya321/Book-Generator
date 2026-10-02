package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class ChapterLlmResponse {
    private String chapterId;
    private List<ChapterSectionOutput> sections = new ArrayList<>();

    public String getChapterId() { return chapterId; }
    public void setChapterId(String chapterId) { this.chapterId = chapterId; }
    public List<ChapterSectionOutput> getSections() { return sections; }
    public void setSections(List<ChapterSectionOutput> sections) { this.sections = sections == null ? new ArrayList<>() : sections; }
}
