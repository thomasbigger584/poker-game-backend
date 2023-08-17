package com.twb.pokergame.ui.activity.pokergame;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.twb.pokergame.R;
import com.twb.pokergame.data.model.dto.playersession.PlayerSessionDTO;
import com.twb.pokergame.data.websocket.message.server.payload.DealCommunityCardDTO;
import com.twb.pokergame.data.websocket.message.server.payload.DealPlayerCardDTO;
import com.twb.pokergame.data.websocket.message.server.payload.RoundFinishedDTO;
import com.twb.pokergame.ui.layout.CardPairLayout;
import com.twb.pokergame.ui.layout.CommunityCardLayout;

import java.util.HashMap;
import java.util.Map;

public class TableController {
    private static final String TAG = TableController.class.getSimpleName();
    private static final int TABLE_SIZE = 6;
    private final CardPairLayout[] cardPairLayouts = new CardPairLayout[TABLE_SIZE];
    private final Map<Integer, CardPairLayout> positionCardPairs = new HashMap<>();

    private final CommunityCardLayout communityCardLayout;

    public TableController(Activity activity) {
        cardPairLayouts[0] = activity.findViewById(R.id.playerCardPairLayout);
        cardPairLayouts[1] = activity.findViewById(R.id.tablePlayer1CardPairLayout);
        cardPairLayouts[2] = activity.findViewById(R.id.tablePlayer2CardPairLayout);
        cardPairLayouts[3] = activity.findViewById(R.id.tablePlayer3CardPairLayout);
        cardPairLayouts[4] = activity.findViewById(R.id.tablePlayer4CardPairLayout);
        cardPairLayouts[5] = activity.findViewById(R.id.tablePlayer5CardPairLayout);
        communityCardLayout = activity.findViewById(R.id.communityCardLayout);
    }

    public void connectCurrentPlayer(PlayerSessionDTO playerSession) {
        int playerPosition = playerSession.getPosition();

        positionCardPairs.clear();

        int index = 0;
        for (int thisPosition = playerPosition; thisPosition <= TABLE_SIZE; thisPosition++, index++) {
            positionCardPairs.put(thisPosition, cardPairLayouts[index]);
        }
        for (int thisPosition = 1; thisPosition < playerPosition; thisPosition++, index++) {
            positionCardPairs.put(thisPosition, cardPairLayouts[index]);
        }
        connectPlayer(playerSession, cardPairLayouts[0]);
    }

    public void connectOtherPlayer(PlayerSessionDTO playerSession) {
        CardPairLayout cardPairLayout = getCardPairLayout(playerSession.getPosition());
        connectPlayer(playerSession, cardPairLayout);
    }

    private void connectPlayer(PlayerSessionDTO playerSession, CardPairLayout cardPairLayout) {
        cardPairLayout.updateDetails(playerSession);
        Boolean dealer = playerSession.getDealer();
        cardPairLayout.updateDealerChip(dealer != null && dealer);
    }

    public void disconnectOtherPlayer(String username) {
        CardPairLayout cardPairLayout = findCardPairLayout(username);
        if (cardPairLayout != null) {
            cardPairLayout.deleteDetails();
        }
    }

    public void dealerDetermined(PlayerSessionDTO playerSession) {
        for (Map.Entry<Integer, CardPairLayout> posCardPairEntry : positionCardPairs.entrySet()) {
            Integer position = posCardPairEntry.getKey();
            CardPairLayout cardPairLayout = posCardPairEntry.getValue();
            cardPairLayout.updateDealerChip(position.equals(playerSession.getPosition()));
        }
    }

    public void dealCurrentPlayerCard(DealPlayerCardDTO dealPlayerCard) {
        PlayerSessionDTO playerSession = dealPlayerCard.getPlayerSession();
        CardPairLayout cardPairLayout = getCardPairLayout(playerSession.getPosition());
        if (playerSession.getUser().getUsername().equals(cardPairLayout.getUsername())) {
            cardPairLayout.updateCardImageView(dealPlayerCard.getCard());
        }
    }

    public void dealOtherPlayerCard(DealPlayerCardDTO dealPlayerCard) {
        PlayerSessionDTO playerSession = dealPlayerCard.getPlayerSession();
        CardPairLayout cardPairLayout = getCardPairLayout(playerSession.getPosition());
        if (playerSession.getUser().getUsername().equals(cardPairLayout.getUsername())) {
            cardPairLayout.updateCardImageView();
        }
    }

    public void dealCommunityCard(DealCommunityCardDTO dealCommunityCard) {
        communityCardLayout.dealCard(dealCommunityCard.getCard());
    }

    // ------------------------------------------------------------------------------

    @Nullable
    private CardPairLayout findCardPairLayout(String username) {
        for (CardPairLayout cardPairLayout : cardPairLayouts) {
            if (username.equals(cardPairLayout.getUsername())) {
                return cardPairLayout;
            }
        }
        return null;
    }

    @NonNull
    private CardPairLayout getCardPairLayout(int position) {
        if (!positionCardPairs.containsKey(position)) {
            throw new RuntimeException("Position is not part of the table for dealing: " + position);
        }
        CardPairLayout cardPairLayout = positionCardPairs.get(position);
        if (cardPairLayout == null) {
            throw new RuntimeException("Card pair layout is null for position: " + position);
        }
        return cardPairLayout;
    }

    public void reset(RoundFinishedDTO roundFinished) {
        communityCardLayout.reset();
        for (CardPairLayout cardPairLayout : cardPairLayouts) {
            cardPairLayout.reset();
        }
    }
}
