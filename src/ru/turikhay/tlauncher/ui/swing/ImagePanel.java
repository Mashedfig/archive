package ru.turikhay.tlauncher.ui.swing;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.ImageObserver;
import ru.turikhay.tlauncher.ui.swing.extended.ExtendedPanel;
import ru.turikhay.util.U;

public class ImagePanel extends ExtendedPanel {
   protected final Object animationLock = new Object();
   private Image originalImage;
   private Image image;
   private float activeOpacity;
   private float nonActiveOpacity;
   private boolean antiAlias;
   private int timeFrame;
   private float opacity;
   private boolean hover;
   private boolean shown;
   private boolean animating;

   protected ImagePanel(Image image, float activeOpacity, float nonActiveOpacity, boolean shown, boolean antiAlias) {
      this.setImage(image);
      this.setActiveOpacity(activeOpacity);
      this.setNonActiveOpacity(nonActiveOpacity);
      this.setAntiAlias(antiAlias);
      this.shown = shown;
      this.opacity = shown ? nonActiveOpacity : 0.0F;
      this.timeFrame = 10;
      this.setBackground(new Color(0, 0, 0, 0));
      this.addMouseListenerOriginally(new MouseAdapter() {
         public void mouseClicked(MouseEvent e) {
            ImagePanel.this.onClick();
         }

         public void mouseEntered(MouseEvent e) {
            ImagePanel.this.onMouseEntered();
         }

         public void mouseExited(MouseEvent e) {
            ImagePanel.this.onMouseExited();
         }
      });
   }

   protected void setImage(Image image, boolean resetSize) {
      Object var3 = this.animationLock;
      synchronized(this.animationLock) {
         this.originalImage = image;
         this.image = image;
         if (resetSize && image != null) {
            this.setSize(image.getWidth((ImageObserver)null), image.getHeight((ImageObserver)null));
         }

      }
   }

   protected void setImage(Image image) {
      this.setImage(image, true);
   }

   protected void setActiveOpacity(float opacity) {
      if (opacity <= 1.0F && opacity >= 0.0F) {
         this.activeOpacity = opacity;
      } else {
         throw new IllegalArgumentException("Invalid opacity! Condition: 0.0F <= opacity (got: " + opacity + ") <= 1.0F");
      }
   }

   protected void setNonActiveOpacity(float opacity) {
      if (opacity <= 1.0F && opacity >= 0.0F) {
         this.nonActiveOpacity = opacity;
      } else {
         throw new IllegalArgumentException("Invalid opacity! Condition: 0.0F <= opacity (got: " + opacity + ") <= 1.0F");
      }
   }

   protected void setAntiAlias(boolean set) {
      this.antiAlias = set;
   }

   public void paintComponent(Graphics g0) {
      if (this.image != null) {
         Graphics2D g = (Graphics2D)g0;
         Composite oldComp = g.getComposite();
         g.setComposite(AlphaComposite.getInstance(3, this.opacity));
         g.drawImage(this.image, 0, 0, this.getWidth(), this.getHeight(), (ImageObserver)null);
         g.setComposite(oldComp);
      }

   }

   public void show() {
      if (!this.shown) {
         this.shown = true;
         Object var1 = this.animationLock;
         synchronized(this.animationLock) {
            this.animating = true;
            this.setVisible(true);
            this.opacity = 0.0F;
            float selectedOpacity = this.hover ? this.activeOpacity : this.nonActiveOpacity;

            while(this.opacity < selectedOpacity) {
               this.opacity += 0.01F;
               if (this.opacity > selectedOpacity) {
                  this.opacity = selectedOpacity;
               }

               this.repaint();
               U.sleepFor((long)this.timeFrame);
            }

            this.animating = false;
         }
      }

   }

   public void hide() {
      if (this.shown) {
         this.shown = false;
         Object var1 = this.animationLock;
         synchronized(this.animationLock) {
            this.animating = true;

            while(this.opacity > 0.0F) {
               this.opacity -= 0.01F;
               if (this.opacity < 0.0F) {
                  this.opacity = 0.0F;
               }

               this.repaint();
               U.sleepFor((long)this.timeFrame);
            }

            this.setVisible(false);
            this.animating = false;
         }
      }

   }

   protected boolean onClick() {
      return this.shown;
   }

   protected void onMouseEntered() {
      this.hover = true;
      if (!this.animating && this.shown) {
         this.opacity = this.activeOpacity;
         this.repaint();
      }

   }

   protected void onMouseExited() {
      this.hover = false;
      if (!this.animating && this.shown) {
         this.opacity = this.nonActiveOpacity;
         this.repaint();
      }

   }
}
