package com.turikhay.tlauncher.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Panel;

public class SettingsPanel extends Panel {
   private static final long serialVersionUID = 4212962090384406608L;
   final SettingsForm sf;

   public SettingsPanel(SettingsForm settingsform) {
      this.sf = settingsform;
   }

   void createInterface() {
      this.setLayout(new GridBagLayout());
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.anchor = 17;
      constraints.gridy = 0;
      constraints.weightx = 0.0D;
      constraints.fill = 0;
      this.add(this.sf.cdel(0, 120, 5), constraints);
      constraints.fill = 2;
      constraints.weightx = 1.0D;
      this.add(this.sf.cdel(0, 150, 5), constraints);
      ++constraints.gridy;
      constraints.weightx = 0.0D;
      constraints.fill = 0;
      this.add(this.sf.gameDirCustom, constraints);
      constraints.fill = 2;
      constraints.weightx = 1.0D;
      this.add(this.sf.gameDirField, constraints);
      ++constraints.gridy;
      constraints.weightx = 0.0D;
      constraints.fill = 0;
      this.add(this.sf.resolutionCustom, constraints);
      constraints.fill = 2;
      constraints.weightx = 1.0D;
      this.add(this.sf.resolutionField, constraints);
      ++constraints.gridy;
      constraints.weightx = 0.0D;
      constraints.fill = 0;
      this.add(this.sf.cdel(1, 120, 5), constraints);
      constraints.fill = 2;
      constraints.weightx = 1.0D;
      this.add(this.sf.cdel(1, 150, 5), constraints);
      ++constraints.gridy;
      constraints.weightx = 0.0D;
      constraints.fill = 0;
      this.add(this.sf.versionChoice, constraints);
      constraints.fill = 2;
      constraints.weightx = 1.0D;
      this.add(this.sf.versionsPan, constraints);
      ++constraints.gridy;
      constraints.weightx = 0.0D;
      constraints.fill = 0;
      this.add(this.sf.cdel(0, 120, 5), constraints);
      constraints.fill = 2;
      constraints.weightx = 1.0D;
      this.add(this.sf.cdel(0, 150, 5), constraints);
      ++constraints.gridy;
      constraints.weightx = 0.0D;
      constraints.fill = 0;
      this.add(this.sf.pathCustom, constraints);
      constraints.fill = 2;
      constraints.weightx = 1.0D;
      this.add(this.sf.pathCustomField, constraints);
      ++constraints.gridy;
      constraints.weightx = 0.0D;
      constraints.fill = 0;
      this.add(this.sf.argsCustom, constraints);
      constraints.fill = 2;
      constraints.weightx = 1.0D;
      this.add(this.sf.argsCustomField, constraints);
      ++constraints.gridy;
      constraints.weightx = 0.0D;
      constraints.fill = 0;
      this.add(this.sf.tlauncherSettings, constraints);
      constraints.fill = 2;
      constraints.weightx = 1.0D;
      this.add(this.sf.tlauncherPan, constraints);
      ++constraints.gridy;
      constraints.weightx = 0.0D;
      constraints.fill = 0;
      this.add(this.sf.autologinCustom, constraints);
      constraints.fill = 2;
      constraints.weightx = 1.0D;
      this.add(this.sf.autologinField, constraints);
      ++constraints.gridy;
      constraints.weightx = 0.0D;
      constraints.fill = 0;
      this.add(this.sf.cdel(0, 120, 5), constraints);
      constraints.fill = 2;
      constraints.weightx = 1.0D;
      this.add(this.sf.cdel(0, 150, 5), constraints);
   }
}
