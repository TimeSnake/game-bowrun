package de.timesnake.game.bowrun.server;

import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.ServerManager;
import de.timesnake.basic.bukkit.util.chat.ChatColor;
import de.timesnake.basic.bukkit.util.user.ExItemStack;
import de.timesnake.basic.bukkit.util.user.User;
import de.timesnake.basic.bukkit.util.user.scoreboard.Sideboard;
import de.timesnake.basic.loungebridge.util.server.LoungeBridgeServer;
import de.timesnake.basic.loungebridge.util.server.LoungeBridgeServerManager;
import de.timesnake.basic.loungebridge.util.user.GameUser;
import de.timesnake.basic.loungebridge.util.user.Kit;
import de.timesnake.database.util.Database;
import de.timesnake.database.util.game.DbGame;
import de.timesnake.database.util.object.Status;
import de.timesnake.game.bowrun.chat.Plugin;
import de.timesnake.game.bowrun.main.GameBowRun;
import de.timesnake.game.bowrun.user.BowRunUser;
import de.timesnake.game.bowrun.user.UserManager;
import de.timesnake.library.basic.util.statistics.Stat;
import de.timesnake.library.extension.util.chat.Chat;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

public class BowRunServerManager extends LoungeBridgeServerManager implements Listener {

    public static BowRunServerManager getInstance() {
        return (BowRunServerManager) ServerManager.getInstance();
    }

    public static final List<ExItemStack> RUNNER_ITEMS = List.of(new ExItemStack(true, "§6Heal II", PotionEffectType.HEAL, 0, 2, 2), new ExItemStack(true, "§6Jump II  (7 s)", PotionEffectType.JUMP, 20 * 7, 2, 1), new ExItemStack(true, "§6Slow Fall (20 s)", PotionEffectType.SLOW_FALLING, 20 * 20, 1, 1), new ExItemStack(true, "§6Speed II (30 s)", PotionEffectType.SPEED, 20 * 15, 2, 1), new ExItemStack(true, "§6Invisibility (8 s)", List.of("§fRemoves your armor"), PotionEffectType.INVISIBILITY, 20 * 8, 1, 1), new ExItemStack(Material.GOLDEN_APPLE, 2), new ExItemStack(true, "§6Fire Resistance (45 s)", PotionEffectType.FIRE_RESISTANCE, 20 * 45, 1, 1), new ExItemStack(true, "§6Resistance (2 min)", PotionEffectType.DAMAGE_RESISTANCE, 20 * 60, 1, 1), new ExItemStack(Material.SHIELD, 1, 300), new ExItemStack(Material.TOTEM_OF_UNDYING));

    public static final List<ExItemStack> ARCHER_ITEMS = List.of(new ExItemStack(Material.BOW, "§6Flame-Bow", 380, List.of(Enchantment.ARROW_INFINITE, Enchantment.ARROW_FIRE), List.of(1, 1)), new ExItemStack(Material.BOW, "§6Power-Bow", 384, List.of(Enchantment.ARROW_INFINITE, Enchantment.ARROW_DAMAGE), List.of(1, 7)), new ExItemStack(Material.BOW, "§6Punch-Bow", 382, List.of(Enchantment.ARROW_INFINITE, Enchantment.ARROW_KNOCKBACK), List.of(1, 2)), new ExItemStack(Material.SPECTRAL_ARROW, 32, "§6Spectral-Arrow"));

    public enum WinType {
        RUNNER_FINISH, RUNNER, ARCHER, ARCHER_TIME, END
    }

    public static final Instrument TIME_INSTRUMENT = Instrument.PLING;
    public static final Note TIME_NOTE = Note.natural(1, Note.Tone.A);

    public static final Sound KILL_SOUND = Sound.ENTITY_PLAYER_LEVELUP;

    public static final Sound END_SOUND = Sound.BLOCK_BEACON_ACTIVATE;

    public static final Double RUNNER_ITEM_CHANCE_MULTIPLIER = 0.6;
    public static final Double ARCHER_ITEM_CHANCE = 0.2;

    public static final float KILL_COINS_POOL = 16;
    public static final float WIN_COINS = 10;
    public static final float RECORD_COINS = 20;

    public static List<ExItemStack> armor;

