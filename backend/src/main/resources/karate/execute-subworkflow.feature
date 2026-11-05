@lock=workflow-execution
Feature: Execute Sub-Workflow Step

  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')

  Scenario: Run Nested Sub-Workflow
    * def subInfo = Bridge.prepareSubWorkflow(executionId, resolvedStep, globalContext)
    * def isPrepSuccess = subInfo.success
    * match isPrepSuccess == true

    * def subSteps = subInfo.steps || []
    * def subContext = subInfo.initialContext || {}
    * def subFlowHeaders = subInfo.flowHeaders || {}
    * def executeStepFeature = 'classpath:karate/execute-step.feature'

    * def runSubStep =
      """
      function(stepItem, idx) {
        if (Bridge.isExecutionCancelled(executionId)) {
          karate.log('Workflow execution is cancelled; aborting sub-workflow step:', stepItem.id);
          return { cancelled: true };
        }
        if (!stepItem.enabled && stepItem.enabled !== undefined) {
          karate.log('Skipping disabled sub-workflow step:', stepItem.id);
          return { skipped: true };
        }
        var callArgs = {
          step: stepItem,
          executionId: executionId,
          stepIndex: idx,
          flowHeaders: subFlowHeaders,
          globalContext: subContext
        };
        var stepResult = karate.call(executeStepFeature, callArgs);
        if (stepResult && stepResult.extractedVars) {
          for (var key in stepResult.extractedVars) {
            subContext[key] = stepResult.extractedVars[key];
          }
        }
        return stepResult;
      }
      """
    * def results = karate.forEach(subSteps, runSubStep)
    * def subFinal = Bridge.finishSubWorkflow(executionId, resolvedStep, subContext)
    * def subPayload = karate.merge(subContext, { outputVariables: subContext, success: subFinal.success, workflowId: subInfo.workflowId, workflowName: subInfo.workflowName })

    # Extract any variables from the sub-workflow outputs
    * def extractedVars = Bridge.extractVariables(executionId, resolvedStep, subPayload, 200, {})

    * eval Bridge.onStepFinished(executionId, resolvedStep, subFinal.success, 200, resolvedStep.subWorkflowConfig, subFinal, extractedVars, [], subFinal.errorMessage)
