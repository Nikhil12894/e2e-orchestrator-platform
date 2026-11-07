@lock=workflow-execution
Feature: Execute HTTP Request Step

  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')
    * def httpCfg = resolvedStep.httpConfig || {}
    * def httpMethod = (httpCfg.method || 'GET').toLowerCase()
    * def expectedStatus = httpCfg.expectedStatus || 200
    * def continueOnFailure = resolvedStep.continueOnFailure || false
    * def fHeaders = karate.get('flowHeaders') || {}
    * def mergedHeaders = Bridge.computeEffectiveHeaders(fHeaders, httpCfg)

  Scenario: Run HTTP Step
    * if (continueOnFailure) karate.configure('continueOnStepFailure', true)
    * url httpCfg.url
    * headers mergedHeaders
    * params httpCfg.params || {}

    # Evaluate body type: Form URL-Encoded vs JSON
    * def isForm = (httpCfg.bodyType == 'FORM_URL_ENCODED') || (mergedHeaders && (mergedHeaders['Content-Type'] == 'application/x-www-form-urlencoded' || mergedHeaders['content-type'] == 'application/x-www-form-urlencoded'))
    * def formPayload = isForm ? (httpCfg.formData || httpCfg.body || {}) : null
    * def reqBody = (!isForm && httpMethod != 'get' && httpMethod != 'delete') ? httpCfg.body : null

    # Apply Karate keywords
    * form fields formPayload
    * request reqBody

    # Configure Karate v2 Polling & Retry
    * def retryAttempts = httpCfg.retryCount || 1
    * def retryInterval = httpCfg.retryIntervalMs || 500
    * eval karate.configure('retry', { count: retryAttempts, interval: retryInterval })
    * retry until responseStatus == expectedStatus

    # Perform Native HTTP Call
    * method httpMethod

    # Evaluate Assertions & Status
    * def assertionResult = Bridge.evaluateAssertions(executionId, resolvedStep, responseStatus, response)
    * def assertionDetails = assertionResult.details
    * def assertionPassed = assertionResult.passed

    # Extract Variables
    * def extractedVars = Bridge.extractVariables(executionId, resolvedStep, response, responseStatus, responseHeaders)

    # Karate Assertions for Report Visibility
    * match responseStatus == expectedStatus
    * match assertionPassed == true

    # Record Success to Bridge
    * eval Bridge.onStepFinished(executionId, resolvedStep, true, responseStatus, isForm ? formPayload : httpCfg.body, response, extractedVars, assertionDetails, null, mergedHeaders)

