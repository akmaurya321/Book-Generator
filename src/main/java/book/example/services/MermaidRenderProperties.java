package book.example.services;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mermaid")
public class MermaidRenderProperties {

    private String command = "mmdc";

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}