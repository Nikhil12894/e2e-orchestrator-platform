@lock=workflow-execution
Feature: Dynamic Step Executor

  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')
    * def resolvedStep = Bridge.resolveStepVariables(executionId, step, globalContext)
    * def stepType = resolvedStep.type + ''

  Scenario: Execute Step By Type
    * eval Bridge.onStepStart(executionId, resolvedStep.id, resolvedStep.name, stepType)
    * def stepRes = null

    # Branch 1: HTTP API Request
    * if (stepType == 'HTTP_REQUEST') karate.set('stepRes', karate.call('classpath:karate/execute-http.feature', { resolvedStep: resolvedStep, executionId: executionId }))

    # Branch 2: Database Polling via HikariCP
    * if (stepType == 'DB_POLL') karate.set('stepRes', karate.call('classpath:karate/execute-db.feature', { resolvedStep: resolvedStep, executionId: executionId }))

    # Branch 3: Kafka Message Assertion
    * if (stepType == 'KAFKA_ASSERT') karate.set('stepRes', karate.call('classpath:karate/execute-kafka.feature', { resolvedStep: resolvedStep, executionId: executionId }))

    # Branch 4: Delay Step
    * if (stepType == 'DELAY') karate.set('stepRes', karate.call('classpath:karate/execute-delay.feature', { resolvedStep: resolvedStep, executionId: executionId }))

    * def extractedVars = (karate.get('stepRes') && stepRes.extractedVars) ? stepRes.extractedVars : {}
