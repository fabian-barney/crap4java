package media.barney.crap.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandResultTest {

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullCapturedOutput() {
        assertThrows(NullPointerException.class, () -> new CommandResult(1, null, ""));
        assertThrows(NullPointerException.class, () -> new CommandResult(1, "", null));
    }
}