    public static final String SIDEBOARD_TIME_TEXT = "§9§lTime";
    public static final String SIDEBOARD_KILLS_TEXT = "§c§lKills";
    public static final String SIDEBOARD_DEATHS_TEXT = "§c§lDeaths";
    public static final String SIDEBOARD_MAP_TEXT = "§c§lMap";

    public static final Stat<Integer> RUNNER_WINS = Stat.Type.INTEGER.asStat("runner_wins", "Runner Wins", 0, 0, 2);
    public static final Stat<Integer> ARCHER_WINS = Stat.Type.INTEGER.asStat("archer_wins", "Archer Wins", 0, 0, 3);
    public static final Stat<Float> WIN_CHANCE = Stat.Type.PERCENT.asStat("win_chance", "Win Chance", 0f, 0, 4);
    public static final Stat<Integer> DEATHS = Stat.Type.INTEGER.asStat("runner_deaths", "Deaths", 0, 1, 1);
    public static final Stat<Integer> KILLS = Stat.Type.INTEGER.asStat("archer_kills", "Kills", 0, 1, 2);
    public static final Stat<Integer> LONGEST_SHOT = Stat.Type.INTEGER.asStat("archer_longest_shot", "Longest Shot", 0, 1, 3);


    private Sideboard sideboard;
    private Sideboard spectatorSideboard;

    private Integer playingTime = 0;
    private BukkitTask playingTimeTask;

    private int runnerDeaths = 0;

    private boolean stopAfterStart = false;

    private List<Boolean> runnerArmor;

    private UserManager userManager;

    private final RecordVerification recordVerification = new RecordVerification();

    private RelayManager relayManager;

    private WinType winType;

