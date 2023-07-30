package com.twb.pokergame.di.network;

import com.google.gson.Gson;
import com.twb.pokergame.data.auth.AuthService;
import com.twb.pokergame.data.websocket.WebSocketClient;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module(includes = NetworkModule.class)
@InstallIn(SingletonComponent.class)
public class WebSocketModule {

    @Provides
    @Singleton
    public WebSocketClient webSocketClient(AuthService authService, Gson gson) {
        return new WebSocketClient(authService, gson);
    }
}
