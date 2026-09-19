package com.jarvis.skills;

/**
 * Sandboxed skill operation set. Dynamically created skills may ONLY compose
 * these approved operations - never arbitrary code. Each op maps to a
 * reviewed implementation in {@link SkillExecutor}.
 */
public enum SkillOp {
    OBSERVE_ENTITY,
    FILTER_ENTITY,
    GET_POSITION,
    CALCULATE_DISTANCE,
    CALCULATE_DIRECTION,
    QUERY_REGISTRY,
    QUERY_RECIPE,
    QUERY_BLOCK,
    QUERY_ITEM,
    QUERY_WORLD,
    NAVIGATE,
    SPEAK,
    DISPLAY,
    STORE_MEMORY,
    CALL_APPROVED_API
}
