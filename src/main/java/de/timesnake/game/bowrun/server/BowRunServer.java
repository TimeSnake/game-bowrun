package de.timesnake.game.bowrun.server;

import de.timesnake.basic.bukkit.util.user.ExItemStack;
import de.timesnake.basic.bukkit.util.user.User;
import de.timesnake.basic.bukkit.util.user.scoreboard.Sideboard;
import de.timesnake.basic.loungebridge.util.server.LoungeBridgeServer;

public class BowRunServer extends LoungeBridgeServer {

    private static final BowRunServerManager server = BowRunServerManager.getInstance();

    public static BowRunMap getMap() {
        return server.getMap();
    }

    public static BowRunGame getGame() {
        return server.getGame();
    }

    public static void stopGame(BowRunServerManager.WinType winType, User finisher) {
        server.stopGame(winType, finisher);
    }

    public static void setGameSideboardScore(int line, String text) {
        server.setGameSideboardScore(line, text);
    }

    public static void setSpectatorSideboardScore(int line, String text) {
        server.setSpectatorSideboardScore(line, text);
    }

    public static Sideboard getGameSideboard() {
        return server.getGameSideboard();
    }

    public static void updateGameTimeOnSideboard() {
        server.updateGameTimeOnSideboard();
    }

    public static ExItemStack getRandomRunnerItem() {
        return server.getRandomRunnerItem();
    }

    public static ExItemStack getRandomArcherItem() {
        return server.getRandomArcherItem();
    }

    public static void addRunnerDeath() {
        server.addRunnerDeath();
    }

    public static int getRunnerDeaths() {
        return server.getRunnerDeaths();
    }

    public static Integer getPlayingTime() {
        return server.getPlayingTime();
    }

    public static void setPlayingTime(Integer playingTime) {
        server.setPlayingTime(playingTime);
    }

}
