package tester.harness;

/** A {@code Runnable} whose body may throw — so {@link Harness#guard} can turn a thrown
 *  wrong-thread/wrong-region access into a recorded FAIL instead of an uncaught stall. */
@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Exception;
}
