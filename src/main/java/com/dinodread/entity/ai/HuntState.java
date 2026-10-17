package com.dinodread.entity.ai;

/** What the T-rex is currently doing. Server-side only for now; later milestones (sound, animation) read it. */
public enum HuntState {
    /** No quarry: strolling around. */
    WANDER,
    /** Heard a noise or lost sight: walking to the last known position. */
    INVESTIGATE,
    /** Sees its target and creeps closer without full speed. */
    STALK,
    /** Sees its target and runs it down at full speed. */
    CHARGE
}
