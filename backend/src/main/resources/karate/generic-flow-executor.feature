@lock=workflow-execution
Feature: Generic Dynamic Workflow Orchestrator

  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')
    * def executionId = karate.properties['karate.executionId'] || java.lang.System.getProperty('karate.executionId')
    * def payload = Bridge.getActiveWorkflowPayload(executionId)
    * def workflowId = payload.workflowId
    * def steps = payload.steps || []
    * def globalContext = payload.initialVariables || {}

  Scenario: Execute Workflow Steps In Topological Order
    * def stepCount = karate.sizeOf(steps)
    * eval Bridge.onWorkflowStart(executionId, workflowId, stepCount)
    * def executeStepFeature = 'classpath:karate/execute-step.feature'
    * def runStep =
      """
      function(stepItem, idx) {
        if (!stepItem.enabled && stepItem.enabled !== undefined) {
          karate.log('Skipping disabled step:', stepItem.id);
          return { skipped: true };
        }
        var callArgs = {
          step: stepItem,
          executionId: executionId,
          stepIndex: idx,
          globalContext: globalContext
        };
        var stepResult = karate.call(executeStepFeature, callArgs);
        if (stepResult && stepResult.extractedVars) {
          for (var key in stepResult.extractedVars) {
            globalContext[key] = stepResult.extractedVars[key];
          }
        }
        return stepResult;
      }
      """
    * def results = karate.forEach(steps, runStep)
    * eval Bridge.onWorkflowComplete(executionId, globalContext)
