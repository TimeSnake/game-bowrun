/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.game.bowrun.server;

import de.timesnake.basic.game.util.game.Team;
import de.timesnake.basic.loungebridge.util.game.TmpGame;
import de.timesnake.database.util.game.DbMap;
import de.timesnake.database.util.game.DbTmpGame;

public class BowRunGame extends TmpGame {

  public static final String RUNNER_TEAM_NAME = "runner";
  public static final String ARCHER_TEAM_NAME = "archer";

  public BowRunGame(DbTmpGame game, boolean loadWorlds) {
    super(game, loadWorlds);
  }

  @Override
  public BowRunMap loadMap(DbMap dbMap, boolean loadWorld) {
    return new BowRunMap(dbMap);
  }

  public Team getRunnerTeam() {
    return super.getTeam(RUNNER_TEAM_NAME);
  }

  public Team getArcherTeam() {
    return super.getTeam(ARCHER_TEAM_NAME);
  }
}
