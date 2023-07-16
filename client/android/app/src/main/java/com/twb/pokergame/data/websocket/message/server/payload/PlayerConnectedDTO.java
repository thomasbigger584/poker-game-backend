package com.twb.pokergame.data.websocket.message.server.payload;

import androidx.annotation.NonNull;

import com.twb.pokergame.data.model.dto.playersession.PlayerSessionDTO;

public class PlayerConnectedDTO {
    private PlayerSessionDTO session;

    public PlayerSessionDTO getSession() {
        return session;
    }

    public void setSession(PlayerSessionDTO session) {
        this.session = session;
    }

    @NonNull
    @Override
    public String toString() {
        return "PlayerConnectedDTO{" +
                "session=" + session +
                '}';
    }
}
