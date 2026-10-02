package book.example.dto;

import java.util.ArrayList;
import java.util.List;

public class GeneratedTable {

    private String title;
    private List<String> columns = new ArrayList<>();
    private List<List<String>> rows = new ArrayList<>();

    public GeneratedTable() {
    }

    public GeneratedTable(String title, List<String> columns, List<List<String>> rows) {
        this.title = title;
        this.columns = columns;
        this.rows = rows;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = rows;
    }
}
