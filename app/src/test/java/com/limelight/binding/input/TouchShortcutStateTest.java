package com.limelight.binding.input;

import com.limelight.nvstream.input.KeyboardPacket;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import static org.junit.Assert.*;

public class TouchShortcutStateTest {
    private static final short CTRL = (short) 0x80A2;
    private static final short SHIFT = (short) 0x80A0;

    private static class Event {
        final short key;
        final boolean down;
        final byte modifiers;

        Event(short key, boolean down, byte modifiers) {
            this.key = key;
            this.down = down;
            this.modifiers = modifiers;
        }
    }

    private static class Fixture implements TouchShortcutState.Scheduler {
        final List<Event> events = new ArrayList<>();
        final Queue<Runnable> tasks = new ArrayDeque<>();
        final TouchShortcutState state = new TouchShortcutState(
                (key, down, modifiers) -> events.add(new Event(key, down, modifiers)), this);

        @Override
        public void postDelayed(Runnable task, long delayMs) {
            assertTrue("Keys need time to be observed by the game", delayMs >= 20);
            tasks.add(task);
        }

        @Override
        public void removeCallbacks(Runnable task) {
            tasks.removeIf(pending -> pending == task);
        }

        void tick() {
            assertFalse(tasks.isEmpty());
            tasks.remove().run();
        }

        void drain() {
            int limit = 1000;
            while (!tasks.isEmpty() && limit-- > 0) tick();
            assertTrue("Macro must terminate, not auto-repeat", tasks.isEmpty());
        }

        String typedDigits() {
            StringBuilder text = new StringBuilder();
            short downKey = 0;
            for (Event event : events) {
                int key = event.key & 0xFF;
                if (key < '0' || key > '9') continue;
                assertEquals(0, event.modifiers);
                if (event.down) {
                    assertEquals("Previous digit must have been released", 0, downKey);
                    downKey = event.key;
                    text.append((char) key);
                } else {
                    assertEquals(downKey, event.key);
                    downKey = 0;
                }
            }
            assertEquals("No held digit after completion or cancellation", 0, downKey);
            return text.toString();
        }
    }

    @Test
    public void firstConnectionNormalizesMultiTouchBeforeFirstClick() {
        assertEquals(1, TouchShortcutState.initialMouseMode(0, true, true));
        assertEquals(1, TouchShortcutState.initialMouseMode(2, true, true));
        assertEquals(1, TouchShortcutState.initialMouseMode(4, true, true));
    }

    @Test
    public void savedLeftOrRightMappingIsPreserved() {
        assertEquals(1, TouchShortcutState.initialMouseMode(1, true, true));
        assertEquals(5, TouchShortcutState.initialMouseMode(5, true, true));
    }

    @Test
    public void noOverlayOrExternalInputOnlyModeIsUnchanged() {
        assertEquals(0, TouchShortcutState.initialMouseMode(0, false, true));
        assertEquals(2, TouchShortcutState.initialMouseMode(2, true, false));
    }

    @Test
    public void sessionStartsWithNoModifiersOrQueuedInput() {
        Fixture f = new Fixture();
        assertFalse(f.state.isCtrlLocked());
        assertFalse(f.state.isShiftLocked());
        assertEquals(0, f.state.getModifiers());
        assertTrue(f.events.isEmpty());
        assertTrue(f.tasks.isEmpty());
    }

    @Test
    public void togglesSendLeftKeysWithBalancedPressAndRelease() {
        Fixture f = new Fixture();
        f.state.toggleCtrl();
        f.state.toggleShift();
        assertEquals(3, f.state.getModifiers());
        assertTrue(f.state.holdsModifier(CTRL));
        assertTrue(f.state.holdsModifier((short) 0xA0));
        assertFalse(f.state.holdsModifier((short) 0x80A3)); // right Ctrl is independent
        f.state.toggleCtrl();
        assertEquals(KeyboardPacket.MODIFIER_SHIFT, f.state.getModifiers());
        f.state.toggleShift();
        assertEquals(0, f.state.getModifiers());
        assertEquals(4, f.events.size());
        assertEquals(CTRL, f.events.get(0).key);
        assertTrue(f.events.get(0).down);
        assertEquals(SHIFT, f.events.get(1).key);
        assertTrue(f.events.get(1).down);
        assertEquals(CTRL, f.events.get(2).key);
        assertFalse(f.events.get(2).down);
        assertEquals(SHIFT, f.events.get(3).key);
        assertFalse(f.events.get(3).down);
    }

