package com.jarvis.language;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Intent labels understood by the planner. */
public enum Intent {
    GREETING,
    FAREWELL,
    HELP,
    STATUS,
    SCAN_HOSTILES,
    SCAN_AREA,
    LOCATE_STRUCTURE,
    LOCATE_BIOME,
    NAVIGATE_GUIDE,
    DISTANCE_QUERY,
    TRAVEL_TIME_QUERY,
    ORE_SCAN,
    MOD_QUESTION,
    MACHINE_QUESTION,
    SPELL_QUESTION,
    REMEMBER,
    FORGET,
    MESSAGE_PLAYER,
    MUTE,
    UNMUTE,
    VOICE_CONFIG,
    SKILL_CREATE,
    SKILL_LIST,
    MEMORY_QUERY,
    KNOWLEDGE_QUERY,
    CONFIRM,
    DENY,
    FOLLOW_UP,
    UNKNOWN
}
