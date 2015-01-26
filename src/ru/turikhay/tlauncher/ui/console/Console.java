package ru.turikhay.tlauncher.ui.console;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import ru.turikhay.tlauncher.TLauncher;
import ru.turikhay.tlauncher.configuration.Configuration;
import ru.turikhay.tlauncher.minecraft.launcher.MinecraftLauncher;
import ru.turikhay.tlauncher.ui.swing.extended.ExtendedComponentAdapter;
import ru.turikhay.util.StringUtil;
import ru.turikhay.util.U;
import ru.turikhay.util.async.AsyncThread;
import ru.turikhay.util.stream.LinkedStringStream;
import ru.turikhay.util.stream.Logger;
import ru.turikhay.util.stream.PrintLogger;
import ru.turikhay.util.stream.StringStream;

public class Console implements Logger {
   private static List frames = Collections.synchronizedList(new ArrayList());
   public final ConsoleFrame frame;
   private final Configuration global;
   private String name;
   private LinkedStringStream stream;
   private PrintLogger logger;
   private Console.CloseAction close;
   private boolean killed;
   MinecraftLauncher launcher;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$ru$turikhay$tlauncher$ui$console$Console$CloseAction;

   public Console(Configuration global, PrintLogger logger, String name, boolean show) {
      this.global = global;
      this.frame = new ConsoleFrame(this);
      this.setName(name);
      frames.add(this.frame);
      this.update();
      this.frame.addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent e) {
            Console.this.save();
            Console.this.onClose();
         }

