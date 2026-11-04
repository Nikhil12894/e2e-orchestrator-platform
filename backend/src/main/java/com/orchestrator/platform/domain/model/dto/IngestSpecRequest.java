package com.orchestrator.platform.domain.model.dto;

import java.io.Serializable;

public class IngestSpecRequest implements Serializable {
    private String serviceName;
    private String specUrl;
    private String specContent;

    public IngestSpecRequest() {}

    public IngestSpecRequest(String serviceName, String specUrl, String specContent) {
        this.serviceName = serviceName;
        this.specUrl = specUrl;
        this.specContent = specContent;
    }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getSpecUrl() { return specUrl; }
    public void setSpecUrl(String specUrl) { this.specUrl = specUrl; }

    public String getSpecContent() { return specContent; }
    public void setSpecContent(String specContent) { this.specContent = specContent; }
}
