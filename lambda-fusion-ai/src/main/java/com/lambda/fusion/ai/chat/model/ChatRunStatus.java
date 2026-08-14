package com.lambda.fusion.ai.chat.model;

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
}
