package su.twomc.staffwork.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommandInputTest {
    @Test
    void validatesPlayerNamesAndUuidWithoutThrowing() {
        assertTrue(CommandInput.validPlayerName("Valid_Name"));
        assertFalse(CommandInput.validPlayerName("../../server"));
        assertFalse(CommandInput.validPlayerName("имя"));
        assertTrue(CommandInput.uuid("00000000-0000-0000-0000-000000000001").isPresent());
        assertTrue(CommandInput.uuid("not-a-uuid").isEmpty());
    }
}
