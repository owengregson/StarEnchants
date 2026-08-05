package engine.sink;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.stores.EngineStores;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import platform.sched.Scheduling;
import platform.text.Colors;
import testfx.Envs;
import testfx.SyncSchedulerBackend;

/**
 * R-QC30r: the VANISH end line rides the window's ONE restore closure, so every route that can end a window
 * prints it and none prints it twice. Four routes end a vanish (the timer, the exhausting hit, a re-proc
 * superseding it, and the quit sweep) — a line hung off the timer alone would go missing on the other three.
 */
class VanishEndMessageTest {

    private static final String LINE = "&4&l* Feign Death - UNVANISHED *";

    private EngineStores stores;
    private RecordingSink sink;
    private Player subject;
    private UUID subjectId;

    @BeforeEach
    void setUp() {
        Scheduling.install(new SyncSchedulerBackend());
        stores = EngineStores.fresh();
        sink = new RecordingSink(Envs.sink().stores(stores).build());
        subjectId = UUID.randomUUID();
        subject = mock(Player.class);
        when(subject.getUniqueId()).thenReturn(subjectId);
    }

    @Test
    void theQuitSweepPrintsTheEndLineOnceAndTheStaleTimerThenPrintsNothing() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(() -> Bukkit.getPlayer(subjectId)).thenReturn(subject);

            // The inline scheduler runs the timer's close immediately after the arm, so this single call already
            // exercises "the window ends" once; the sweep below is the second, competing route.
            sink.vanish(subject, 40, 1, "", LINE);
            sink.flush(); // the arm is a planned entity op; the inline scheduler then runs the timer's close
            stores.vanish().clear(subjectId);

            verify(subject, times(1)).sendMessage(Colors.translate(LINE));
        }
    }

    @Test
    void anEmptyEndMessageStaysSilent() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(() -> Bukkit.getPlayer(subjectId)).thenReturn(subject);

            sink.vanish(subject, 40, 1, "", "");
            sink.flush();
            stores.vanish().clear(subjectId);

            verify(subject, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
        }
    }
}
