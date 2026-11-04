@lock=workflow-execution
Feature: Execute Kafka Assertion Step

  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')

  Scenario: Run Kafka Assertion
    * def kafkaResult = Bridge.executeKafkaAssert(executionId, resolvedStep)
    * def isMatched = kafkaResult.matched
    * def matchedPayload = kafkaResult.matchedPayload || ''

    # Extract any variables from the matched message
    * def extractedVars = Bridge.extractVariables(executionId, resolvedStep, matchedPayload, 200, {})

    # Karate Assertion
    * match isMatched == true

    * eval Bridge.onStepFinished(executionId, resolvedStep, isMatched, 200, resolvedStep.kafkaConfig.topic, kafkaResult, extractedVars, [], kafkaResult.errorMessage)
