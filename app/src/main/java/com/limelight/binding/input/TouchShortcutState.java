package com.limelight.binding.input;

import com.limelight.nvstream.input.KeyboardPacket;

/** Session-only input state for the touch shortcuts. All calls run on the UI thread. */
public final class TouchShortcutState {
    public static final int MOUSE_TOGGLE = 1001;
    public static final int CTRL_TOGGLE = 1002;
    public static final int SHIFT_TOGGLE = 1003;
    public static final int TYPE_1000 = 1004;
    public static final int TYPE_1000000 = 1005;

    private static final short LEFT_CTRL = (short) 0x80A2;
    private static final short LEFT_SHIFT = (short) 0x80A0;
    private static final int KEY_INTERVAL_MS = 40;
    private static final int MAX_PENDING_DIGITS = 140;

    public interface KeySink {
        void send(short key, boolean down, byte modifiers);
    }

    public interface Scheduler {
        void postDelayed(Runnable task, long delayMs);
        void removeCallbacks(Runnable task);
    }

    private final KeySink sink;
    private final Scheduler scheduler;
    private final StringBuilder pendingDigits = new StringBuilder();
    private final Runnable typeNext = this::typeNextDigit;
    private boolean ctrlLocked;
    private boolean shiftLocked;
    private boolean typing;
    private byte sentModifiers;
    private short heldDigit;

    public TouchShortcutState(KeySink sink, Scheduler scheduler) {
        this.sink = sink;
        this.scheduler = scheduler;
    }

    public static boolean isAction(int code) {
        return code >= MOUSE_TOGGLE && code <= TYPE_1000000;
    }

    public static int initialMouseMode(int currentMode, boolean shortcutsEnabled,
                                       boolean canChangeMode) {
        // Preserve an explicitly saved L/R mode. Other modes are incompatible with
        // this overlay's promise that a screen tap is a mouse click.
        return shortcutsEnabled && canChangeMode && currentMode != 1 && currentMode != 5
                ? 1 : currentMode;
    }

    public boolean isCtrlLocked() {
        return ctrlLocked;
    }

    public boolean isShiftLocked() {
        return shiftLocked;
    }

    public byte getModifiers() {
        return sentModifiers;
    }

    public boolean isTyping() {
        return typing;
    }

    public boolean holdsModifier(short key) {
        return ((key & 0xFF) == 0xA2 && (sentModifiers & KeyboardPacket.MODIFIER_CTRL) != 0)
                || ((key & 0xFF) == 0xA0 && (sentModifiers & KeyboardPacket.MODIFIER_SHIFT) != 0);
    }

    public void toggleCtrl() {
        ctrlLocked = !ctrlLocked;
        syncModifiers();
    }

    public void toggleShift() {
        shiftLocked = !shiftLocked;
        syncModifiers();
    }

    private void syncModifiers() {
        byte desired = 0;
        if (!typing) {
            if (ctrlLocked) desired |= KeyboardPacket.MODIFIER_CTRL;
            if (shiftLocked) desired |= KeyboardPacket.MODIFIER_SHIFT;
        }
        updateModifier(LEFT_CTRL, KeyboardPacket.MODIFIER_CTRL, desired);
        updateModifier(LEFT_SHIFT, KeyboardPacket.MODIFIER_SHIFT, desired);
    }

    private void updateModifier(short key, byte mask, byte desired) {
        if ((sentModifiers & mask) == (desired & mask)) return;
        boolean down = (desired & mask) != 0;
        sentModifiers = (byte) ((sentModifiers & ~mask) | (desired & mask));
        sink.send(key, down, sentModifiers);
    }

    public boolean typeNumber(String digits) {
        if (digits == null || !digits.matches("[0-9]+") ||
                pendingDigits.length() + digits.length() > MAX_PENDING_DIGITS) return false;
        pendingDigits.append(digits);
        if (!typing) {
            typing = true;
            // Digits must not turn into Ctrl shortcuts or Shift punctuation.
            // Keep the user's locks, but temporarily release their host key state.
            syncModifiers();
            scheduler.postDelayed(typeNext, KEY_INTERVAL_MS);
        }
        return true;
    }

    private void typeNextDigit() {
        if (!typing) return;
        if (heldDigit != 0) {
            sink.send(heldDigit, false, (byte) 0);
            heldDigit = 0;
        } else if (pendingDigits.length() != 0) {
            heldDigit = (short) (0x8000 | pendingDigits.charAt(0));
            pendingDigits.deleteCharAt(0);
            sink.send(heldDigit, true, (byte) 0);
        } else {
            typing = false;
            syncModifiers();
            return;
        }
        scheduler.postDelayed(typeNext, KEY_INTERVAL_MS);
    }

    public void cancelTyping() {
        scheduler.removeCallbacks(typeNext);
        pendingDigits.setLength(0);
        if (heldDigit != 0) {
            sink.send(heldDigit, false, (byte) 0);
            heldDigit = 0;
        }
        typing = false;
        syncModifiers();
    }

    public void releaseAll() {
        // Clear desired locks BEFORE cancelling, so teardown never presses them again.
        ctrlLocked = shiftLocked = false;
        cancelTyping();
    }
}
