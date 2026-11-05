@lock=workflow-execution
Feature: Execute Delay Step

  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')
    * def pauseMs = resolvedStep.delayMs || 1000

  Scenario: Run Delay
    * def delayRes = Bridge.executeDelay(executionId, pauseMs)
    * def isCancelled = delayRes.interrupted || false
    * eval Bridge.onStepFinished(executionId, resolvedStep, !isCancelled, 200, { delayMs: pauseMs }, { status: isCancelled ? 'CANCELLED' : 'COMPLETE' }, {}, [], isCancelled ? 'Step cancelled by user' : null)

