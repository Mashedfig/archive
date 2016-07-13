package ru.turikhay.tlauncher.ui.listener;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import ru.turikhay.tlauncher.TLauncher;
import ru.turikhay.tlauncher.repository.Repository;
import ru.turikhay.tlauncher.ui.alert.Alert;
import ru.turikhay.tlauncher.ui.block.Blockable;
import ru.turikhay.tlauncher.ui.block.Blocker;
import ru.turikhay.tlauncher.updater.Update;
import ru.turikhay.tlauncher.updater.UpdateListener;
import ru.turikhay.util.OS;
import ru.turikhay.util.U;

public class UpdateUIListener implements UpdateListener {
   private final TLauncher t;
   private final Update u;

   public UpdateUIListener(Update u) {
      if (u == null) {
         throw new NullPointerException();
      } else {
         this.t = TLauncher.getInstance();
         this.u = u;
         u.addListener(this);
      }
   }

   public void push() {
      this.block();
      this.u.download(true);
   }

   public void onUpdateError(Update u, Throwable e) {
      if (Alert.showLocQuestion("updater.error.title", "updater.download-error", e)) {
         URLConnection connection;
         try {
            connection = Repository.EXTRA_VERSION_REPO.getRelevant().getFirst().get(u.getLink().substring(1), U.getReadTimeout(), U.getProxy());
         } catch (IOException var5) {
            U.log(var5);
            return;
         }

         openUpdateLink(connection.getURL().toString());
      }

      this.unblock();
   }

   public void onUpdateDownloading(Update u) {
   }

   public void onUpdateDownloadError(Update u, Throwable e) {
      this.onUpdateError(u, e);
   }

   public void onUpdateReady(Update u) {
      onUpdateReady(u, false);
   }

   private static void onUpdateReady(Update u, boolean showChangeLog) {
      Alert.showLocWarning("updater.downloaded", showChangeLog ? u.getDescription() : null);
      u.apply();
   }

   public void onUpdateApplying(Update u) {
   }

   public void onUpdateApplyError(Update u, Throwable e) {
      if (Alert.showLocQuestion("updater.save-error", e)) {
         URLConnection connection;
         try {
            connection = Repository.EXTRA_VERSION_REPO.getRelevant().getFirst().get(u.getLink().substring(1), U.getReadTimeout(), U.getProxy());
         } catch (IOException var5) {
            U.log(var5);
            return;
         }

         openUpdateLink(connection.getURL().toString());
      }

      this.unblock();
   }

   private static boolean openUpdateLink(String link) {
      try {
         if (OS.openLink(new URI(link), false)) {
            return true;
         }
      } catch (URISyntaxException var2) {
      }

      Alert.showLocError("updater.found.cannotopen", link);
      return false;
   }

   private void block() {
      Blocker.block((Blockable)this.t.getFrame().mp, (Object)"updater");
   }

   private void unblock() {
      Blocker.unblock((Blockable)this.t.getFrame().mp, (Object)"updater");
   }
}
