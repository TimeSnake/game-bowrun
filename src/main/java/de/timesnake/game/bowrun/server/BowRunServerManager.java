/*
 * game-bowrun.main
 * Copyright (C) 2022 timesnake
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; If not, see <http://www.gnu.org/licenses/>.
 */

package de.timesnake.game.bowrun.server;

import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.ServerManager;
import de.timesnake.basic.bukkit.util.chat.ChatColor;
import de.timesnake.basic.bukkit.util.user.ExItemStack;
import de.timesnake.basic.bukkit.util.user.User;
import de.timesnake.basic.bukkit.util.user.scoreboard.Sideboard;
import de.timesnake.basic.game.util.Team;
import de.timesnake.basic.game.util.TeamUser;
import de.timesnake.basic.loungebridge.util.server.LoungeBridgeServerManager;
import de.timesnake.basic.loungebridge.util.tool.GameTool;
import de.timesnake.basic.loungebridge.util.tool.StartableTool;
import de.timesnake.basic.loungebridge.util.tool.StopableTool;
import de.timesnake.basic.loungebridge.util.tool.TimerTool;
import de.timesnake.basic.loungebridge.util.user.GameUser;
import de.timesnake.basic.loungebridge.util.user.Kit;
import de.timesnake.database.util.Database;
import de.timesnake.database.util.game.DbGame;
import de.timesnake.database.util.game.DbTmpGame;
import de.timesnake.game.bowrun.chat.Plugin;
import de.timesnake.game.bowrun.main.GameBowRun;
import de.timesnake.game.bowrun.user.BowRunUser;
import de.timesnake.game.bowrun.user.UserManager;
import de.timesnake.library.basic.util.Status;
import de.timesnake.library.basic.util.chat.ExTextColor;
import de.timesnake.library.basic.util.statistics.StatPeriod;
import de.timesnake.library.basic.util.statistics.StatType;
import de.timesnake.library.extension.util.chat.Chat;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

public class BowRunServerManager extends LoungeBridgeServerManager<BowRunGame> implements Listener {

    public static BowRunServerManager getInstance() {
        return (BowRunServerManager) ServerManager.getInstance();
    }

    private final RecordVerification recordVerification = new RecordVerification();
    private Sideboard sideboard;
    private Sideboard spectatorSideboard;
    private BossBar timeBar;
    private int runnerDeaths = 0;
    private boolean stopAfterStart = false;
    private TimerTool timerTool;
    private List<Boolean> runnerArmor;
    private UserManager userManager;
    private RelayManager relayManager;
    private ArrowGenerator arrowGenerator;
    private BowRunServer.WinType winType;
    private BowRunUser finisher;

    public void onBowRunEnable() {
        super.onLoungeBridgeEnable();
        super.setTeamMateDamage(false);

        Server.registerListener(this, GameBowRun.getPlugin());

        this.userManager = new UserManager();

        this.relayManager = new RelayManager();

        this.sideboard = Server.getScoreboardManager().registerNewSideboard("bowrun", "§6§lBowRun");
        this.sideboard.setScore(4, BowRunServer.SIDEBOARD_TIME_TEXT);
        //time (method after spec sideboard)
        this.sideboard.setScore(2, "§r§f-----------");
        // kills/deaths
        // kills/deaths amount

        this.spectatorSideboard = Server.getScoreboardManager().registerNewSideboard("bowrunSpectator", "§6§lBowRun");
        this.spectatorSideboard.setScore(4, BowRunServer.SIDEBOARD_TIME_TEXT);
        // time
        this.spectatorSideboard.setScore(2, "§r§f-----------");
        this.spectatorSideboard.setScore(1, BowRunServer.SIDEBOARD_MAP_TEXT);
        // map

        this.timeBar = Server.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);

