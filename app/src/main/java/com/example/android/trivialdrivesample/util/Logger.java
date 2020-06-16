package com.example.android.trivialdrivesample.util;

import android.util.Log;

/**
 * @Author Ehsan Abbasi for hope-trivial-driver
 * Create time: 26/04/2020
 */
public class Logger {

  private static final String splitterForLogs = "->\n---------------------------------\n";

  public static void debug(String tag, String message) {
    Log.d(tag, splitterForLogs + message + splitterForLogs);
  }

  public static void error(String tag, String message) {
    Log.e(tag, splitterForLogs + message + splitterForLogs);
  }

  public static void warning(String tag, String message) {
    Log.w(tag, splitterForLogs + message + splitterForLogs);
  }
}
