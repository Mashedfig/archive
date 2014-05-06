package net.minecraft.launcher.versions;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.turikhay.tlauncher.repository.Repository;
import ru.turikhay.util.OS;
import ru.turikhay.util.U;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import net.minecraft.launcher.updater.VersionList;
import net.minecraft.launcher.versions.json.DateTypeAdapter;
import net.minecraft.launcher.versions.json.LowerCaseEnumTypeAdapterFactory;

public class CompleteVersion implements Version, Cloneable {
	String id, original_id;

	Date time;
	Date releaseTime;

	ReleaseType type;

	String jvmArguments;
	String minecraftArguments;
	String mainClass;

	List<Library> libraries;
	List<Rule> rules;
	List<String> unnecessaryEntries;

	Integer minimumLauncherVersion = Integer.valueOf(0);
	Integer tlauncherVersion = Integer.valueOf(0);

	String assets;

	Repository source;
	VersionList list;

	@Override
	public String getID() {
		return id;
	}

	@Override
	public void setID(String id) {
		if (id == null || id.isEmpty())
			throw new IllegalArgumentException("ID is NULL or empty");
		this.id = id;
	}

	@Override
	public ReleaseType getReleaseType() {
		return type;
	}

	@Override
	public Repository getSource() {
		return source;
	}

	@Override
	public void setSource(Repository repository) {
		if (repository == null)
			throw new NullPointerException();

		this.source = repository;
	}

	@Override
	public Date getUpdatedTime() {
		return time;
	}

	public void setUpdatedTime(Date time) {
		if (time == null)
			throw new NullPointerException("Time is NULL!");
		this.time = time;
	}

	@Override
	public Date getReleaseTime() {
		return releaseTime;
	}

	@Override
	public VersionList getVersionList() {
		return list;
	}

	@Override
	public void setVersionList(VersionList list) {
		if (list == null)
			throw new NullPointerException("VersionList cannot be NULL!");

		this.list = list;
	}

	public String getOriginal() {
		return original_id;
	}

	public String getJVMArguments() {
		return jvmArguments;
	}

	public String getMinecraftArguments() {
		return minecraftArguments;
	}

	public String getMainClass() {
		return mainClass;
	}

	public List<Library> getLibraries() {
		return Collections.unmodifiableList(libraries);
	}

	public List<Rule> getRules() {
		return Collections.unmodifiableList(rules);
	}

	public List<String> getRemovableEntries() {
		return unnecessaryEntries;
	}

	public int getMinimumLauncherVersion() {
		return minimumLauncherVersion;
	}

	public int getMinimumCustomLauncherVersion() {
		return tlauncherVersion;
	}

	public String getAssets() {
		return assets;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null)
			return false;
		if (this.hashCode() == o.hashCode())
			return true;

		if (!(o instanceof Version))
			return false;

		Version compare = (Version) o;
		if (compare.getID() == null)
			return false;

		return compare.getID().equals(id);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "{id='" + id + "', time=" + time
				+ ", release=" + releaseTime + ", type=" + type + ", class="
				+ mainClass + ", minimumVersion=" + minimumLauncherVersion
				+ ", assets='" + assets + "', source=" + source + ", list="
				+ list + ", libraries=" + libraries + "}";
	}

	public File getFile(File base) {
		return new File(base, "versions/" + getID() + "/" + getID() + ".jar");
	}

	public boolean appliesToCurrentEnvironment() {
		if (this.rules == null)
			return true;

		for (Rule rule : this.rules) {
			Rule.Action action = rule.getAppliedAction();

			if (action == Rule.Action.DISALLOW)
				return false;
		}

		return true;
	}

	public Collection<Library> getRelevantLibraries() {
		List<Library> result = new ArrayList<Library>();

		for (Library library : this.libraries)
			if (library.appliesToCurrentEnvironment())
				result.add(library);

		return result;
	}

	public Collection<File> getClassPath(OS os, File base) {
		Collection<Library> libraries = getRelevantLibraries();
		Collection<File> result = new ArrayList<File>();

		for (Library library : libraries) {
			if (library.getNatives() == null)
				result.add(new File(base, "libraries/"
						+ library.getArtifactPath()));
		}

		result.add(new File(base, "versions/" + getID() + "/" + getID()
				+ ".jar"));

		return result;
	}
	
	public Collection<File> getClassPath(File base) {
		return getClassPath(OS.CURRENT, base);
	}

	public Collection<String> getNatives(OS os) {
		Collection<Library> libraries = getRelevantLibraries();
		Collection<String> result = new ArrayList<String>();

		for (Library library : libraries) {
			Map<OS, String> natives = library.getNatives();

			if (natives != null && natives.containsKey(os))
				result.add("libraries/"
						+ library.getArtifactPath(natives.get(os)));
		}

		return result;
	}
	
	public Collection<String> getNatives() {
		return getNatives(OS.CURRENT);
	}

	public Set<String> getRequiredFiles(OS os) {
		Set<String> neededFiles = new HashSet<String>();

		for (Library library : getRelevantLibraries()) {
			if (library.getNatives() != null) {
				String natives = library.getNatives().get(os);

				if (natives != null)
					neededFiles.add("libraries/"
							+ library.getArtifactPath(natives));

			} else {
				neededFiles.add("libraries/" + library.getArtifactPath());
			}
		}

		return neededFiles;
	}
	
	@Override
	public Object cloneSafely() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

	public Collection<String> getExtractFiles(OS os) {
		Collection<Library> libraries = getRelevantLibraries();
		Collection<String> result = new ArrayList<String>();

		for (Library library : libraries) {
			Map<OS, String> natives = library.getNatives();

			if (natives != null && natives.containsKey(os))
				result.add("libraries/"
						+ library.getArtifactPath(natives.get(os)));
		}

		return result;
	}

	public static class CompleteVersionSerializer implements
			JsonSerializer<CompleteVersion>, JsonDeserializer<CompleteVersion> {
		private final Gson defaultContext;

		public CompleteVersionSerializer() {
			GsonBuilder builder = new GsonBuilder();
			builder.registerTypeAdapterFactory(new LowerCaseEnumTypeAdapterFactory());
			builder.registerTypeAdapter(Date.class, new DateTypeAdapter());
			builder.enableComplexMapKeySerialization();
			builder.setPrettyPrinting();

			this.defaultContext = builder.create();
		}

		@Override
		public CompleteVersion deserialize(JsonElement elem, Type type,
				JsonDeserializationContext context) throws JsonParseException {
			// TODO remove in far future
			JsonObject object = elem.getAsJsonObject();
			JsonElement sourceElement = object.get("source");

			if (sourceElement != null && !sourceElement.isJsonPrimitive())
				object.remove("source");

			CompleteVersion version = defaultContext.fromJson(elem,
					CompleteVersion.class);

			if (version.id == null)
				throw new JsonParseException("Version ID is NULL!");

			if (version.type == null)
				version.type = ReleaseType.UNKNOWN;

			if (version.source == null)
				version.source = Repository.LOCAL_VERSION_REPO;

			if (version.time == null)
				version.time = new Date(0);

			if (version.assets == null)
				version.assets = "legacy";

			return version;
		}

		@Override
		public JsonElement serialize(CompleteVersion version0, Type type,
				JsonSerializationContext context) {
			CompleteVersion version;

			try {
				version = (CompleteVersion) version0.clone();
			} catch (CloneNotSupportedException e) {
				U.log("Cloning of CompleteVersion is not supported O_o", e);
				return defaultContext.toJsonTree(version0, type);
			}

			version.list = null;

			return defaultContext.toJsonTree(version, type);
		}
	}
}
