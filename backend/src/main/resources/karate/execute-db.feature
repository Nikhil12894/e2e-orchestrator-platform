@lock=workflow-execution
Feature: Execute Database SQL Poll Step

  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')

  Scenario: Run DB Polling
    * def pollResult = Bridge.executeDbPoll(executionId, resolvedStep)
    * def isMatched = pollResult.matched
    * def latestRow = pollResult.latestRow || {}

    # Extract any variables from the matched DB row
    * def extractedVars = Bridge.extractVariables(executionId, resolvedStep, latestRow, 200, {})

    # Karate Assertion
    * match isMatched == true

    * eval Bridge.onStepFinished(executionId, resolvedStep, isMatched, 200, resolvedStep.dbPollConfig.query, pollResult, extractedVars, [], pollResult.errorMessage)
