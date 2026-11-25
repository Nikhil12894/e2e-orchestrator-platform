package com.orchestrator.platform.standalone;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebSpaController {

    /**
     * Forwards non-API, non-WebSocket, non-static-asset browser requests to index.html
     * so that browser page reloads or client-side paths resolve cleanly.
     */
    @GetMapping(value = {
            "/{path:[^\\.]*}",
            "/*/{path:[^\\.]*}"
    })
    public String forwardSpa() {
        return "forward:/index.html";
    }
}
