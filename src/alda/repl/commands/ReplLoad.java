package alda.repl.commands;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileAlreadyExistsException;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.util.stream.Stream;
import java.util.function.Consumer;

import alda.AldaServer;
import alda.AldaResponse;
import alda.AldaResponse.AldaScore;
import alda.Util;
import jline.console.ConsoleReader;

/**
 * Handles the :load command.
 * Takes a filename as a paramter.
 */
public class ReplLoad implements ReplCommand {

  private ReplCommandManager cmdManager;

  public ReplLoad(ReplCommandManager m) {
    cmdManager = m;
  }

  private String oldSaveFile() {
    return cmdManager.getSaveFile();
  }
  private void setOldSaveFile(String s) {
    cmdManager.setSaveFile(s);
  }

  /**
   * Helper to load a file from disk
   * @args args the path to load from
   * @args server the server to load/save from
   * @args history buffer to modify
   */
  private void loadFile(String args, AldaServer server, StringBuffer history, Consumer<AldaScore> newInstrument) {
    Stream<String> fLines = null;
    boolean error = false;
    try {
      fLines = Files.lines(Paths.get(args));
      StringBuffer newHistory = new StringBuffer();
      fLines.forEach(x -> {
          newHistory.append(x);
          newHistory.append("\n");
        });

      // Check if the score we just loaded is valid (prevent further errors)
      String res = "";
      try {
        // TODO: include a jobId, add parsing to job system on the server-side
        res = server.parseRaw(newHistory.toString(), false);
      } catch (Throwable e) {
        server.error(e.getMessage());
        // Don't change 'history'
        error = true;
      }

      if (!error) {
        history.delete(0, history.length());
        history.append(newHistory);

        // Set new prompt string if possible
        newInstrument.accept(AldaResponse.fromJsonScore(res));

        // Save the loaded filename to shorten :save/:loads
        setOldSaveFile(args);
      }
    } catch (IOException|UncheckedIOException e) {
      System.err.println("There was an error reading '" + args + "'");
    } finally {
      if (fLines != null)
        fLines.close();
    }
  }

  @Override
  public void act(String args, StringBuffer history, AldaServer server,
                  ConsoleReader reader, Consumer<AldaScore> newInstrument) {
    if (args.length() == 0) {
      // We will try to load from the last saved file
        if (oldSaveFile() != null && oldSaveFile().length() != 0) {
          // Just assume the user typed in the last given file.
          loadFile(oldSaveFile(), server, history, newInstrument);
        } else {
          usage();
        }
        return;
    }
    // Turn ~ into home
    args = args.replaceFirst("^~",System.getProperty("user.home"));
    loadFile(args, server, history, newInstrument);

  }
  @Override
  public String docSummary() {
    return "Loads an Alda score into the current REPL session.";
  }
  @Override
  public String docDetails() {
    return "Usage:\n\n" +
      "  :load test/examples/bach_cello_suite_no_1.alda\n" +
      "  :load /Users/rick/Scores/love_is_alright_tonite.alda" +
      "Once :load/:save has been executed once:\n" +
      "  :load";
  }
  @Override
  public String key() {
    return "load";
  }
}
