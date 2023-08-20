package com.twb.pokergame.service.game;

import com.antkorwin.xsync.XSync;
import com.twb.pokergame.domain.AppUser;
import com.twb.pokergame.domain.PlayerSession;
import com.twb.pokergame.domain.PokerTable;
import com.twb.pokergame.domain.enumeration.ConnectionType;
import com.twb.pokergame.dto.playersession.PlayerSessionDTO;
import com.twb.pokergame.repository.PlayerSessionRepository;
import com.twb.pokergame.repository.TableRepository;
import com.twb.pokergame.repository.UserRepository;
import com.twb.pokergame.service.PlayerSessionService;
import com.twb.pokergame.service.game.thread.GameThread;
import com.twb.pokergame.service.game.thread.GameThreadManager;
import com.twb.pokergame.web.websocket.message.MessageDispatcher;
import com.twb.pokergame.web.websocket.message.server.ServerMessageDTO;
import com.twb.pokergame.web.websocket.message.server.ServerMessageFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional
@RequiredArgsConstructor
public class GameConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(GameConnectionService.class);

    private final UserRepository userRepository;
    private final TableRepository tableRepository;
    private final PlayerSessionRepository playerSessionRepository;

    private final PlayerSessionService playerSessionService;

    private final GameThreadManager threadManager;
    private final ServerMessageFactory messageFactory;
    private final MessageDispatcher dispatcher;
    private final XSync<UUID> mutex;

    public ServerMessageDTO onUserConnected(UUID tableId, ConnectionType connectionType, String username) {
        return mutex.evaluate(tableId, () -> {
            Optional<PokerTable> pokerTableOpt = tableRepository.findById(tableId);
            if (pokerTableOpt.isEmpty()) {
                String message = String.format("Failed to connect user %s to table %s as table not found", username, tableId);
                throw new RuntimeException(message);
            }

            Optional<AppUser> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                String message = String.format("Failed to connect user %s to table %s as user not found", username, tableId);
                throw new RuntimeException(message);
            }

            Optional<PlayerSession> playerSessionOpt = playerSessionRepository.findByTableIdAndUsername(tableId, username);
            if (playerSessionOpt.isPresent()) {
                String message = String.format("User %s already connected to table %s", username, tableId);
                throw new RuntimeException(message);
            }

            PokerTable pokerTable = pokerTableOpt.get();
            AppUser appUser = userOpt.get();

            if (connectionType == ConnectionType.PLAYER) {
                threadManager.createIfNotExist(pokerTable);
            }

            PlayerSessionDTO connectedPlayerSession = playerSessionService.connectUserToRound(appUser, connectionType, pokerTable);
            List<PlayerSessionDTO> allPlayerSessions = playerSessionService.getByTableId(tableId);

            dispatcher.send(tableId, messageFactory.playerConnected(connectedPlayerSession));
            return messageFactory.playerSubscribed(allPlayerSessions);
        });
    }

    public void onUserDisconnected(UUID tableId, String username) {
        mutex.execute(tableId, () -> {
            playerSessionService.disconnectUser(tableId, username);
            Optional<GameThread> threadOpt = threadManager.getIfExists(tableId);
            if (threadOpt.isPresent()) {
                GameThread thread = threadOpt.get();
                List<PlayerSession> playerSessions =
                        playerSessionRepository.findConnectedPlayersByTableIdNoLock(tableId);
                if (CollectionUtils.isEmpty(playerSessions)) {
                    thread.stopThread();
                } else {
                    thread.onPlayerDisconnected(username);
                }
            }
            ServerMessageDTO message =
                    messageFactory.playerDisconnected(username);
            dispatcher.send(tableId, message);
        });
    }
}
