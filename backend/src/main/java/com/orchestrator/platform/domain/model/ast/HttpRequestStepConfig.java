package com.orchestrator.platform.domain.model.ast;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpRequestStepConfig implements Serializable {
    private String url;
    private String method = "GET";
    private Map<String, String> headers = new HashMap<>();
    private boolean inheritGlobalHeaders = true;
    private List<String> excludedGlobalHeaders = new ArrayList<>();
    private Map<String, String> params = new HashMap<>();
    private Object body;
    private Integer expectedStatus = 200;
    private Integer timeoutMs = 15000;
    private Integer retryCount = 1;
    private Integer retryIntervalMs = 500;

    public HttpRequestStepConfig() {}

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public boolean isInheritGlobalHeaders() { return inheritGlobalHeaders; }
    public void setInheritGlobalHeaders(boolean inheritGlobalHeaders) { this.inheritGlobalHeaders = inheritGlobalHeaders; }

    public List<String> getExcludedGlobalHeaders() { return excludedGlobalHeaders; }
    public void setExcludedGlobalHeaders(List<String> excludedGlobalHeaders) { this.excludedGlobalHeaders = excludedGlobalHeaders; }

    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) { this.params = params; }

    public Object getBody() { return body; }
    public void setBody(Object body) { this.body = body; }

    public Integer getExpectedStatus() { return expectedStatus; }
    public void setExpectedStatus(Integer expectedStatus) { this.expectedStatus = expectedStatus; }

    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Integer getRetryIntervalMs() { return retryIntervalMs; }
    public void setRetryIntervalMs(Integer retryIntervalMs) { this.retryIntervalMs = retryIntervalMs; }
}
