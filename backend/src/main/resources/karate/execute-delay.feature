@lock=workflow-execution
Feature: Execute Delay Step

  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')
    * def pauseMs = resolvedStep.delayMs || 1000

  Scenario: Run Delay
    * eval karate.pause(pauseMs)
    * eval Bridge.onStepFinished(executionId, resolvedStep, true, 200, { delayMs: pauseMs }, { status: 'COMPLETE' }, {}, [], null)

