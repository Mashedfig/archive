package ru.turikhay.tlauncher.minecraft.auth;

import java.util.Random;
import org.apache.commons.lang3.StringUtils;

public class GameProfile {
   public static final GameProfile DEFAULT_PROFILE = new GameProfile("0", "(Default)");
   private final String id;
   private final String name;

   private GameProfile(String id, String name) {
      if (StringUtils.isBlank(id) && StringUtils.isBlank(name)) {
         throw new IllegalArgumentException("Name and ID cannot both be blank");
      } else {
         this.id = id;
         this.name = name + (new Random()).nextInt();
      }
   }

   public String getId() {
      return this.id;
   }

   public String getName() {
      return this.name;
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         GameProfile that = (GameProfile)o;
         return !this.id.equals(that.id) ? false : this.name.equals(that.name);
      } else {
         return false;
      }
   }

   public int hashCode() {
      int result = this.id.hashCode();
      result = 31 * result + this.name.hashCode();
      return result;
   }

   public String toString() {
      return "GameProfile{id='" + this.id + '\'' + ", name='" + this.name + '\'' + '}';
   }
}
