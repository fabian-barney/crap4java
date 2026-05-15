package media.barney.crap.core;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessTimeoutTest {

    @Test
    void destroysProcessWhenWaitIsInterrupted() {
        InterruptingProcess process = new InterruptingProcess();

        try {
            assertThrows(InterruptedException.class, () -> ProcessTimeout.waitForOrTerminate(
                    process,
                    List.of("git", "status"),
                    Duration.ofSeconds(5),
                    "Changed-file detection command"
            ));

            assertTrue(process.destroyed);
            assertEquals(2, process.timedWaitCalls);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static final class InterruptingProcess extends Process {
        private int timedWaitCalls;
        private boolean destroyed;

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            timedWaitCalls++;
            if (timedWaitCalls == 1) {
                throw new InterruptedException("test interruption");
            }
            return true;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyed = true;
            return this;
        }
    }
}
