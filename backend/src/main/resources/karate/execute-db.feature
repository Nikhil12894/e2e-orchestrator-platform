@lock=workflow-execution
Feature: Execute Database SQL Poll Step

  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')

  Scenario: Run DB Polling
    * def pollResult = Bridge.executeDbPoll(executionId, resolvedStep)
    * def isMatched = pollResult.matched
    * def latestRow = pollResult.latestRow || {}

    # Build composite payload with row, latestRow, rows, and columns for rich extraction
    * def dbPayload = karate.merge(latestRow, { row: latestRow, latestRow: latestRow, rows: pollResult.rows, attempts: pollResult.attempts, matched: isMatched })

    # Extract any variables from the matched DB row
    * def extractedVars = Bridge.extractVariables(executionId, resolvedStep, dbPayload, 200, {})

    # Karate Assertion
    * match isMatched == true

    * eval Bridge.onStepFinished(executionId, resolvedStep, isMatched, 200, resolvedStep.dbPollConfig.query, pollResult, extractedVars, [], pollResult.errorMessage)
