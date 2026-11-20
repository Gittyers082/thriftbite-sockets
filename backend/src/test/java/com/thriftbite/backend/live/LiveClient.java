package com.thriftbite.backend.live;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A WebSocket client for tests. It uses the JDK's own client, so it shares no
 * code with the server it's testing.
 */
final class LiveClient implements WebSocket.Listener, AutoCloseable {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private final StringBuilder partial = new StringBuilder();
    private WebSocket socket;

    /**
     * @param origin the website the "browser" says the page came from, or null to send none
     * @throws java.util.concurrent.ExecutionException if the server refuses the connection
     */
    static LiveClient connect(int port, String origin) throws Exception {
        LiveClient client = new LiveClient();
        WebSocket.Builder builder = HTTP.newWebSocketBuilder();
        if (origin != null) {
            builder.header("Origin", origin);
        }
        client.socket = builder.buildAsync(URI.create("ws://localhost:" + port + "/ws"), client)
                .get(10, TimeUnit.SECONDS);
        return client;
    }

    /** Sends the login token and waits for the server to accept it. */
    LiveClient logIn(String token) throws Exception {
        send("{\"type\":\"auth\",\"token\":\"" + token + "\"}");
        await("\"ready\"");
        return this;
    }

    void send(String text) throws Exception {
        socket.sendText(text, true).get(5, TimeUnit.SECONDS);
    }

    /** Waits for a message containing {@code text} and returns it. Fails if none arrives in 5 seconds. */
    String await(String text) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<String> others = new ArrayList<>();
        while (true) {
            long left = deadline - System.nanoTime();
            String message = left > 0 ? messages.poll(left, TimeUnit.NANOSECONDS) : null;
            if (message == null) {
                throw new AssertionError("No live message containing " + text + " arrived. Got instead: " + others);
            }
            if (message.contains(text)) {
                return message;
            }
            others.add(message);
        }
    }

    /** Waits {@code millis}, then returns (and forgets) everything that has arrived so far. */
    List<String> collect(long millis) throws InterruptedException {
        Thread.sleep(millis);
        List<String> all = new ArrayList<>();
        messages.drainTo(all);
        return all;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        partial.append(data);
        if (last) {
            messages.add(partial.toString());
            partial.setLength(0);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public void close() {
        if (socket != null) {
            socket.abort();
        }
    }
}