    @Test
    public void numberButtonsTypeExactlyTheRequestedDigits() {
        for (String digits : Arrays.asList("1000", "1000000")) {
            Fixture f = new Fixture();
            assertTrue(f.state.typeNumber(digits));
            f.drain();
            assertEquals(digits, f.typedDigits());
            assertEquals(digits.length() * 2, f.events.size());
            assertFalse(f.state.isTyping());
        }
    }

    @Test
    public void rapidNumberClicksAreSerializedNotInterleaved() {
        Fixture f = new Fixture();
        f.state.typeNumber("1000");
        f.tick(); // first digit is still held when the next macro is queued
        f.state.typeNumber("1000000");
        f.state.typeNumber("1000");
        f.drain();
        assertEquals("100010000001000", f.typedDigits());
    }

    @Test
    public void numericInputSuspendsAndRestoresBothLocks() {
        Fixture f = new Fixture();
        f.state.toggleCtrl();
        f.state.toggleShift();
        f.state.typeNumber("1000");
        assertTrue(f.state.isCtrlLocked());
        assertTrue(f.state.isShiftLocked());
        assertEquals(0, f.state.getModifiers());
        assertFalse(f.events.get(2).down);
        assertFalse(f.events.get(3).down);
        f.drain();
        assertEquals("1000", f.typedDigits());
        assertEquals(3, f.state.getModifiers());
        assertEquals(CTRL, f.events.get(12).key);
        assertTrue(f.events.get(12).down);
        assertEquals(SHIFT, f.events.get(13).key);
        assertTrue(f.events.get(13).down);
    }

    @Test
    public void changesDuringTypingRestoreOnlyTheLatestLocks() {
        Fixture f = new Fixture();
        f.state.toggleCtrl();
        f.state.typeNumber("1000");
        f.state.toggleCtrl();
        f.state.toggleShift();
        assertEquals(0, f.state.getModifiers());
        f.drain();
        assertFalse(f.state.isCtrlLocked());
        assertTrue(f.state.isShiftLocked());
        assertEquals(KeyboardPacket.MODIFIER_SHIFT, f.state.getModifiers());
        assertEquals(SHIFT, f.events.get(f.events.size() - 1).key);
    }

    @Test
    public void interruptingMacroReleasesDigitAndRestoresLocksBeforeUserClick() {
        Fixture f = new Fixture();
        f.state.toggleCtrl();
        f.state.typeNumber("1000000");
        f.tick();
        f.state.cancelTyping();
        assertEquals("1", f.typedDigits());
        assertEquals(KeyboardPacket.MODIFIER_CTRL, f.state.getModifiers());
        assertTrue(f.tasks.isEmpty());
        assertFalse(f.state.isTyping());
    }

    @Test
    public void teardownReleasesHeldDigitWithoutRestoringLocks() {
        Fixture f = new Fixture();
        f.state.toggleCtrl();
        f.state.toggleShift();
        f.state.typeNumber("1000000");
        f.tick();
        f.state.releaseAll();
        assertEquals("1", f.typedDigits());
        assertEquals(6, f.events.size()); // two modifier downs/ups, one digit down/up
        assertEquals(0, f.state.getModifiers());
        assertFalse(f.state.isCtrlLocked());
        assertFalse(f.state.isShiftLocked());
        assertTrue(f.tasks.isEmpty());
        f.state.releaseAll();
        assertEquals(6, f.events.size()); // repeated lifecycle cleanup is harmless
    }

    @Test
    public void teardownBeforeFirstDigitProducesNoText() {
        Fixture f = new Fixture();
        f.state.typeNumber("1000");
        f.state.releaseAll();
        assertTrue(f.tasks.isEmpty());
        assertTrue(f.events.isEmpty());
    }

    @Test
    public void staleCancelledCallbackCannotTypeOrRelock() {
        Fixture f = new Fixture();
        f.state.toggleShift();
        f.state.typeNumber("1000");
        Runnable stale = f.tasks.peek();
        f.state.releaseAll();
        stale.run();
        assertEquals(2, f.events.size());
        assertEquals(0, f.state.getModifiers());
        assertTrue(f.tasks.isEmpty());
    }

    @Test
    public void invalidOrExcessiveInputIsRejectedWithoutPartialEnqueue() {
        Fixture f = new Fixture();
        assertFalse(f.state.typeNumber(null));
        assertFalse(f.state.typeNumber(""));
        assertFalse(f.state.typeNumber("1\n"));
        assertFalse(f.state.typeNumber("Ctrl+A"));
        for (int i = 0; i < 20; i++) assertTrue(f.state.typeNumber("1000000"));
        assertFalse(f.state.typeNumber("1000"));
        f.drain();
        assertEquals(140, f.typedDigits().length());
    }
}
