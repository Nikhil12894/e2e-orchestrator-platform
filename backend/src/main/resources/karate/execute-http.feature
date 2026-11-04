@lock=workflow-execution
Feature: Execute HTTP Request Step

  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')
    * def httpCfg = resolvedStep.httpConfig || {}
    * def httpMethod = (httpCfg.method || 'GET').toLowerCase()
    * def expectedStatus = httpCfg.expectedStatus || 200
    * def continueOnFailure = resolvedStep.continueOnFailure || false

  Scenario: Run HTTP Step
    * if (continueOnFailure) karate.configure('continueOnStepFailure', true)
    * url httpCfg.url
    * headers httpCfg.headers || {}
    * params httpCfg.params || {}
    * def reqBody = (httpMethod == 'get' || httpMethod == 'delete') ? null : httpCfg.body
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
    * eval Bridge.onStepFinished(executionId, resolvedStep, true, responseStatus, httpCfg.body, response, extractedVars, assertionDetails, null)

