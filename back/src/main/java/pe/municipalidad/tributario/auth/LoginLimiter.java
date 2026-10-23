package pe.municipalidad.tributario.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LoginLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int LIMIT = 5;
    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public boolean blocked(String key) {
        Deque<Instant> times = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            prune(times);
            return times.size() >= LIMIT;
        }
    }

    public void failed(String key) {
        Deque<Instant> times = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            prune(times);
            times.addLast(Instant.now());
        }
    }

    public void succeeded(String key) {
        attempts.remove(key);
    }

    private void prune(Deque<Instant> times) {
        Instant oldest = Instant.now().minus(WINDOW);
        while (!times.isEmpty() && times.peekFirst().isBefore(oldest)) times.removeFirst();
    }
}
