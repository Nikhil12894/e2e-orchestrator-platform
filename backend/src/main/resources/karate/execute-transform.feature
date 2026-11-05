@lock=workflow-execution
Feature: Execute JavaScript Transform Step

  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')
    * def transCfg = resolvedStep.transformConfig || {}
    * def jsScript = transCfg.script || "function(ctx) { return {}; }"

  Scenario: Run Transform
    * def evalFn =
      """
      function(code, ctx) {
        try {
          var fn = karate.eval('(' + code + ')');
          if (typeof fn === 'function') {
            return fn(ctx);
          }
          return fn;
        } catch (e) {
          karate.log('Transform script evaluation error:', e);
          return { error: '' + e };
        }
      }
      """
    * def output = evalFn(jsScript, globalContext)
    * def isObj = (typeof output === 'object' && output !== null && !Array.isArray(output))
    * def extractedVars = isObj ? output : { transformResult: output }
    * eval Bridge.onStepFinished(executionId, resolvedStep, true, 200, jsScript, output, extractedVars, [], null)
