package com.lambda.fusion.ai.chat.model;

import java.util.List;
import java.util.Set;

/** 对话业务 Run 状态。 */
public enum ChatRunStatus {
    CREATED,
    RUNNING,
    AWAITING_CONFIRM,
    STOPPING,
    COMPLETED,
    STOPPED,
    FAILED;

    private static final Set<ChatRunStatus> TERMINAL = Set.of(COMPLETED, STOPPED, FAILED);

    private static final List<String> TERMINAL_NAMES =
            TERMINAL.stream().map(Enum::name).toList();

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public static boolean isTerminal(String status) {
        if (status == null) {
            return false;
        }
        try {
            return valueOf(status).isTerminal();
        } catch (IllegalArgumentException invalidStatus) {
            return false;
        }
    }

    /** 终态状态名列表，供 SQL in/notIn 条件复用，避免各处手写终态三元组。 */
    public static List<String> terminalNames() {
        return TERMINAL_NAMES;
    }
}
