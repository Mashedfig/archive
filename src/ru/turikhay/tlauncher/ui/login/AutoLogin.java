package ru.turikhay.tlauncher.ui.login;

import ru.turikhay.tlauncher.ui.login.buttons.ButtonPanel;
import ru.turikhay.util.U;
import ru.turikhay.util.async.AsyncThread;

public class AutoLogin implements LoginForm.LoginProcessListener {
   private boolean enabled;
   private boolean active;
   private int timeout;
   private int sec;
   private Runnable task;
   private final LoginForm loginForm;

   AutoLogin(LoginForm lf) {
      this.loginForm = lf;
      this.enabled = lf.global.getBoolean("login.auto");
      int timeout = lf.global.getInteger("login.auto.timeout");
      if (timeout < 2 || timeout > 10) {
         timeout = 3;
      }

      this.timeout = timeout;
      this.task = new Runnable() {
         public void run() {
            while(AutoLogin.this.sec > 0) {
               U.sleepFor(1000L);
               if (AutoLogin.this.updateLogin()) {
                  AutoLogin.this.loginForm.startLauncher();
               }
            }

         }
      };
   }

   private boolean updateLogin() {
      --this.sec;
      this.loginForm.buttons.cancel.setText("loginform.cancel", new Object[]{this.sec});
      if (this.sec != 0) {
         return false;
      } else {
         this.stopActive();
         return true;
      }
   }

   public void setActive(boolean active) {
      if (this.active != active) {
         this.active = active;
         if (active) {
            this.startActive();
         } else {
            this.stopActive();
         }
      }

   }

   private void startActive() {
      this.sec = this.timeout;
      AsyncThread.execute(this.task);
   }

   private void stopActive() {
      this.sec = -1;
      this.loginForm.buttons.setState(ButtonPanel.ButtonPanelState.MANAGE_BUTTONS);
   }

   public void setEnabled(boolean enabled) {
      if (this.enabled != enabled) {
         this.enabled = enabled;
         if (this.active) {
            this.setActive(enabled);
         }

         if (this.loginForm.checkbox.autologin.isSelected() != enabled) {
            this.loginForm.checkbox.autologin.setSelected(enabled);
         }

         this.loginForm.global.set("login.auto", enabled);
      }

   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public int getTimeout() {
      return this.timeout;
   }

   public void logginingIn() throws LoginException {
      this.setActive(false);
   }

   public void loginFailed() {
   }

   public void loginSucceed() {
   }
}
