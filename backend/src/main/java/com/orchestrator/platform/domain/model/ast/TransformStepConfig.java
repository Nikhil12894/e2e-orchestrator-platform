package com.orchestrator.platform.domain.model.ast;

import java.io.Serializable;

public class TransformStepConfig implements Serializable {
    private String script;

    public TransformStepConfig() {}

    public TransformStepConfig(String script) {
        this.script = script;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }
}