        Color color = this.getGame().getRunnerTeam().getColor();
        BowRunServer.armor = List.of(
                new ExItemStack(Material.GOLDEN_BOOTS).addExEnchantment(Enchantment.PROTECTION_PROJECTILE, 1),
                new ExItemStack(Material.GOLDEN_LEGGINGS).addExEnchantment(Enchantment.PROTECTION_PROJECTILE, 2),
                new ExItemStack(Material.GOLDEN_CHESTPLATE).addExEnchantment(Enchantment.PROTECTION_PROJECTILE, 2),
                ExItemStack.getLeatherArmor(Material.LEATHER_HELMET, color));


        this.timerTool = new TimerTool() {
            @Override
            public void onTimerUpdate() {
                updateGameTimeOnSideboard();
                if (this.time % 20 == 0) {
                    BowRunServerManager.this.giveArcherSpecialItems();
                }

                if ((this.time % 60) == 0) {
                    Server.broadcastNote(BowRunServer.TIME_INSTRUMENT, BowRunServer.TIME_NOTE);
                    Server.broadcastTitle(Component.empty(), Component.text(this.time / 60 + " min left"),
                            Duration.ofSeconds(2));
                }
            }

            @Override
            public void onTimerEnd() {
                BowRunServerManager.this.winType = BowRunServer.WinType.ARCHER_TIME;
                BowRunServerManager.this.finisher = null;
                Server.runTaskSynchrony(BowRunServerManager.this::stopGame, GameBowRun.getPlugin());
            }
        };
        super.getToolManager().add(this.timerTool);

        this.updateGameTimeOnSideboard();

