/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.game.bowrun.server;

import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.ServerManager;
import de.timesnake.basic.bukkit.util.user.User;
import de.timesnake.basic.bukkit.util.user.inventory.ExItemStack;
import de.timesnake.basic.bukkit.util.user.scoreboard.KeyedSideboard;
import de.timesnake.basic.bukkit.util.user.scoreboard.KeyedSideboard.LineId;
import de.timesnake.basic.bukkit.util.user.scoreboard.KeyedSideboardBuilder;
import de.timesnake.basic.bukkit.util.user.scoreboard.Sideboard;
import de.timesnake.basic.bukkit.util.world.ExLocation;
import de.timesnake.basic.game.util.game.Team;
import de.timesnake.basic.game.util.user.TeamUser;
import de.timesnake.basic.loungebridge.util.server.EndMessage;
import de.timesnake.basic.loungebridge.util.server.LoungeBridgeServerManager;
import de.timesnake.basic.loungebridge.util.tool.GameTool;
import de.timesnake.basic.loungebridge.util.tool.advanced.MapTimerTool;
import de.timesnake.basic.loungebridge.util.tool.advanced.TimerTool;
import de.timesnake.basic.loungebridge.util.tool.scheduler.StartableTool;
import de.timesnake.basic.loungebridge.util.tool.scheduler.StopableTool;
import de.timesnake.basic.loungebridge.util.user.GameUser;
import de.timesnake.database.util.Database;
import de.timesnake.database.util.game.DbGame;
import de.timesnake.database.util.game.DbTmpGame;
import de.timesnake.game.bowrun.main.GameBowRun;
import de.timesnake.game.bowrun.user.BowRunUser;
import de.timesnake.game.bowrun.user.UserManager;
import de.timesnake.library.basic.util.Status;
import de.timesnake.library.basic.util.statistics.StatPeriod;
import de.timesnake.library.basic.util.statistics.StatType;
import de.timesnake.library.chat.Chat;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BowRunServerManager extends LoungeBridgeServerManager<BowRunGame> implements Listener {

  public static BowRunServerManager getInstance() {
    return (BowRunServerManager) ServerManager.getInstance();
  }

  private final Logger logger = LogManager.getLogger("bowrun.server");

  private final RecordVerification recordVerification = new RecordVerification();
  private KeyedSideboard sideboard;
  private KeyedSideboard spectatorSideboard;
  private BossBar timeBar;
  private int runnerDeaths = 0;
  private TimerTool timerTool;
  private List<Boolean> runnerArmor;
  private UserManager userManager;
  private RelayManager relayManager;
  private BowRunServer.WinType winType;
  private BowRunUser finisher;

  public void onBowRunEnable() {
    super.onLoungeBridgeEnable();
    super.setTeamMateDamage(false);

    Server.registerListener(this, GameBowRun.getPlugin());

    this.userManager = new UserManager();

    this.relayManager = new RelayManager();

    this.sideboard = Server.getScoreboardManager().registerExSideboard(new KeyedSideboardBuilder()
        .name("bowrun")
        .title("§6§lBowRun")
        .lineSpacer()
        .addLine(LineId.TIME)
        .addLine(LineId.EMPTY));

    this.spectatorSideboard = Server.getScoreboardManager()
        .registerExSideboard(new KeyedSideboardBuilder()
            .name("bowrunSpectator")
            .title("§6§lBowRun")
            .lineSpacer()
            .addLine(LineId.TIME)
            .addLine(LineId.MAP));

    this.timeBar = Server.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);

    this.timerTool = new MapTimerTool() {
      @Override
      public void onTimerPrepare() {
        super.onTimerPrepare();
        BowRunServerManager.this.updateGameTimeOnSideboard();
        BowRunServerManager.this.timeBar.setTitle(
            Chat.getTimeString(BowRunServerManager.this.getPlayingTime()));
        BowRunServerManager.this.timeBar.setColor(BarColor.GREEN);
        BowRunServerManager.this.timeBar.setProgress(1);
        BowRunServerManager.this.timeBar.setVisible(true);
      }

      @Override
      public void onTimerUpdate() {
        updateGameTimeOnSideboard();
        if (this.getTime() % 20 == 0) {
          BowRunServerManager.this.giveArcherSpecialItems();
        }

        if ((this.getTime() % 60) == 0) {
          Server.broadcastNote(BowRunServer.TIME_INSTRUMENT, BowRunServer.TIME_NOTE);
          Server.broadcastTitle(Component.empty(),
              Component.text(this.getTime() / 60 + " min left"),
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

    super.getToolManager().add(new ArrowGenerator());
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
  public void onMapLoad() {
    BowRunMap map = BowRunServer.getMap();
    map.getWorld().setTime(map.isTimeNight() ? 19000 : 1000);
    this.updateMapOnSideboard();

    if (map.getBestTime() != null) {
      String recordTime = Chat.getTimeString(map.getBestTime());
      if (map.getBestPlayer() != null) {
        BowRunServer.getGameTablist().setFooter("§hRecord: §u" + recordTime + " §hby §u" +
            Database.getUsers().getUser(map.getBestPlayer()).getName());
      } else {
        BowRunServer.getGameTablist().setFooter("§hRecord: §u- - -");
      }
    }
  }

  @Override
  public void onBeforeGameStart() {
    runnerArmor = List.of(false, false, false, false);
    if (BowRunServer.getMap().isRunnerArmor()) {
      int runnerSize = this.getGame().getRunnerTeam().getUsers().size();
      int size = Server.getInGameUsers().size();
      double runnerRatio = runnerSize / ((double) size);
      double ratioDiff = this.getGame().getRunnerTeam().getRatio() - runnerRatio;
      if (ratioDiff > 0.15) {
        runnerArmor = List.of(true, true, true, true);
      } else if (ratioDiff > 0.1) {
        runnerArmor = List.of(true, false, true, true);
      } else if (ratioDiff > 0.05) {
        runnerArmor = List.of(false, false, true, true);
      }
    }
  }

  @Override
  public void onGameStart() {

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
    if (archerKills == 0) {
      archerKills = 1;
    }

    for (User user : Server.getInGameUsers()) {
      ((BowRunUser) user).resetGameEquipment();

      if (((BowRunUser) user).getTeam().equals(archerTeam)) {
        user.addCoins((float) (((BowRunUser) user).getKills() / ((double) archerKills)
            * BowRunServer.KILL_COINS_POOL), true);
      }
    }

    EndMessage endMessage = new EndMessage()
        .winner(this.winType != null ? this.winType.getTeam() : null)
        .applyIf(this.winType == BowRunServer.WinType.ARCHER_TIME, e -> e.subTitle("Time is up"))
        .applyIf(this.winType == BowRunServer.WinType.RUNNER_FINISH, e -> e.subTitle(finisher.getTDChatName() + "§p " +
            "reached the goal"))
        .addStat("Kills", archerTeam.getUsers(), 3, GameUser::getKills)
        .addStat("Deaths", runnerTeam.getUsers(), 3, GameUser::getDeaths)
        .addStat("Longest Shot", archerTeam.getUsers(), 3, u -> u.getLongestShot() > 0, GameUser::getLongestShot);

    String recordTime = null;
    UUID lastRecord = null;
    BowRunMap map = BowRunServer.getMap();
    int time = map.getTime() - this.getPlayingTime();
    Integer oldRecord = map.getBestTime();

    // record check
    if (map.getBestPlayer() != null) {
      lastRecord = map.getBestPlayer();
    }

    if (winType == BowRunServer.WinType.RUNNER_FINISH && (oldRecord == null || oldRecord > time) && finisher != null) {
      recordTime = Chat.getTimeString(time);

      endMessage.addExtra("§hNew record: §v" + recordTime + " §hby " + finisher.getTDChatName());
      endMessage.send();

      this.recordVerification.checkRecord(time, finisher, map);
    } else {
      endMessage.send();
    }

    // stats
    List<String> stats = new ArrayList<>();

    stats.add("Teams (R vs. A): " + runnerTeam.getUsers().size() + " vs. " + archerTeam.getUsers().size());
    stats.add("Time: " + this.getPlayingTime() + " of " + map.getTime());
    stats.add("Map: " + map.getName());
    if (recordTime != null) {
      if (lastRecord != null) {
        stats.add("New Record: " + recordTime + " " + finisher.getUniqueId() + (oldRecord != null ?
            " (old: " + oldRecord + " " + lastRecord + ")" : ""));
      } else {
        stats.add("New Record: " + recordTime + " " + finisher.getUniqueId());
      }
    }

    stats.add("GameUserStats: Team Deaths Kills BowHits BowShots BowShotHits Hit/Shot");

    for (User user : Server.getGameNotServiceUsers()) {
      if (user.hasStatus(Status.User.SPECTATOR)) {
        continue;
      }

      GameUser gameUser = ((GameUser) user);

      if (gameUser.getTeam() == null) {
        continue;
      }

      if (gameUser.getBowShots() == 0) {
        stats.add(gameUser.getName() + ": " + gameUser.getTeam().getName() + " "
            + gameUser.getDeaths() + " " + gameUser.getKills() + " " + gameUser.getBowHits() + " "
            + gameUser.getBowShots() + " " + gameUser.getBowHitTarget() + " " + (gameUser.getBowHitTarget()));
      } else {
        stats.add(gameUser.getName() + ": " + gameUser.getTeam().getName() + " "
            + gameUser.getDeaths() + " " + gameUser.getKills() + " " + gameUser.getBowHits() + " "
            + gameUser.getBowShots() + " " + gameUser.getBowHitTarget() + " "
            + gameUser.getBowHitTarget() / ((double) gameUser.getBowShots()));
      }
    }

    this.logger.info("---- Stats ----");
    for (String line : stats) {
      this.logger.info(line);
    }
    this.logger.info("---- Stats ----");
  }

  @Override
  public void onGameUserQuit(GameUser user) {
    if (!user.getStatus().equals(Status.User.IN_GAME)) {
      return;
    }

    if (user.getTeam() == null) {
      return;
    }

    if (!user.getTeam().getUsers().isEmpty()) {
      return;
    }

    this.winType = user.getTeam().equals(this.getGame().getArcherTeam()) ? BowRunServer.WinType.RUNNER
            : BowRunServer.WinType.ARCHER;
    this.finisher = null;
    this.stopGame();
  }

  @Override
  public boolean checkGameEnd() {
    return this.getGame().getRunnerTeam().isEmpty() || this.getGame().getArcherTeam().isEmpty()
        || this.timerTool.getTime() == 0;
  }

  @Override
  public boolean isRejoiningAllowed() {
    return true;
  }

  @Override
  public void onGameUserRejoin(GameUser user) {
    user.respawn();
  }

  @Override
  public void onGameReset() {
    this.updateGameTimeOnSideboard();
    this.relayManager.reset();
    this.winType = null;
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

    this.sideboard.updateScore(LineId.TIME, this.getPlayingTime());
    this.spectatorSideboard.updateScore(LineId.TIME, this.getPlayingTime());

    if (this.getMap() != null) {
      this.timeBar.setTitle(Chat.getTimeString(this.getPlayingTime()));
      this.timeBar.setProgress(this.getPlayingTime() / ((double) this.getMap().getTime()));

      if (this.getPlayingTime() < this.getMap().getTime() / 10) {
        this.timeBar.setColor(BarColor.RED);
      } else if (this.getPlayingTime() < this.getMap().getTime() / 4) {
        this.timeBar.setColor(BarColor.YELLOW);
      }
    }

  }

  public void updateMapOnSideboard() {
    this.spectatorSideboard.updateScore(LineId.MAP,
        "§f" + BowRunServer.getMap().getDisplayName());
  }

  public RecordVerification getRecordVerification() {
    return recordVerification;
  }

  public ExItemStack getRandomRunnerItem() {
    return BowRunServer.RUNNER_ITEMS.get((int) ((Math.random() * BowRunServer.RUNNER_ITEMS.size()
            + Math.random() * BowRunServer.RUNNER_ITEMS.size()) / 2));
  }

  public ExItemStack getRandomArcherItem() {
    return BowRunServer.ARCHER_ITEMS.get((int) ((Math.random() * BowRunServer.ARCHER_ITEMS.size()
            + Math.random() * BowRunServer.ARCHER_ITEMS.size()) / 2));
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

  public List<Boolean> getRunnerArmor() {
    return this.runnerArmor;
  }

  @Override
  public Sideboard getSpectatorSideboard() {
    return this.spectatorSideboard;
  }

  @Override
  public ExLocation getSpectatorSpawn() {
    return this.getMap().getArcherSpawn();
  }

  public double getRunnerItemChance() {
    return (1 - ((double) this.getPlayingTime() / this.getMap().getTime()))
        * BowRunServer.RUNNER_ITEM_CHANCE_MULTIPLIER;
  }

  public void giveArcherSpecialItems() {
    if (!this.getMap().isOnlyInstant() && !this.getMap().isOnlyPunch() && !this.getMap()
        .isArcherNoSpecialItems()) {
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
          user.getStat(BowRunServer.RUNNER_WINS).increaseAllBy(1);
        }
        user.getStat(BowRunServer.DEATHS).increaseAllBy(user.getDeaths());
      } else if (user.getTeam().equals(this.getGame().getArcherTeam())) {
        if (this.winType.equals(BowRunServer.WinType.ARCHER_TIME)
            || this.winType.equals(BowRunServer.WinType.ARCHER)) {
          user.getStat(BowRunServer.ARCHER_WINS).increaseAllBy(1);
        }
        user.getStat(BowRunServer.KILLS).increaseAllBy(user.getKills());

        user.getStat(BowRunServer.LONGEST_SHOT).updateAllToMax(user.getLongestShot());
      }

      for (StatPeriod period : StatPeriod.values()) {
        Integer archerWins = user.getStat(BowRunServer.ARCHER_WINS).get(period);
        Integer runnerWins = user.getStat(BowRunServer.RUNNER_WINS).get(period);
        Integer gamesPlayed = user.getStat(GAMES_PLAYED).get(period);
        user.getStat(BowRunServer.WIN_CHANCE)
            .set(period, (archerWins + runnerWins) / ((float) gamesPlayed));
      }
    }

    if (user.getTeam().equals(this.getGame().getArcherTeam())) {
      user.getStat(BowRunServer.MOST_KILLS_PER_MATCH).updateAllToMax(user.getKills());
    }

  }

  @Override
  public Set<StatType<?>> getStats() {
    return Set.of(BowRunServer.RUNNER_WINS, BowRunServer.ARCHER_WINS, BowRunServer.WIN_CHANCE,
        BowRunServer.KILLS,
        BowRunServer.DEATHS, BowRunServer.MOST_KILLS_PER_MATCH, BowRunServer.LONGEST_SHOT);
  }

  public static class ArrowGenerator implements GameTool, StartableTool, StopableTool {

    private BukkitTask task;

    @Override
    public void start() {
      int periodInTicks = (int) (BowRunServer.ARROW_GENERATION_PERIOD +
                                 Math.sqrt(BowRunServer.ARROW_GENERATION_PLAYER_MULTIPLIER *
                                           BowRunServer.getGame().getArcherTeam().getInGameUsers().size()));

      this.task = Server.runTaskTimerSynchrony(() -> {
        if (BowRunServer.getMap() != null) {
          for (TeamUser user : BowRunServer.getGame().getArcherTeam().getInGameUsers()) {
            int delta = user.containsAtLeast(BowRunUser.ARROW, BowRunServer.MAX_ARROWS, true);
            if (delta < 0) {
              user.addItem(BowRunUser.ARROW.cloneWithId().asQuantity(Math.min(BowRunServer.RESPAWN_ARROW_AMOUNT,
                  -delta)));
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
  }
}
