package de.timesnake.game.bowrun.server;

import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.ServerManager;
import de.timesnake.basic.bukkit.util.chat.ChatColor;
import de.timesnake.basic.bukkit.util.user.ExItemStack;
import de.timesnake.basic.bukkit.util.user.User;
import de.timesnake.basic.bukkit.util.user.scoreboard.Sideboard;
import de.timesnake.basic.loungebridge.util.server.LoungeBridgeServerManager;
import de.timesnake.basic.loungebridge.util.user.GameUser;
import de.timesnake.basic.loungebridge.util.user.Kit;
import de.timesnake.database.util.Database;
import de.timesnake.database.util.game.DbGame;
import de.timesnake.game.bowrun.chat.Plugin;
import de.timesnake.game.bowrun.main.GameBowRun;
import de.timesnake.game.bowrun.user.BowRunUser;
import de.timesnake.game.bowrun.user.UserManager;
import de.timesnake.library.basic.util.Status;
import de.timesnake.library.basic.util.statistics.StatPeriod;
import de.timesnake.library.basic.util.statistics.StatType;
import de.timesnake.library.extension.util.chat.Chat;
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

public class BowRunServerManager extends LoungeBridgeServerManager implements Listener {

    public static BowRunServerManager getInstance() {
        return (BowRunServerManager) ServerManager.getInstance();
    }

    private final RecordVerification recordVerification = new RecordVerification();
    private Sideboard sideboard;
    private Sideboard spectatorSideboard;
    private BossBar timeBar;
    private Integer playingTime = 0;
    private BukkitTask playingTimeTask;
    private int runnerDeaths = 0;
    private boolean stopAfterStart = false;
    private List<Boolean> runnerArmor;
    private UserManager userManager;
    private RelayManager relayManager;
    private BowRunServer.WinType winType;

    public void onBowRunEnable() {
        super.onLoungeBridgeEnable();
        super.setTeamMateDamage(false);

        Server.registerListener(this, GameBowRun.getPlugin());

        this.playingTime = 0;

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

        this.updateGameTimeOnSideboard();

        Color color = this.getGame().getRunnerTeam().getColor();
        BowRunServer.armor = List.of(new ExItemStack(Material.GOLDEN_BOOTS,
                List.of(Enchantment.PROTECTION_PROJECTILE), List.of(1)), new ExItemStack(Material.GOLDEN_LEGGINGS,
                List.of(Enchantment.PROTECTION_PROJECTILE), List.of(1)), new ExItemStack(Material.GOLDEN_CHESTPLATE,
                List.of(Enchantment.PROTECTION_PROJECTILE), List.of(1)), new ExItemStack(Material.LEATHER_HELMET,
                color));
    }

    @Override
    public BowRunUser loadUser(Player player) {
        return new BowRunUser(player);
    }

    @Override
    protected BowRunGame loadGame(DbGame dbGame, boolean loadWorlds) {
        return new BowRunGame(dbGame, true);
    }

    @Override
    public de.timesnake.library.basic.util.chat.Plugin getGamePlugin() {
        return Plugin.BOWRUN;
    }

    @Override
    public void prepareGame() {
        this.setPlayingTime(BowRunServer.getMap().getTime());
        this.updateGameTimeOnSideboard();
        this.updateMapOnSideboard();
    }