        this.arrowGenerator = new ArrowGenerator();
        super.getToolManager().add(this.arrowGenerator);
    }

    @Override
    public BowRunUser loadUser(Player player) {
        return new BowRunUser(player);
    }

    @Override
    protected BowRunGame loadGame(DbGame dbGame, boolean loadWorlds) {
        return new BowRunGame((DbTmpGame) dbGame, true);
    }

    @Override
    public de.timesnake.library.extension.util.chat.Plugin getGamePlugin() {
        return Plugin.BOWRUN;
    }

    @Override
    public void onGamePrepare() {
        this.updateGameTimeOnSideboard();
        this.updateMapOnSideboard();
    }


    @Override
    public void onMapLoad() {
        BowRunMap map = BowRunServer.getMap();
        map.getWorld().setTime(map.isTimeNight() ? 19000 : 1000);
        this.updateGameTimeOnSideboard();
        if (map.getBestTime() != null) {
            String recordTime = Chat.getTimeString(map.getBestTime());
            if (map.getBestTimeUser() != null) {
                BowRunServer.getGameTablist().setFooter(ChatColor.GOLD + "Record: " + ChatColor.BLUE + recordTime +
                        ChatColor.GOLD + " by " + ChatColor.BLUE + Database.getUsers().getUser(map.getBestTimeUser()).getName());
            } else {
                BowRunServer.getGameTablist().setFooter(ChatColor.GOLD + "Record: " + ChatColor.BLUE + "- - -");
            }

            String playTime = Chat.getTimeString(this.getPlayingTime());

            this.timeBar.setTitle(playTime);
            this.timeBar.setColor(BarColor.GREEN);
            this.timeBar.setProgress(1);
            this.timeBar.setVisible(true);
        }

    }

    @Override
    public void onGameStart() {
        if (stopAfterStart) {
            this.winType = BowRunServer.WinType.END;
            this.finisher = null;
        } else {
            runnerArmor = List.of(false, false, false, false);
            if (BowRunServer.getMap().isRunnerArmor()) {
                int runnerSize = this.getGame().getRunnerTeam().getUsers().size();
                int size = Server.getInGameUsers().size();
                double runnerRatio = runnerSize / ((double) size);
                double ratioDiff = this.getGame().getRunnerTeam().getRatio() - runnerRatio;
                if (ratioDiff > 0.05) { // < 0.2
                    runnerArmor = List.of(false, false, true, true);
                }
                if (ratioDiff > 0.1) {
                    runnerArmor = List.of(true, false, true, true);
                }
                if (ratioDiff > 0.15) {
                    runnerArmor = List.of(true, true, true, true);
                }
            }

            for (User user : Server.getInGameUsers()) {
                ((BowRunUser) user).startGame();
            }

            int period =
                    (int) (BowRunServer.ARROW_GENERATION_SPEED +
                            Math.sqrt(BowRunServer.ARROW_GENERATION_PLAYER_MULTIPLIER *
                                    BowRunServer.getGame().getArcherTeam().getInGameUsers().size()));
            this.arrowGenerator.setPeriod(period);
        }
    }

    @Override
    public BowRunGame getGame() {
        return super.getGame();
    }

    @Override
    public BowRunMap getMap() {
        return (BowRunMap) super.getMap();
    }


    public void stopGame(BowRunServer.WinType winType, BowRunUser finisher) {
        this.winType = winType;
        this.finisher = finisher;
        this.stopGame();
    }

    @Override
    public void onGameStop() {
        if (this.getState().equals(BowRunServer.State.STOPPED)) {
            return;
        }

        this.setState(BowRunServer.State.STOPPED);

        Team archerTeam = this.getGame().getArcherTeam();
        Team runnerTeam = this.getGame().getRunnerTeam();

        int archerKills = archerTeam.getKills();
        if (archerKills == 0) archerKills = 1;

        for (User user : Server.getInGameUsers()) {
            ((BowRunUser) user).resetGameEquipment();

            if (((BowRunUser) user).getTeam().equals(archerTeam)) {
                user.addCoins((float) (((BowRunUser) user).getKills() / ((double) archerKills) * BowRunServer.KILL_COINS_POOL), true);
            }
        }

        this.broadcastGameMessage(Chat.getLongLineSeparator());
        Server.broadcastSound(BowRunServer.END_SOUND, 5F);
        switch (winType) {
            case ARCHER -> {
                Server.broadcastTitle(Component.text("Archers ", archerTeam.getTextColor())
                                .append(Component.text("win!", ExTextColor.PUBLIC)), Component.empty(),
                        Duration.ofSeconds(5));
                this.broadcastGameMessage(Component.text("Archers ", archerTeam.getTextColor())
                        .append(Component.text("win!", ExTextColor.PUBLIC)));
                for (User user : this.getGame().getArcherTeam().getUsers()) {
                    user.addCoins(BowRunServer.WIN_COINS, true);
                }
            }
            case RUNNER -> {
                Server.broadcastTitle(Component.text("Runners ", runnerTeam.getTextColor())
                                .append(Component.text("win!", ExTextColor.PUBLIC)), Component.empty(),
                        Duration.ofSeconds(5));
                this.broadcastGameMessage(Component.text("Runners ", runnerTeam.getTextColor())
                        .append(Component.text("win!", ExTextColor.PUBLIC)));
                for (User user : this.getGame().getRunnerTeam().getUsers()) {
                    user.addCoins(BowRunServer.WIN_COINS, true);
                }
            }
            case ARCHER_TIME -> {
                Server.broadcastTitle(Component.text("Archers ", archerTeam.getTextColor())
                                .append(Component.text("win!", ExTextColor.PUBLIC)),
                        Component.text("Time is up", ExTextColor.PUBLIC), Duration.ofSeconds(5));
                this.broadcastGameMessage(Component.text("Archers ", archerTeam.getTextColor())
                        .append(Component.text("win!", ExTextColor.PUBLIC)));
                for (User user : this.getGame().getArcherTeam().getUsers()) {
                    user.addCoins(BowRunServer.WIN_COINS, true);
                }
            }
            case RUNNER_FINISH -> {
                Server.broadcastTitle(Component.text("Runners ", runnerTeam.getTextColor())
                                .append(Component.text("win!", ExTextColor.PUBLIC)),
                        finisher.getChatNameComponent()
                                .append(Component.text(" reached the finish", ExTextColor.PUBLIC)),
                        Duration.ofSeconds(5));
                this.broadcastGameMessage(Component.text("Runners ", runnerTeam.getTextColor())
                        .append(Component.text("win!", ExTextColor.PUBLIC)));
                for (User user : this.getGame().getRunnerTeam().getUsers()) {
                    user.addCoins(BowRunServer.WIN_COINS, true);
                }
            }
            default -> {
                Server.broadcastTitle(Component.text("Game has ended", ExTextColor.PUBLIC), Component.empty(),
                        Duration.ofSeconds(5));
                this.broadcastGameMessage(Component.text("Game has ended", ExTextColor.PUBLIC));
            }
        }

        this.broadcastGameMessage(Chat.getLongLineSeparator());
        this.broadcastHighscore("Kills", (Collection) BowRunServer.getGame().getArcherTeam().getUsers(), 3,
                GameUser::getKills);
        this.broadcastHighscore("Deaths", (Collection) (BowRunServer.getGame().getRunnerTeam().getUsers()), 3,
                GameUser::getDeaths);
        this.broadcastHighscore("Longest Shot", (Collection) BowRunServer.getGame().getArcherTeam().getUsers(), 3,
                u -> u.getLongestShot() > 0, GameUser::getLongestShot);
        this.broadcastGameMessage(Chat.getLongLineSeparator());

        String recordTime = null;
        UUID lastRecord = null;
        BowRunMap map = BowRunServer.getMap();
        int time = map.getTime() - this.getPlayingTime();
        int oldRecord = map.getBestTime();

        // record check
        if (map.getBestTimeUser() != null) {
            lastRecord = map.getBestTimeUser();
        }

        if (oldRecord > time && winType == BowRunServer.WinType.RUNNER_FINISH && finisher != null) {
            recordTime = Chat.getTimeString(time);

            this.broadcastGameMessage(Component.text("New record: ", ExTextColor.GOLD)
                    .append(Component.text(recordTime, ExTextColor.BLUE))
                    .append(Component.text(" by ", ExTextColor.GOLD))
                    .append(finisher.getChatNameComponent()));

            this.recordVerification.checkRecord(time, finisher, map);
        }
        // stats
        List<String> stats = new ArrayList<>();

        stats.add("Teams (R vs. A): " + this.getGame().getRunnerTeam().getUsers().size() + " vs. " + this.getGame().getArcherTeam().getUsers().size());
        stats.add("Time: " + this.getPlayingTime() + " of " + map.getTime());
        stats.add("Map: " + map.getName());
        if (recordTime != null) {
            if (lastRecord != null) {
                stats.add("New Record: " + recordTime + " " + finisher.getUniqueId() + "(old: " + oldRecord + " " + lastRecord + ")");
            } else {
                stats.add("New Record: " + recordTime + " " + finisher.getUniqueId());
            }
        }

        stats.add("GameUserStats: Team Deaths Kills BowHits BowShots BowShotHits Hit/Shot");
        for (User user : Server.getGameNotServiceUsers()) {
            if (user.getStatus().equals(Status.User.SPECTATOR)) {
                continue;
            }

            GameUser gameUser = ((GameUser) user);

            if (gameUser.getTeam() == null) {
                continue;
            }

            if (gameUser.getBowShots().equals(0)) {
                stats.add(gameUser.getName() + ": " + gameUser.getTeam().getName() + " " + gameUser.getDeaths() + " " +
                        gameUser.getKills() + " " + gameUser.getBowHits() + " " + gameUser.getBowShots() + " " +
                        gameUser.getBowHitTarget() + " " + (gameUser.getBowHitTarget()));
            } else {
                stats.add(gameUser.getName() + ": " + gameUser.getTeam().getName() + " " + gameUser.getDeaths() + " " +
                        gameUser.getKills() + " " + gameUser.getBowHits() + " " + gameUser.getBowShots() + " " +
                        gameUser.getBowHitTarget() + " " + gameUser.getBowHitTarget() / ((double) gameUser.getBowShots()));
            }
        }

        Server.printSection(Plugin.BOWRUN, "GameStats", stats);
    }

    @Override
    public void onGameUserQuit(GameUser user) {
        if (!user.getStatus().equals(Status.User.IN_GAME)) {
            return;
        }

        if (user.getTeam() == null) {
            return;
        }

        if (user.getTeam().getUsers().size() > 0) {
            return;
        }

        this.winType = user.getTeam().equals(this.getGame().getArcherTeam()) ? BowRunServer.WinType.RUNNER : BowRunServer.WinType.ARCHER;
        this.finisher = null;
        this.stopGame();
    }

    @Override
    public void onGameUserQuitBeforeStart(GameUser user) {
        if (user.getTeam() == null) {
            return;
        }

        if (user.getTeam().getUsers().size() > 1) {
            return;
        }

        this.stopAfterStart = true;
    }

    @Override
    public boolean isRejoiningAllowed() {
        return true;
    }

    @Override
    public void onGameUserRejoin(GameUser user) {
        user.joinGame();
        ((BowRunUser) user).startGame();
    }

    @Override
    public void onGameReset() {
        this.updateGameTimeOnSideboard();
        this.stopAfterStart = false;
        this.relayManager.reset();
        this.winType = null;
    }

    @Override
    public Kit getKit(int index) {
        return null;
    }

    @Override
    public Kit[] getKits() {
        return null;
    }

    public void setGameSideboardScore(int line, String text) {
        this.sideboard.setScore(line, text);
    }

    public void setSpectatorSideboardScore(int line, String text) {
        this.spectatorSideboard.setScore(line, text);
    }

    public Sideboard getGameSideboard() {
        return this.sideboard;
    }

    public void updateGameTimeOnSideboard() {
        String time = Chat.getTimeString(this.getPlayingTime());

        this.setGameSideboardScore(3, time);
        this.setSpectatorSideboardScore(3, time);

        if (this.getMap() != null) {
            this.timeBar.setTitle(time);
            this.timeBar.setProgress(this.getPlayingTime() / ((double) this.getMap().getTime()));

            if (this.getPlayingTime() == 60) {
                this.timeBar.setColor(BarColor.RED);
            }
        }

    }

    public void updateMapOnSideboard() {
        this.spectatorSideboard.setScore(0, "§f" + BowRunServer.getMap().getDisplayName());
    }

    public RecordVerification getRecordVerification() {
        return recordVerification;
    }

    public ExItemStack getRandomRunnerItem() {
        return BowRunServer.RUNNER_ITEMS.get((int) ((Math.random() * BowRunServer.RUNNER_ITEMS.size() + Math.random() * BowRunServer.RUNNER_ITEMS.size()) / 2));
    }

    public ExItemStack getRandomArcherItem() {
        return BowRunServer.ARCHER_ITEMS.get((int) ((Math.random() * BowRunServer.ARCHER_ITEMS.size() + Math.random() * BowRunServer.ARCHER_ITEMS.size()) / 2));
    }

    public void addRunnerDeath() {
        this.runnerDeaths++;
    }

    public int getRunnerDeaths() {
        return this.runnerDeaths;
    }

    public Integer getPlayingTime() {
        if (this.timerTool == null) {
            return 0;
        }
        return this.timerTool.getTime();
    }

    public BossBar getTimeBar() {
        return timeBar;
    }

    @Override
    public void broadcastGameMessage(Component message) {
        Server.broadcastMessage(Plugin.BOWRUN, message);
    }

    @Override
    public boolean isGameRunning() {
        return BowRunServer.getState() == BowRunServer.State.RUNNING;
    }

    public List<Boolean> getRunnerArmor() {
        return this.runnerArmor;
    }

    @Override
    public Sideboard getSpectatorSideboard() {
        return this.spectatorSideboard;
    }

    @Override
    public Location getSpectatorSpawn() {
        return this.getMap().getArcherSpawn();
    }

    public double getRunnerItemChance() {
        return (1 - ((double) this.getPlayingTime() / this.getMap().getTime())) * BowRunServer.RUNNER_ITEM_CHANCE_MULTIPLIER;
    }

    public void giveArcherSpecialItems() {
        if (!this.getMap().isOnlyInstant() && !this.getMap().isOnlyPunch() && !this.getMap().isArcherNoSpecialItems()) {
            for (User user : this.getGame().getArcherTeam().getInGameUsers()) {
                if (Math.random() < BowRunServer.ARCHER_ITEM_CHANCE) {
                    ItemStack item = BowRunServer.getRandomArcherItem();

                    for (int slot = 0; slot < 9; slot++) {
                        if (user.getInventory().getItem(slot) == null) {
                            user.addItem(item);
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void saveGameUserStats(GameUser user) {
        super.saveGameUserStats(user);

        if (this.winType != null) {
            if (user.getTeam().equals(this.getGame().getRunnerTeam())) {
                if ((this.winType.equals(BowRunServer.WinType.RUNNER_FINISH)
                        || this.winType.equals(BowRunServer.WinType.RUNNER))) {
                    user.getStat(BowRunServer.RUNNER_WINS).increaseAll(1);
                }
                user.getStat(BowRunServer.DEATHS).increaseAll(user.getDeaths());
            } else if (user.getTeam().equals(this.getGame().getArcherTeam())) {
                if (this.winType.equals(BowRunServer.WinType.ARCHER_TIME)
                        || this.winType.equals(BowRunServer.WinType.ARCHER)) {
                    user.getStat(BowRunServer.ARCHER_WINS).increaseAll(1);
                }
                user.getStat(BowRunServer.KILLS).increaseAll(user.getKills());

                user.getStat(BowRunServer.LONGEST_SHOT).higherAll(user.getLongestShot());
            }

            for (StatPeriod period : StatPeriod.values()) {
                Integer archerWins = user.getStat(BowRunServer.ARCHER_WINS).get(period);
                Integer runnerWins = user.getStat(BowRunServer.RUNNER_WINS).get(period);
                Integer gamesPlayed = user.getStat(GAMES_PLAYED).get(period);
                user.getStat(BowRunServer.WIN_CHANCE).set(period, (archerWins + runnerWins) / ((float) gamesPlayed));
            }
        }

        if (user.getTeam().equals(this.getGame().getArcherTeam())) {
            user.getStat(BowRunServer.MOST_KILLS_PER_MATCH).higherAll(user.getKills());
        }

    }

    @Override
    public Set<StatType<?>> getStats() {
        return Set.of(BowRunServer.RUNNER_WINS, BowRunServer.ARCHER_WINS, BowRunServer.WIN_CHANCE, BowRunServer.KILLS,
                BowRunServer.DEATHS, BowRunServer.MOST_KILLS_PER_MATCH, BowRunServer.LONGEST_SHOT);
    }

    public static class ArrowGenerator implements GameTool, StartableTool, StopableTool {

        private BukkitTask task;
        private int periodInTicks;

        @Override
        public void start() {
            this.task = Server.runTaskTimerSynchrony(() -> {
                if (BowRunServer.getMap() != null) {
                    for (TeamUser user : BowRunServer.getGame().getArcherTeam().getInGameUsers()) {
                        int delta = user.containsAtLeast(BowRunUser.ARROW, BowRunServer.MAX_ARROWS, true);
                        if (delta < 0) {
                            user.addItem(BowRunUser.ARROW.cloneWithId().asQuantity(Math.min(BowRunServer.RESPAWN_ARROW_AMOUNT, -delta)));
                        }
                    }
                }
            }, 0, periodInTicks, GameBowRun.getPlugin());
        }

        @Override
        public void stop() {
            if (this.task != null) {
                this.task.cancel();
            }
        }

        public void setPeriod(int period) {
            this.periodInTicks = period;
        }
    }
}
