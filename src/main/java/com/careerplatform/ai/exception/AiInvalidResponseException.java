package com.careerplatform.ai.exception;

/** Raised when a provider response cannot be parsed as the requested type. */
public class AiInvalidResponseException extends AiException {
    private final Rule rule;

    public enum Stage {
        TOOL_CONFIGURATION, TOOL_ARGUMENT_VALIDATION, TOOL_EXECUTION,
        FINAL_RESPONSE_PARSE, SERVICE_RESULT_VALIDATION, RESULT_KEY_RESOLUTION
    }

    /** Closed vocabulary: never populate diagnostics from model or provider data. */
    public enum Rule {
        TOOL_CONFIGURATION_INVALID(Stage.TOOL_CONFIGURATION),
        TOOL_RESPONSE_INVALID(Stage.TOOL_CONFIGURATION),
        TOOL_NAME_INVALID(Stage.TOOL_CONFIGURATION),
        REQUIRED_TOOL_MISSING(Stage.TOOL_CONFIGURATION),
        TOOL_ARGUMENTS_INVALID_JSON(Stage.TOOL_ARGUMENT_VALIDATION),
        TOOL_ARGUMENTS_INVALID(Stage.TOOL_ARGUMENT_VALIDATION),
        TOOL_ARGUMENTS_EXTRA_FIELD(Stage.TOOL_ARGUMENT_VALIDATION),
        MAX_RESULTS_MISSING(Stage.TOOL_ARGUMENT_VALIDATION),
        MAX_RESULTS_TYPE_INVALID(Stage.TOOL_ARGUMENT_VALIDATION),
        MAX_RESULTS_OUT_OF_RANGE(Stage.TOOL_ARGUMENT_VALIDATION),
        TOOL_BUDGET_EXCEEDED(Stage.TOOL_EXECUTION),
        MODEL_BUDGET_EXCEEDED(Stage.TOOL_EXECUTION),
        TOOL_HISTORY_EMPTY(Stage.TOOL_EXECUTION),
        TOOL_EXECUTION_FAILED(Stage.TOOL_EXECUTION),
        FINAL_RESPONSE_EMPTY(Stage.FINAL_RESPONSE_PARSE),
        FINAL_RESPONSE_JSON_INVALID(Stage.FINAL_RESPONSE_PARSE),
        FINAL_RESPONSE_CLEANER_FAILED(Stage.FINAL_RESPONSE_PARSE),
        FINAL_RESPONSE_JSON_SYNTAX_INVALID(Stage.FINAL_RESPONSE_PARSE),
        FINAL_RESPONSE_MAPPING_FAILED(Stage.FINAL_RESPONSE_PARSE),
        FINAL_RESPONSE_UNKNOWN_PROPERTY(Stage.FINAL_RESPONSE_PARSE),
        FINAL_RESPONSE_DESERIALIZATION_FAILED(Stage.FINAL_RESPONSE_PARSE),
        CANDIDATES_NULL(Stage.SERVICE_RESULT_VALIDATION),
        CANDIDATE_COUNT_EXCEEDED(Stage.SERVICE_RESULT_VALIDATION),
        CANDIDATE_INVALID(Stage.SERVICE_RESULT_VALIDATION),
        RESULT_KEY_DUPLICATE(Stage.SERVICE_RESULT_VALIDATION),
        RANK_INVALID(Stage.SERVICE_RESULT_VALIDATION),
        SERVICE_RESULT_NULL(Stage.SERVICE_RESULT_VALIDATION),
        WARNINGS_COUNT_EXCEEDED(Stage.SERVICE_RESULT_VALIDATION),
        WARNING_INVALID(Stage.SERVICE_RESULT_VALIDATION),
        ADVICE_LIST_NULL(Stage.SERVICE_RESULT_VALIDATION),
        ADVICE_LIST_COUNT_EXCEEDED(Stage.SERVICE_RESULT_VALIDATION),
        ADVICE_ITEM_INVALID(Stage.SERVICE_RESULT_VALIDATION),
        SOURCE_FACTS_INVALID(Stage.SERVICE_RESULT_VALIDATION),
        UNKNOWN_RESULT_KEY(Stage.RESULT_KEY_RESOLUTION);

        private final Stage stage;

        Rule(Stage stage) {
            this.stage = stage;
        }
    }

    /** M6C diagnostics carry only a fixed rule and deliberately have no cause. */
    public AiInvalidResponseException(Rule rule, String message) {
        super(message, null);
        this.rule = java.util.Objects.requireNonNull(rule, "rule must not be null");
    }

    public Rule getRuleId() {
        return rule;
    }

    public Stage getStage() {
        return rule == null ? null : rule.stage;
    }

    public AiInvalidResponseException(String message) {
        super(message);
        this.rule = null;
    }

    public AiInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
        this.rule = null;
    }
}