    @Override
    public void onMapLoad() {
        BowRunMap map = BowRunServer.getMap();
        this.setPlayingTime(map.getTime());
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

            String playTime = Chat.getTimeString(this.playingTime);

            this.timeBar.setTitle(playTime);
            this.timeBar.setColor(BarColor.GREEN);
            this.timeBar.setProgress(1);
            this.timeBar.setVisible(true);
        }

    }

    @Override
    public void onGameStart() {
        if (stopAfterStart) {
            this.stopGame(BowRunServer.WinType.END, null);
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
                    (int) (Math.sqrt(BowRunServer.ARROW_GENERATION_PLAYER_MULTIPLIER * BowRunServer.getGame().getArcherTeam().getInGameUsers().size()) * BowRunServer.ARROW_GENERATION_SPEED);
            this.userManager.runArrowGenerator(period);

            playingTimeTask = Server.runTaskTimerSynchrony(() -> {
                this.playingTime--;

                updateGameTimeOnSideboard();
                if (this.playingTime % 20 == 0) {
                    this.giveArcherSpecialItems();
                }

                if ((this.playingTime % 60) == 0) {
                    Server.broadcastNote(BowRunServer.TIME_INSTRUMENT, BowRunServer.TIME_NOTE);
                }
                if (this.playingTime == 0) {
                    Server.runTaskSynchrony(() -> stopGame(BowRunServer.WinType.ARCHER_TIME, null),
                            GameBowRun.getPlugin());
                }

            }, 20, 20, GameBowRun.getPlugin());
        }
    }

    @Override
    public BowRunGame getGame() {
        return (BowRunGame) super.getGame();
    }

    @Override
    public BowRunMap getMap() {
        return (BowRunMap) super.getMap();
    }

    public void stopGame(BowRunServer.WinType winType, User finisher) {
        if (this.getState().equals(BowRunServer.State.STOPPED)) {
            return;
        }

        this.winType = winType;

        this.setState(BowRunServer.State.STOPPED);

        this.playingTimeTask.cancel();
        this.userManager.cancelArrowGenerator();

        int archerKills = this.getGame().getArcherTeam().getKills();
        if (archerKills == 0) archerKills = 1;

        for (User user : Server.getInGameUsers()) {
            ((BowRunUser) user).resetGameEquipment();

            if (((BowRunUser) user).getTeam().equals(this.getGame().getArcherTeam())) {
                user.addCoins((float) (((BowRunUser) user).getKills() / ((double) archerKills) * BowRunServer.KILL_COINS_POOL), true);
            }
        }

        this.broadcastGameMessage(Chat.getLongLineSeparator());
        Server.broadcastSound(BowRunServer.END_SOUND, 5F);
        switch (winType) {
            case ARCHER -> {
                Server.broadcastTitle(ChatColor.RED + "Archers " + ChatColor.PUBLIC + "win!", "",
                        Duration.ofSeconds(5));
                this.broadcastGameMessage(ChatColor.RED + "Archers " + ChatColor.PUBLIC + "win!");
                for (User user : this.getGame().getArcherTeam().getUsers()) {
                    user.addCoins(BowRunServer.WIN_COINS, true);
                }
            }
            case RUNNER -> {
                Server.broadcastTitle(ChatColor.BLUE + "Runners " + ChatColor.PUBLIC + "win!", "",
                        Duration.ofSeconds(5));
                this.broadcastGameMessage(ChatColor.BLUE + "Runners " + ChatColor.PUBLIC + "win!");
                for (User user : this.getGame().getRunnerTeam().getUsers()) {
                    user.addCoins(BowRunServer.WIN_COINS, true);
                }
            }
            case ARCHER_TIME -> {
                Server.broadcastTitle(ChatColor.RED + "Archers " + ChatColor.PUBLIC + "win!", ChatColor.PUBLIC +
                        "Time is up", Duration.ofSeconds(5));
                this.broadcastGameMessage(ChatColor.RED + "Archers " + ChatColor.PUBLIC + "win!");
                for (User user : this.getGame().getArcherTeam().getUsers()) {
                    user.addCoins(BowRunServer.WIN_COINS, true);
                }
            }
            case RUNNER_FINISH -> {
                Server.broadcastTitle(ChatColor.BLUE + "Runners " + ChatColor.PUBLIC + "win!",
                        finisher.getChatName() + ChatColor.PUBLIC + " reached the finish", Duration.ofSeconds(5));
                this.broadcastGameMessage(ChatColor.BLUE + "Runners " + ChatColor.PUBLIC + "win!");
                for (User user : this.getGame().getRunnerTeam().getUsers()) {
                    user.addCoins(BowRunServer.WIN_COINS, true);
                }
            }
            default -> {
                Server.broadcastTitle(ChatColor.WHITE + "Game has ended", "", Duration.ofSeconds(5));
                this.broadcastGameMessage(ChatColor.WHITE + "Game has ended");
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
        int time = map.getTime() - this.playingTime;
        int oldRecord = map.getBestTime();

        // record check
        if (map.getBestTimeUser() != null) {
            lastRecord = map.getBestTimeUser();
        }

        if (oldRecord > time && winType == BowRunServer.WinType.RUNNER_FINISH) {
            StringBuilder record = new StringBuilder();
            if (time >= 60) record.append(time / 60).append(" min").append("  ");
            record.append(time % 60).append(" s");
            recordTime = record.toString();

            this.broadcastGameMessage(ChatColor.GOLD + "New record: " + ChatColor.BLUE + recordTime + ChatColor.GOLD + " by " + finisher.getChatName());

            this.recordVerification.checkRecord(time, finisher, map);
        }
        // stats
        List<String> stats = new ArrayList<>();

        stats.add("Teams (R vs. A): " + this.getGame().getRunnerTeam().getUsers().size() + " vs. " + this.getGame().getArcherTeam().getUsers().size());
        stats.add("Time: " + this.playingTime + " of " + map.getTime());
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
                stats.add(gameUser.getName() + ": " + gameUser.getTeam().getName() + " " + gameUser.getDeaths() + " " + gameUser.getKills() + " " + gameUser.getBowHits() + " " + gameUser.getBowShots() + " " + gameUser.getBowHitTarget() + " " + (gameUser.getBowHitTarget()));
            } else {
                stats.add(gameUser.getName() + ": " + gameUser.getTeam().getName() + " " + gameUser.getDeaths() + " " + gameUser.getKills() + " " + gameUser.getBowHits() + " " + gameUser.getBowShots() + " " + gameUser.getBowHitTarget() + " " + gameUser.getBowHitTarget() / ((double) gameUser.getBowShots()));
            }
        }

        Server.printSection(Plugin.BOWRUN, "GameStats", stats);

        BowRunServer.closeGame();
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

        this.stopGame(user.getTeam().equals(this.getGame().getArcherTeam()) ? BowRunServer.WinType.RUNNER :
                BowRunServer.WinType.ARCHER, null);
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
    public void resetGame() {

        this.playingTime = BowRunServer.getMap().getTime();
        this.updateGameTimeOnSideboard();
        this.stopAfterStart = false;
        this.relayManager.reset();
        this.winType = null;

        if (BowRunServer.getMap() != null) {
            Server.getWorldManager().reloadWorld(BowRunServer.getMap().getWorld());
        }
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
        String time = Chat.getTimeString(this.playingTime);

        this.setGameSideboardScore(3, time);
        this.setSpectatorSideboardScore(3, time);

        if (this.getMap() != null) {
            this.timeBar.setTitle(time);
            this.timeBar.setProgress(this.playingTime / ((double) this.getMap().getTime()));

            if (this.playingTime == 60) {
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
        return playingTime;
    }

    public void setPlayingTime(Integer playingTime) {
        this.playingTime = playingTime;
    }

    public BossBar getTimeBar() {
        return timeBar;
    }

    @Override
    public void broadcastGameMessage(String message) {
        Server.broadcastMessage(Plugin.BOWRUN, ChatColor.PUBLIC + message);
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
        return (1 - ((double) this.playingTime / this.getMap().getTime())) * BowRunServer.RUNNER_ITEM_CHANCE_MULTIPLIER;
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
}
