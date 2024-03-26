package com.twb.pokerapp.utils.game.player;

import com.twb.pokerapp.domain.enumeration.ConnectionType;
import com.twb.pokerapp.utils.keycloak.KeycloakHelper;
import com.twb.pokerapp.utils.message.ServerMessageConverter;
import com.twb.pokerapp.web.websocket.message.client.CreatePlayerActionDTO;
import com.twb.pokerapp.web.websocket.message.server.ServerMessageDTO;
import com.twb.pokerapp.web.websocket.message.server.payload.ErrorMessageDTO;
import com.twb.pokerapp.web.websocket.message.server.payload.LogMessageDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractTestUser implements StompSessionHandler, StompFrameHandler {
    private static final Logger logger = LoggerFactory.getLogger(AbstractTestUser.class);
    private static final String CONNECTION_URL = "ws://localhost:8081/looping";
    private static final String SUBSCRIPTION_TOPIC_SUFFIX = "/topic/loops.%s";
    private static final String SEND_PLAYER_ACTION = "/app/pokerTable/%s/sendPlayerAction";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_CONNECTION_TYPE = "X-Connection-Type";
    private static final TaskScheduler TASK_SCHEDULER =
            new ConcurrentTaskScheduler(Executors.newSingleThreadScheduledExecutor());
    protected final CountdownLatches latches;
    private final UUID tableId;
    @Getter
    protected final String username;
    private final WebSocketStompClient client;
    private final Keycloak keycloak;
    private final CountDownLatch connectLatch = new CountDownLatch(1);
    @Getter
    private final AtomicReference<Throwable> exceptionThrown = new AtomicReference<>();
    @Getter
    private final List<ServerMessageDTO> receivedMessages = Collections.synchronizedList(new ArrayList<>());
    private StompSession session;

    public AbstractTestUser(UUID tableId, CountdownLatches latches,
                            String username, String password) {
        this.tableId = tableId;
        this.latches = latches;
        this.username = username;
        this.client = createClient();
        this.keycloak = KeycloakHelper.getKeycloak(username, password);
        this.session = null;
    }

    public void connect() throws InterruptedException {
        logger.info("Connecting {} to {}", username, tableId);
        URI url = URI.create(CONNECTION_URL);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        String accessToken = KeycloakHelper.getAccessToken(keycloak);
        headers.add(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken);

        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.put(HEADER_CONNECTION_TYPE, Collections.singletonList(getConnectionType().toString()));

        client.connectAsync(url, headers, stompHeaders, this);
        connectLatch.await(10, TimeUnit.SECONDS);
    }

    public void disconnect() {
        if (session != null && session.isConnected()) {
            logger.info("Disconnecting {} from {}", username, tableId);
            session.disconnect();
        }
        session = null;
    }

    // ***************************************************************
    // Interface Methods
    // ***************************************************************

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        session.setAutoReceipt(true);
        session.subscribe(String.format(SUBSCRIPTION_TOPIC_SUFFIX, tableId), this);
        this.session = session;
    }

    @Override
    public void handleException(StompSession session, StompCommand command,
                                StompHeaders headers, byte[] payload, Throwable exception) {
        logger.error("Exception thrown during stomp session", exception);
        exceptionThrown.set(exception);
        connectLatch.countDown();
        latches.roundLatch().countDown();
        latches.gameLatch().countDown();
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        logger.error("Exception thrown after connect failure", exception);
        exceptionThrown.set(exception);
        connectLatch.countDown();
        latches.roundLatch().countDown();
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return ServerMessageDTO.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        if (payload == null) {
            logger.warn("Frame received but payload is null with headers {}", headers);
            return;
        }
        connectLatch.countDown();
        ServerMessageDTO message = (ServerMessageDTO) payload;
        receivedMessages.add(message);

        if (message.getPayload() instanceof ErrorMessageDTO error) {
            logger.error("{} received error message: {}", username, error.getMessage());
            return;
        } else if (message.getPayload() instanceof LogMessageDTO log) {
            logger.info("{} received log message: {}", username, log.getMessage());
            return;
        }
        handleMessage(headers, message);
    }

    // ***************************************************************
    // Send Methods
    // ***************************************************************

    protected void sendPlayerAction(CreatePlayerActionDTO createDto) {
        send(String.format(SEND_PLAYER_ACTION, tableId), createDto);
    }

    private void send(String destination, Object dto) {
        if (session == null || !session.isConnected()) {
            logger.warn("Cannot send to destination {} for user {} as not connected", destination, username);
            return;
        }
        logger.info(">>>> [{}] sending {}", username, dto);
        StompSession.Receiptable receiptable = session.send(destination, dto);
        receiptable.addReceiptTask(() -> logger.info("Receipt received for user {} destination {} and payload {}", username, destination, dto));
        receiptable.addReceiptLostTask(() -> logger.info("Failed to receive receipt for user {} destination {} and payload {}", username, destination, dto));
    }

    // ***************************************************************
    // Abstract Methods
    // ***************************************************************

    protected abstract void handleMessage(StompHeaders headers, ServerMessageDTO message);

    protected abstract ConnectionType getConnectionType();

    // ***************************************************************
    // Helper Methods
    // ***************************************************************

    @NotNull
    private WebSocketStompClient createClient() {
        List<Transport> transports = new ArrayList<>(2);
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        transports.add(new RestTemplateXhrTransport());

        SockJsClient sockJsClient = new SockJsClient(transports);
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setTaskScheduler(TASK_SCHEDULER);
        stompClient.setMessageConverter(new ServerMessageConverter());
        return stompClient;
    }

    public record CountdownLatches(
            CountDownLatch roundLatch,
            CountDownLatch gameLatch
    ) {
        private static final int SINGLE = 1;

        public static CountdownLatches create() {
            CountDownLatch roundLatch = new CountDownLatch(SINGLE);
            CountDownLatch gameLatch = new CountDownLatch(SINGLE);
            return new CountdownLatches(roundLatch, gameLatch);
        }
    }
}
