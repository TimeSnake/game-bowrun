/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.game.bowrun.chat;

import de.timesnake.library.basic.util.LogHelper;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Plugin extends de.timesnake.basic.game.util.user.Plugin {

  public static final Plugin BOWRUN = new Plugin("BowRun", "BWR",
      LogHelper.getLogger("BowRun", Level.INFO));

  protected Plugin(String name, String code, Logger logger) {
    super(name, code, logger);
  }
}
