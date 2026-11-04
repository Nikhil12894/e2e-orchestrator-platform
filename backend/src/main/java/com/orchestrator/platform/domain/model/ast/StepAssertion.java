package com.orchestrator.platform.domain.model.ast;

import java.io.Serializable;

public class StepAssertion implements Serializable {
    private String field;
    private String operator; // EQUALS, NOT_EQUALS, CONTAINS, GREATER_THAN, LESS_THAN, MATCHES_REGEX, IS_NOT_NULL
    private Object expectedValue;

    public StepAssertion() {}

    public StepAssertion(String field, String operator, Object expectedValue) {
        this.field = field;
        this.operator = operator;
        this.expectedValue = expectedValue;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public Object getExpectedValue() { return expectedValue; }
    public void setExpectedValue(Object expectedValue) { this.expectedValue = expectedValue; }
}
