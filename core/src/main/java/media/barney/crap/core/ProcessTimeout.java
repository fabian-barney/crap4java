package media.barney.crap.core;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ProcessTimeout {

    static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(5);

    private ProcessTimeout() {
    }

    static void validate(Duration timeout) {
        timeoutNanos(timeout);
    }

    static int waitForOrTerminate(Process process,
                                  List<String> command,
                                  Duration timeout,
                                  String commandDescription) throws InterruptedException {
        long timeoutNanos = timeoutNanos(timeout);
        try {
            if (process.waitFor(timeoutNanos, TimeUnit.NANOSECONDS)) {
                return process.exitValue();
            }
            String commandText = String.join(" ", command);
            process.destroyForcibly();
            if (!process.waitFor(CLEANUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(commandDescription + " timed out after " + timeout
                        + " and could not be terminated within " + CLEANUP_TIMEOUT + ": " + commandText);
            }
            throw new IllegalStateException(commandDescription + " timed out after " + timeout + ": " + commandText);
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            try {
                process.waitFor(CLEANUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
            }
            Thread.currentThread().interrupt();
            throw ex;
        }
    }

    private static long timeoutNanos(Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Command timeout must be positive: " + timeout);
        }
        try {
            return timeout.toNanos();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Command timeout is too large: " + timeout, ex);
        }
    }
}
