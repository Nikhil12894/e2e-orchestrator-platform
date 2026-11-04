package com.orchestrator.platform.domain.model.ast;

import java.io.Serializable;

public class VariableExtraction implements Serializable {
    private String variableName;
    private String source; // RESPONSE_BODY, RESPONSE_HEADER, RESPONSE_STATUS, DB_ROW_COLUMN, KAFKA_MESSAGE
    private String jsonPath; // e.g. $.data.token or $.id
    private String defaultValue;

    public VariableExtraction() {}

    public VariableExtraction(String variableName, String source, String jsonPath, String defaultValue) {
        this.variableName = variableName;
        this.source = source;
        this.jsonPath = jsonPath;
        this.defaultValue = defaultValue;
    }

    public String getVariableName() { return variableName; }
    public void setVariableName(String variableName) { this.variableName = variableName; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getJsonPath() { return jsonPath; }
    public void setJsonPath(String jsonPath) { this.jsonPath = jsonPath; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
}
