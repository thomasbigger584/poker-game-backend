package com.twb.pokergame.service.game;

import com.twb.pokergame.domain.PokerTable;
import com.twb.pokergame.repository.PokerTableRepository;
import com.twb.pokergame.web.websocket.message.MessageDispatcher;
import com.twb.pokergame.web.websocket.message.server.ServerMessageFactory;
import com.twb.pokergame.web.websocket.message.server.ServerMessageType;
import com.twb.pokergame.web.websocket.message.server.ServerMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Scope("prototype")
@RequiredArgsConstructor
public class PokerGameRunnable implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(PokerGameRunnable.class);

    private final String pokerTableId;

    @Autowired
    private ServerMessageFactory messageFactory;

    @Autowired
    private MessageDispatcher dispatcher;

    @Override
    public void run() {
        logger.info("Starting Game Runnable: {} ", pokerTableId);

        for (int index = 0; index < 3000; index++) {
            System.out.println("index = " + index);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }


        logger.info("Finishing Game Runnable: {} ", pokerTableId);
    }

    public void onPlayerConnected(String username) {
        logger.info("Game Runnable: Player Connected" + username);
    }

    public void onPlayerDisconnected(String username) {
        logger.info("Game Runnable: Player Disconnected" + username);
    }
}