         public void windowClosed(WindowEvent e) {
            U.log("Console", Console.this.name, "has been disposed.");
         }
      });
      this.frame.addComponentListener(new ExtendedComponentAdapter(this.frame) {
         public void componentShown(ComponentEvent e) {
            Console.this.save(true);
         }

         public void componentHidden(ComponentEvent e) {
            Console.this.save(true);
         }

         public void onComponentResized(ComponentEvent e) {
            Console.this.save(true);
         }

         public void onComponentMoved(ComponentEvent e) {
            Console.this.save(true);
         }
      });
      this.frame.addComponentListener(new ComponentListener() {
         public void componentResized(ComponentEvent e) {
            Console.this.save(false);
         }

         public void componentMoved(ComponentEvent e) {
            Console.this.save(false);
         }

         public void componentShown(ComponentEvent e) {
            Console.this.save(true);
         }

         public void componentHidden(ComponentEvent e) {
            Console.this.save(true);
         }
      });
      if (logger == null) {
         this.logger = null;
         this.stream = new LinkedStringStream();
         this.stream.setLogger(this);
      } else {
         this.logger = logger;
         this.stream = logger.getStream();
      }

      if (show) {
         this.show();
      }

      if (this.stream.getLength() != 0) {
         this.rawlog(this.stream.getOutput());
      }

      if (logger != null) {
         logger.setMirror(this);
      }

   }

   public Console(PrintLogger logger, String name) {
      this((Configuration)null, logger, name, true);
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
      this.frame.setTitle(name);
   }

   public MinecraftLauncher getLauncher() {
      return this.launcher;
   }

   public void setLauncher(MinecraftLauncher launcher) {
      this.launcher = launcher;
      this.frame.bottom.kill.setEnabled(launcher != null);
   }

   public void log(String s) {
      if (this.logger != null) {
         this.logger.rawlog(s);
      } else {
         this.stream.write(s.toCharArray());
      }

   }

   public void log(Object... o) {
      this.log(U.toLog(o));
   }

   public void rawlog(String s) {
      if (StringUtil.lastChar(s) == '\n') {
         this.frame.print(s);
      } else {
         this.frame.println(s);
      }

   }

   public void rawlog(Object... o) {
      this.rawlog(U.toLog(o));
   }

   public void rawlog(char[] c) {
      this.rawlog(new String(c));
   }

   public PrintLogger getLogger() {
      return this.logger;
   }

   public String getOutput() {
      return this.stream.getOutput();
   }

   StringStream getStream() {
      return this.stream;
   }

   void update() {
      this.check();
      if (this.global != null) {
         String prefix = "gui.console.";
         int width = this.global.getInteger(prefix + "width", 670);
         int height = this.global.getInteger(prefix + "height", 500);
         int x = this.global.getInteger(prefix + "x", 0);
         int y = this.global.getInteger(prefix + "y", 0);
         this.frame.setSize(width, height);
         this.frame.setLocation(x, y);
      }
   }

   void save() {
      this.save(false);
   }

   void save(boolean flush) {
      this.check();
      if (this.global != null) {
         String prefix = "gui.console.";
         int[] size = this.getSize();
         int[] position = this.getPosition();
         this.global.set(prefix + "width", size[0], false);
         this.global.set(prefix + "height", size[1], false);
         this.global.set(prefix + "x", position[0], false);
         this.global.set(prefix + "y", position[1], false);
      }
   }

   private void check() {
      if (this.killed) {
         throw new IllegalStateException("Console is already killed!");
      }
   }

   public void setShown(boolean shown) {
      if (shown) {
         this.show();
      } else {
         this.hide();
      }

   }

   public void show() {
      this.show(true);
   }

   public void show(boolean toFront) {
      this.check();
      this.frame.setVisible(true);
      this.frame.scrollDown();
      if (toFront) {
         this.frame.toFront();
      }

   }

   public void hide() {
      this.check();
      this.frame.setVisible(false);
   }

   public void clear() {
      this.check();
      this.frame.clear();
      this.stream.clear();
   }

   public void kill() {
      this.check();
      this.save();
      this.frame.dispose();
      this.frame.clear();
      frames.remove(this.frame);
      this.killed = true;
   }

   public void killIn(final long millis) {
      this.check();
      this.save();
      this.frame.hideIn(millis);
      AsyncThread.execute(new Runnable() {
         public void run() {
            U.sleepFor(millis + 1000L);
            if (Console.this.isHidden()) {
               Console.this.kill();
            }

         }
      });
   }

   public boolean isKilled() {
      this.check();
      return this.killed;
   }

   boolean isHidden() {
      this.check();
      return !this.frame.isShowing();
   }

   Point getPositionPoint() {
      this.check();
      return this.frame.getLocation();
   }

   int[] getPosition() {
      this.check();
      Point p = this.getPositionPoint();
      return new int[]{p.x, p.y};
   }

   Dimension getDimension() {
      this.check();
      return this.frame.getSize();
   }

   int[] getSize() {
      this.check();
      Dimension d = this.getDimension();
      return new int[]{d.width, d.height};
   }

   public Console.CloseAction getCloseAction() {
      return this.close;
   }

   public void setCloseAction(Console.CloseAction action) {
      this.close = action;
   }

   private void onClose() {
      if (this.close != null) {
         switch($SWITCH_TABLE$ru$turikhay$tlauncher$ui$console$Console$CloseAction()[this.close.ordinal()]) {
         case 1:
            this.kill();
         case 2:
            TLauncher.kill();
         default:
         }
      }
   }

   public static void updateLocale() {
      Iterator var1 = frames.iterator();

      while(var1.hasNext()) {
         ConsoleFrame frame = (ConsoleFrame)var1.next();
         frame.updateLocale();
      }

   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$ru$turikhay$tlauncher$ui$console$Console$CloseAction() {
      int[] var10000 = $SWITCH_TABLE$ru$turikhay$tlauncher$ui$console$Console$CloseAction;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[Console.CloseAction.values().length];

         try {
            var0[Console.CloseAction.EXIT.ordinal()] = 2;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[Console.CloseAction.KILL.ordinal()] = 1;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$ru$turikhay$tlauncher$ui$console$Console$CloseAction = var0;
         return var0;
      }
   }

   public static enum CloseAction {
      KILL,
      EXIT;
   }
}