    public void onBowRunEnable() {
        super.onLoungeBridgeEnable();
        super.setTeamMateDamage(false);

        Server.registerListener(this, GameBowRun.getPlugin());

        this.playingTime = 0;

        this.userManager = new UserManager();

        this.relayManager = new RelayManager();

        this.sideboard = Server.getScoreboardManager().registerNewSideboard("bowrun", "§6§lBowRun");
        this.sideboard.setScore(4, SIDEBOARD_TIME_TEXT);
        //time (method after spec sideboard)
        this.sideboard.setScore(2, "§r§f-----------");
        // kills/deaths
        // kills/deaths amount

        this.spectatorSideboard = Server.getScoreboardManager().registerNewSideboard("bowrunSpectator", "§6§lBowRun");
        this.spectatorSideboard.setScore(4, SIDEBOARD_TIME_TEXT);
        // time
        this.spectatorSideboard.setScore(2, "§r§f-----------");
        this.spectatorSideboard.setScore(1, SIDEBOARD_MAP_TEXT);
        // map

        this.updateGameTimeOnSideboard();

        Color color = this.getGame().getRunnerTeam().getColor();
        armor = List.of(new ExItemStack(Material.GOLDEN_BOOTS, List.of(Enchantment.PROTECTION_PROJECTILE), List.of(1)), new ExItemStack(Material.GOLDEN_LEGGINGS, List.of(Enchantment.PROTECTION_PROJECTILE), List.of(1)), new ExItemStack(Material.GOLDEN_CHESTPLATE, List.of(Enchantment.PROTECTION_PROJECTILE), List.of(1)), new ExItemStack(Material.LEATHER_HELMET, color));
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
    public void loadMap() {
        BowRunMap map = BowRunServer.getMap();
        this.setPlayingTime(map.getTime());
        map.getWorld().setTime(map.isTimeNight() ? 19000 : 1000);
        this.updateGameTimeOnSideboard();
        if (map.getBestTime() != null) {
            StringBuilder time = new StringBuilder();
            if (map.getBestTime() >= 60) time.append(map.getBestTime() / 60).append(" min").append("  ");
            time.append(map.getBestTime() % 60).append(" s");
            if (map.getBestTimeUser() != null) {
                BowRunServer.getGameTablist().setFooter(ChatColor.GOLD + "Record: " + ChatColor.BLUE + time + ChatColor.GOLD + " by " + ChatColor.BLUE + Database.getUsers().getUser(map.getBestTimeUser()).getName());
            } else {
                BowRunServer.getGameTablist().setFooter(ChatColor.GOLD + "Record: " + ChatColor.BLUE + time);
            }
        }

    }

    @Override
    public void startGame() {
        if (stopAfterStart) {
            this.stopGame(WinType.END, null);
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
            playingTimeTask = Server.runTaskTimerSynchrony(() -> {
                updateGameTimeOnSideboard();
                if (playingTime % 20 == 0) {
                    this.giveArcherSpecialItems();
                }

                if ((playingTime % 60) == 0) {
                    Server.broadcastNote(TIME_INSTRUMENT, TIME_NOTE);
                }
                if (playingTime == 0) {
                    Server.runTaskSynchrony(() -> stopGame(WinType.ARCHER_TIME, null), GameBowRun.getPlugin());
                }

                playingTime--;
            }, 0, 20, GameBowRun.getPlugin());
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

    public void stopGame(WinType winType, User finisher) {
        if (this.getState().equals(BowRunServer.State.STOPPED)) {
            return;
        }

        this.winType = winType;

        this.setState(BowRunServer.State.STOPPED);

        this.playingTimeTask.cancel();

        int archerKills = this.getGame().getArcherTeam().getKills();
        if (archerKills == 0) archerKills = 1;

        for (User user : Server.getInGameUsers()) {
            ((BowRunUser) user).resetGameEquipment();

            if (((BowRunUser) user).getTeam().equals(this.getGame().getArcherTeam())) {
                user.addCoins(((BowRunUser) user).getKills() / archerKills, true);
            }
        }

        this.broadcastGameMessage(Chat.getLongLineSeparator());
        Server.broadcastSound(END_SOUND, 5F);
        switch (winType) {
            case ARCHER:
                Server.broadcastTitle(ChatColor.RED + "Archers " + ChatColor.PUBLIC + "win!", "", Duration.ofSeconds(5));
                this.broadcastGameMessage(ChatColor.RED + "Archers " + ChatColor.PUBLIC + "win!");
                for (User user : this.getGame().getArcherTeam().getUsers()) {
                    user.addCoins(WIN_COINS, true);
                }
                break;
            case RUNNER:
                Server.broadcastTitle(ChatColor.BLUE + "Runners " + ChatColor.PUBLIC + "win!", "", Duration.ofSeconds(5));
                this.broadcastGameMessage(ChatColor.BLUE + "Runners " + ChatColor.PUBLIC + "win!");
                for (User user : this.getGame().getRunnerTeam().getUsers()) {
                    user.addCoins(WIN_COINS, true);
                }
                break;
            case ARCHER_TIME:
                Server.broadcastTitle(ChatColor.RED + "Archers " + ChatColor.PUBLIC + "win!", ChatColor.PUBLIC + "Time is up", Duration.ofSeconds(5));
                this.broadcastGameMessage(ChatColor.RED + "Archers " + ChatColor.PUBLIC + "win!");
                for (User user : this.getGame().getArcherTeam().getUsers()) {
                    user.addCoins(WIN_COINS, true);
                }
                break;
            case RUNNER_FINISH:
                Server.broadcastTitle(ChatColor.BLUE + "Runners " + ChatColor.PUBLIC + "win!", finisher.getChatName() + ChatColor.PUBLIC + " reached the finish", Duration.ofSeconds(5));
                this.broadcastGameMessage(ChatColor.BLUE + "Runners " + ChatColor.PUBLIC + "win!");
                for (User user : this.getGame().getRunnerTeam().getUsers()) {
                    user.addCoins(WIN_COINS, true);
                }
                break;
            default:
                Server.broadcastTitle(ChatColor.WHITE + "Game has ended", "", Duration.ofSeconds(5));
                this.broadcastGameMessage(ChatColor.WHITE + "Game has ended");
        }

        this.broadcastGameMessage(Chat.getLongLineSeparator());

        GameUser userKills = LoungeBridgeServer.getMostKills((Collection) BowRunServer.getGame().getArcherTeam().getUsers());
        GameUser userDeaths = LoungeBridgeServer.getMostDeaths(((Collection) BowRunServer.getGame().getRunnerTeam().getUsers()));
        GameUser userLongestShot = LoungeBridgeServer.getLongestShot(((Collection) BowRunServer.getGame().getArcherTeam().getUsers()));

        if (userKills != null) {
            this.broadcastGameMessage(ChatColor.WHITE + "Kills: " + ChatColor.GOLD + userKills.getKills() + ChatColor.WHITE + " by " + userKills.getChatName());
        }
        if (userDeaths != null) {
            this.broadcastGameMessage(ChatColor.WHITE + "Deaths: " + ChatColor.GOLD + userDeaths.getDeaths() + ChatColor.WHITE + " by " + userDeaths.getChatName());
        }
        if (userLongestShot != null && userLongestShot.getLongestShot() > 0) {
            this.broadcastGameMessage(ChatColor.WHITE + "Longest Shot: " + ChatColor.GOLD + userLongestShot.getLongestShot() + ChatColor.WHITE + " by " + userLongestShot.getChatName());
        }

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

        if (oldRecord > time && winType == WinType.RUNNER_FINISH) {
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

        this.stopGame(user.getTeam().equals(this.getGame().getArcherTeam()) ? WinType.RUNNER : WinType.ARCHER, null);
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
            BowRunServer.getMap().getWorld().removePlayers();
            Server.getWorldManager().reloadWorld(BowRunServer.getMap().getWorld());
            Server.printText(Plugin.BOWRUN, "Loaded backup of map " + BowRunServer.getMap().getName());
        } else {
            Server.printWarning(Plugin.BOWRUN, "Can not load backup of map " + BowRunServer.getMap().getName());
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
        StringBuilder time = new StringBuilder();
        if (this.playingTime >= 60) time.append(playingTime / 60).append(" min").append("  ");
        time.append(playingTime % 60).append(" s");

        this.setGameSideboardScore(3, time.toString());
        this.setSpectatorSideboardScore(3, time.toString());
    }

    public void updateMapOnSideboard() {
        this.spectatorSideboard.setScore(0, "§f" + BowRunServer.getMap().getDisplayName());
    }

    public RecordVerification getRecordVerification() {
        return recordVerification;
    }

    public ExItemStack getRandomRunnerItem() {
        return RUNNER_ITEMS.get((int) ((Math.random() * RUNNER_ITEMS.size() + Math.random() * RUNNER_ITEMS.size()) / 2));
    }

    public ExItemStack getRandomArcherItem() {
        return ARCHER_ITEMS.get((int) ((Math.random() * ARCHER_ITEMS.size() + Math.random() * ARCHER_ITEMS.size()) / 2));
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
        return (1 - ((double) this.playingTime / this.getMap().getTime())) * RUNNER_ITEM_CHANCE_MULTIPLIER;
    }

    public void giveArcherSpecialItems() {
        if (!this.getMap().isOnlyInstant() && !this.getMap().isOnlyPunch() && !this.getMap().isArcherNoSpecialItems()) {
            for (User user : this.getGame().getArcherTeam().getInGameUsers()) {
                if (Math.random() < BowRunServerManager.ARCHER_ITEM_CHANCE) {
                    ItemStack item = BowRunServerManager.getInstance().getRandomArcherItem();

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
                if ((this.winType.equals(WinType.RUNNER_FINISH) || this.winType.equals(WinType.RUNNER))) {
                    user.increaseStat(RUNNER_WINS, 1);
                }
                user.increaseStat(DEATHS, user.getDeaths());
            } else if (user.getTeam().equals(this.getGame().getArcherTeam())) {
                if (this.winType.equals(WinType.ARCHER_TIME) || this.winType.equals(WinType.ARCHER)) {
                    user.increaseStat(ARCHER_WINS, 1);
                }
                user.increaseStat(KILLS, user.getKills());

                user.higherStat(LONGEST_SHOT, user.getLongestShot());
            }

            user.setStat(WIN_CHANCE, (user.getStat(ARCHER_WINS) + user.getStat(RUNNER_WINS)) / ((float) user.getStat(GAMES_PLAYED)));
        }

    }

    @Override
    public Set<Stat<?>> getStats() {
        return Set.of(RUNNER_WINS, ARCHER_WINS, WIN_CHANCE, KILLS, DEATHS, LONGEST_SHOT);
    }
}
