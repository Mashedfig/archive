package ru.turikhay.tlauncher.minecraft.launcher;

import com.google.gson.Gson;
import net.minecraft.launcher.process.JavaProcess;
import net.minecraft.launcher.process.JavaProcessLauncher;
import net.minecraft.launcher.process.JavaProcessListener;
import net.minecraft.launcher.process.ProcessMonitorThread;
import net.minecraft.launcher.updater.AssetIndex;
import net.minecraft.launcher.updater.VersionSyncInfo;
import net.minecraft.launcher.versions.*;
import net.minecraft.launcher.versions.json.DateTypeAdapter;
import net.minecraft.options.OptionsFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import ru.turikhay.app.nstweaker.NSTweaker;
import ru.turikhay.tlauncher.TLauncher;
import ru.turikhay.tlauncher.configuration.Configuration;
import ru.turikhay.tlauncher.connection.ConnectionHelper;
import ru.turikhay.tlauncher.downloader.AbortedDownloadException;
import ru.turikhay.tlauncher.downloader.DownloadableContainer;
import ru.turikhay.tlauncher.downloader.Downloader;
import ru.turikhay.tlauncher.handlers.ExceptionHandler;
import ru.turikhay.tlauncher.managers.*;
import ru.turikhay.tlauncher.minecraft.*;
import ru.turikhay.tlauncher.minecraft.auth.Account;
import ru.turikhay.tlauncher.minecraft.crash.CrashManager;
import ru.turikhay.tlauncher.sentry.Sentry;
import ru.turikhay.tlauncher.sentry.SentryBreadcrumb;
import ru.turikhay.tlauncher.sentry.SentryContext;
import ru.turikhay.tlauncher.ui.alert.Alert;
import ru.turikhay.tlauncher.ui.logger.Logger;
import ru.turikhay.tlauncher.stats.Stats;
import ru.turikhay.tlauncher.user.PlainUser;
import ru.turikhay.util.*;
import ru.turikhay.util.async.AsyncThread;
import ru.turikhay.util.stream.LinkedOutputStringStream;
import ru.turikhay.util.stream.PrintLogger;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MinecraftLauncher implements JavaProcessListener {
    public static final String SENTRY_CONTEXT_NAME = "minecraftLauncher", CAPABLE_WITH = "1.6.84-j";
    private static final int OFFICIAL_VERSION = 21, ALTERNATIVE_VERSION = 11, MIN_WORK_TIME = 5000;
    private SentryContext sentryContext = SentryContext.createWithName(SENTRY_CONTEXT_NAME);
    private boolean working;
    private boolean killed;
    private final Thread parentThread;
    private final Gson gson;
    private final DateTypeAdapter dateAdapter;
    private final Downloader downloader;
    private final Configuration settings;
    private final boolean forceUpdate;
    private final boolean assistLaunch;
    private final VersionManager vm;
    private final AssetsManager am;
    private final ProfileManager pm;
    private final StringBuffer output;
    private final PrintLogger printLogger;
    private Logger logger;
    private final LoggerVisibility loggerVis;
    private CrashManager crashManager;
    private final List<MinecraftListener> listeners;
    private final List<MinecraftExtendedListener> extListeners;
    private final List<MinecraftLauncherAssistant> assistants;
    private MinecraftLauncher.MinecraftLauncherStep step;
    private Account.AccountType librariesForType;
    private String oldMainclass;
    private String versionName;
    private VersionSyncInfo versionSync;
    private CompleteVersion version;
    private CompleteVersion deJureVersion;
    private boolean isLauncher;
    private String accountName;
    private Account account;
    private String cmd;
    private String family;
    private File rootDir;
    private File gameDir;
    private File localAssetsDir;
    private File nativeDir;
    private File globalAssetsDir;
    private File assetsIndexesDir;
    private File assetsObjectsDir;
    private int[] windowSize;
    private boolean fullScreen;
    private boolean fullCommand;
    private int ramSize;
    private OptionsFile optionsFile;
    private JavaProcessLauncher launcher;
    private String javaArgs;
    private String programArgs;
    private boolean minecraftWorking;
    private long startupTime;
    private int exitCode;
    private Server server;
    private List<PromotedServer> promotedServers;
    private List<PromotedServer> outdatedPromotedServers;
    private PromotedServerAddStatus promotedServerAddStatus = PromotedServerAddStatus.NONE;
    private int serverId;
    private static boolean ASSETS_WARNING_SHOWN;
    private JavaProcess process;

    private Rule.FeatureMatcher featureMatcher = createFeatureMatcher();

    private boolean firstLine;
    private int logStart, logEnd;

    public Downloader getDownloader() {
        return downloader;
    }

    public Configuration getConfiguration() {
        return settings;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public boolean isLaunchAssist() {
        return assistLaunch;
    }

    public CharSequence getOutput() {
        return logger != null ? U.requireNotNull(logger.getOutput(), "logger output") : U.requireNotNull(output, "output");
    }

    public CharSequence getLogOutput() {
        CharSequence output = getOutput();
        return logStart > 0 && logEnd > logStart && output.length() > logStart ? output.subSequence(logStart, logEnd) : output;
    }

    public LoggerVisibility getLoggerVisibility() {
        return loggerVis;
    }

    public Logger getLogger() {
        return logger;
    }

    public MinecraftLauncher.MinecraftLauncherStep getStep() {
        return step;
    }

    public boolean isWorking() {
        return working;
    }

    private MinecraftLauncher(ComponentManager manager, Downloader downloader, Configuration configuration, boolean forceUpdate, LoggerVisibility visibility, boolean exit) {
        firstLine = true;
        if (manager == null) {
            throw new NullPointerException("Ti sovsem s duba ruhnul?");
        } else if (downloader == null) {
            throw new NullPointerException("Downloader is NULL!");
        } else if (configuration == null) {
            throw new NullPointerException("Configuration is NULL!");
        } else if (visibility == null) {
            throw new NullPointerException("LoggerVisibility is NULL!");
        } else {
            parentThread = Thread.currentThread();
            gson = new Gson();
            dateAdapter = new DateTypeAdapter();
            this.downloader = downloader;
            settings = configuration;
            assistants = manager.getComponentsOf(MinecraftLauncherAssistant.class);
            vm = manager.getComponent(VersionManager.class);
            am = manager.getComponent(AssetsManager.class);
            pm = manager.getComponent(ProfileManager.class);
            this.forceUpdate = forceUpdate;
            assistLaunch = !exit;
            recordValue("assistLaunch", assistLaunch);
            loggerVis = visibility;
            printLogger = loggerVis.equals(LoggerVisibility.NONE) ? null : new PrintLogger(new LinkedOutputStringStream());
            logger = printLogger == null ? null : new Logger(settings, printLogger, "Minecraft", loggerVis.equals(LoggerVisibility.ALWAYS) && assistLaunch);
            output = logger == null ? new StringBuffer() : null;
            if (logger != null) {
                final Logger l = logger;
                logger.frame.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        l.kill();
                    }
                });
            }

            recordValue("loggerVisibility", loggerVis);
            recordValue("logger", logger);
            recordValue("output", output != null);

            listeners = Collections.synchronizedList(new ArrayList());
            extListeners = Collections.synchronizedList(new ArrayList());
            step = MinecraftLauncher.MinecraftLauncherStep.NONE;
            log("Minecraft Launcher [" + OFFICIAL_VERSION + ";" + ALTERNATIVE_VERSION + "] has initialized");
            log("Running under TLauncher " + TLauncher.getVersion() + " " + TLauncher.getBrand());
            log("Current machine:", OS.getSummary());
        }
    }

    public MinecraftLauncher(TLauncher t, boolean forceUpdate) {
        this(t.getManager(), t.getDownloader(), t.getSettings(), forceUpdate, t.getSettings().getLoggerType().getVisibility(), t.getSettings().getActionOnLaunch() == Configuration.ActionOnLaunch.EXIT);
    }

    public void addListener(MinecraftListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        } else {
            if (listener instanceof MinecraftExtendedListener) {
                extListeners.add((MinecraftExtendedListener) listener);
            }

            listeners.add(listener);
        }
    }

    public void start() {
        checkWorking();
        working = true;

        try {
            collectInfo();
        } catch (Throwable var5) {
            Throwable e = var5;
            log("Error occurred:", var5);
            if (var5 instanceof MinecraftException) {
                MinecraftException listener2 = (MinecraftException) var5;
                Iterator var4 = listeners.iterator();

                while (var4.hasNext()) {
                    MinecraftListener listener3 = (MinecraftListener) var4.next();
                    listener3.onMinecraftKnownError(listener2);
                }
            } else {
                MinecraftListener listener;
                Iterator listener1;
                if (var5 instanceof MinecraftLauncher.MinecraftLauncherAborted) {
                    listener1 = listeners.iterator();

                    while (listener1.hasNext()) {
                        listener = (MinecraftListener) listener1.next();
                        listener.onMinecraftAbort();
                    }
                } else {
                    sentryContext.sendError(null, "unknownError", e, null);

                    listener1 = listeners.iterator();

                    while (listener1.hasNext()) {
                        listener = (MinecraftListener) listener1.next();
                        listener.onMinecraftError(e);
                    }
                }
            }
        }

        working = false;
        step = MinecraftLauncher.MinecraftLauncherStep.NONE;
        log("Launcher stopped.");
    }

    public void stop() {
        if (step == MinecraftLauncher.MinecraftLauncherStep.NONE) {
            throw new IllegalStateException();
        } else {
            if (step == MinecraftLauncher.MinecraftLauncherStep.DOWNLOADING) {
                downloader.stopDownload();
            }

            working = false;
        }
    }

    public String getVersion() {
        return version.getID();
    }

    public void setVersion(String name) {
        checkWorking();
        this.versionName = name;
    }

    public int getExitCode() {
        return exitCode;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server, int id) {
        checkWorking();
        this.server = server;
        this.serverId = id;
    }

    public void setPromotedServers(List<PromotedServer> serverList, List<PromotedServer> outdatedServerList) {
        this.promotedServers = U.shuffle(serverList);
        this.outdatedPromotedServers = outdatedServerList;
    }

    public OptionsFile getOptionsFile() {
        return optionsFile;
    }

    private void collectInfo() throws MinecraftException {
        checkStep(MinecraftLauncher.MinecraftLauncherStep.NONE, MinecraftLauncher.MinecraftLauncherStep.COLLECTING);
        log("Collecting info...");
        Iterator command = listeners.iterator();

        while (command.hasNext()) {
            MinecraftListener type = (MinecraftListener) command.next();
            type.onMinecraftPrepare();
        }

        command = extListeners.iterator();

        while (command.hasNext()) {
            MinecraftExtendedListener type1 = (MinecraftExtendedListener) command.next();
            type1.onMinecraftCollecting();
        }

        log("Force update:", Boolean.valueOf(forceUpdate));
        recordValue("forceUpdate", forceUpdate);

        if (versionName == null) {
            versionName = settings.get("login.version");
        }
        if (versionName == null || versionName.isEmpty()) {
            throw new IllegalArgumentException("Version name is NULL or empty!");
        }

        log("Selected version:", versionName);
        recordValue("versionName", versionName);

        versionSync = vm.getVersionSyncInfo(versionName);
        if (versionSync == null) {
            throw new IllegalArgumentException("Cannot find version " + versionName);
        }

        log("Version sync info:", versionSync);
        recordValue("version", versionSync);

        try {
            deJureVersion = versionSync.resolveCompleteVersion(vm, forceUpdate);
        } catch (IOException var10) {
            throw new RuntimeException("Cannot get complete version!", var10);
        }

        if (deJureVersion == null) {
            throw new NullPointerException("Could not get complete version");
        }
        recordValue("resolvedVersion", deJureVersion);

        if (deJureVersion.getReleaseType() == ReleaseType.LAUNCHER) {
            isLauncher = true;
        }
        recordValue("isLauncher", isLauncher);

        accountName = settings.get("login.account");
        if (accountName != null && !accountName.isEmpty()) {
            Account.AccountType type2 = Reflect.parseEnum(Account.AccountType.class, settings.get("login.account.type"));
            account = pm.getAuthDatabase().getByUsername(accountName, type2);
        }
        if(account == null) {
            if (isLauncher) {
                log("Account is not required, setting user \"launcher\"");
                accountName = "launcher";
                account = new Account(new PlainUser("launcher", new UUID(0L, 0L)));
            } else {
                throw new NullPointerException("account");
            }
        }

        log("Selected account:", account.getUser());
        recordValue("account", account);

        if (!isLauncher) {
            Account.AccountType lookupLibrariesForType;
            switch (account.getType()) {
                case MCLEAKS:
                    if (McleaksManager.isUnsupported()) {
                        throw new MinecraftException(false, "MCLeaks is not supported", "mcleaks-unsupported");
                    } else {
                        lookupLibrariesForType = Account.AccountType.MCLEAKS;
                        oldMainclass = deJureVersion.getMainClass();
                    }
                    break;
                case ELY:
                case ELY_LEGACY:
                    lookupLibrariesForType = Account.AccountType.ELY;
                    break;
                case PLAIN:
                    mayBeEly:
                    {
                        if (!TLauncher.getInstance().getLibraryManager().isAllowElyEverywhere()) {
                            break mayBeEly;
                        }
                        if (!settings.getBoolean("ely.globally")) {
                            break mayBeEly;
                        }
                        lookupLibrariesForType = Account.AccountType.ELY;
                        break;
                    }
                    lookupLibrariesForType = Account.AccountType.PLAIN;
                    break;
                default:
                    lookupLibrariesForType = account.getType();
            }

            log("Looking up libraries for", librariesForType = lookupLibrariesForType);

            TLauncher.getInstance().getLibraryManager().refreshComponent();
            if (TLauncher.getInstance().getLibraryManager().hasLibraries(deJureVersion, librariesForType)) {
                recordValue("librariesFound", lookupLibrariesForType);
                version = TLauncher.getInstance().getLibraryManager().process(deJureVersion, librariesForType);
            } else {
                recordValue("librariesNotFound", librariesForType);
                version = deJureVersion;
            }
        } else {
            version = deJureVersion;
        }

        recordValue("finalVersion", version);

        if (logger != null) {
            logger.setName(version.getID());
        }

        family = version.getFamily();
        if (StringUtils.isEmpty(family))
            family = "unknown";

        String command1 = settings.get("minecraft.cmd");
        cmd = command1 == null ? OS.getJavaPath() : command1;
        log("Command:", cmd);
        recordValue("command", cmd);

        rootDir = new File(settings.get("minecraft.gamedir"));
        recordValue("rootDir", rootDir);

        long freeSpace = rootDir.getUsableSpace();
        if (freeSpace > 0 && freeSpace < 1024L * 64L) {
            throw new MinecraftException(true, "Insufficient space " + rootDir.getAbsolutePath() + "(" + freeSpace + ")", "free-space", rootDir);
        }

        if (settings.getBoolean("minecraft.gamedir.separate")) {
            gameDir = new File(rootDir, "home/" + family);
        } else {
            gameDir = rootDir;
        }
        recordValue("gameDir", gameDir);

        try {
            FileUtil.createFolder(rootDir);
        } catch (Exception var9) {
            throw new MinecraftException(true, "Cannot create working directory!", "folder-not-found", var9);
        }
        if (!isLauncher) {
            try {
                FileUtil.createFolder(gameDir);
            } catch (Exception var9) {
                throw new MinecraftException(true, "Cannot create game directory!", "folder-not-found", var9);
            }
        }

        log("Root directory:", rootDir);
        log("Game directory:", gameDir);

        optionsFile = new OptionsFile(new File(gameDir, "options.txt"));

        if (optionsFile.getFile().isFile()) {
            try {
                optionsFile.read();
            } catch (IOException ioE) {
                recordValue("optionsReadError", ioE);
                log("could not read options file", ioE);
            }
        }

        log("Options:", optionsFile);
        recordValue("options", optionsFile.copy());

        globalAssetsDir = new File(rootDir, "assets");

        if (!isLauncher) {
            try {
                FileUtil.createFolder(globalAssetsDir);
            } catch (IOException var8) {
                throw new RuntimeException("Cannot create assets directory!", var8);
            }
        }

        assetsIndexesDir = new File(globalAssetsDir, "indexes");

        if (!isLauncher) {
            try {
                FileUtil.createFolder(assetsIndexesDir);
            } catch (IOException var7) {
                throw new RuntimeException("Cannot create assets indexes directory!", var7);
            }
        }

        assetsObjectsDir = new File(globalAssetsDir, "objects");

        if (!isLauncher) {
            try {
                FileUtil.createFolder(assetsObjectsDir);
            } catch (IOException var6) {
                throw new RuntimeException("Cannot create assets objects directory!", var6);
            }
        }

        nativeDir = new File(rootDir, "versions/" + version.getID() + "/" + "natives");

        try {
            FileUtil.createFolder(nativeDir);
        } catch (IOException var5) {
            throw new RuntimeException("Cannot create native files directory!", var5);
        }

        javaArgs = settings.get("minecraft.javaargs");
        if (javaArgs != null && javaArgs.isEmpty()) {
            javaArgs = null;
        }
        recordValue("javaArgs", javaArgs);

        programArgs = settings.get("minecraft.args");
        if (programArgs != null && programArgs.isEmpty()) {
            programArgs = null;
        }
        recordValue("appArgs", programArgs);

        windowSize = settings.getClientWindowSize();
        if (windowSize[0] < 1) {
            throw new IllegalArgumentException("Invalid window width!");
        } else if (windowSize[1] < 1) {
            throw new IllegalArgumentException("Invalid window height!");
        } else {
            fullScreen = settings.getBoolean("minecraft.fullscreen");
            recordValue("fullScreen", fullScreen);

            ramSize = settings.getInteger("minecraft.memory");
            if (ramSize < 512) {
                throw new IllegalArgumentException("Invalid RAM size!");
            }
            recordValue("memoryAmount", ramSize);

            fullCommand = settings.getBoolean("gui.logger.fullcommand");
            recordValue("fullCommand", fullCommand);

            Iterator var4 = assistants.iterator();

            while (var4.hasNext()) {
                MinecraftLauncherAssistant assistant = (MinecraftLauncherAssistant) var4.next();
                assistant.collectInfo();
            }

            log("Checking conditions...");
            if (version.getMinimumCustomLauncherVersion() > ALTERNATIVE_VERSION) {
                throw new MinecraftException(false, "Alternative launcher is incompatible with launching version!", "incompatible");
            } else {
                if (version.getMinimumCustomLauncherVersion() == 0 && version.getMinimumLauncherVersion() > OFFICIAL_VERSION) {
                    sentryContext.sendWarning(null, "minimumLauncherVersion",
                            DataBuilder.create("expectedLauncherVersion", version.getMinimumLauncherVersion()).add("currentLauncherVersion", OFFICIAL_VERSION)
                    );
                    Alert.showLocWarning("launcher.warning.title", "launcher.warning.incompatible.launcher", null);
                }

                if (!version.appliesToCurrentEnvironment(featureMatcher)) {
                    Alert.showLocWarning("launcher.warning.title", "launcher.warning.incompatible.environment", null);
                }

                downloadResources();
            }
        }
    }

    public File getRootDir() {
        return rootDir;
    }

    public File getGameDir() {
        return gameDir;
    }

    private void downloadResources() throws MinecraftException {
        checkStep(MinecraftLauncher.MinecraftLauncherStep.COLLECTING, MinecraftLauncher.MinecraftLauncherStep.DOWNLOADING);

        boolean fastCompare;
        if (versionSync.isInstalled()) {
            fastCompare = !forceUpdate;
        } else {
            fastCompare = false;
        }

        Iterator execContainer = extListeners.iterator();
        while (execContainer.hasNext()) {
            MinecraftExtendedListener assets = (MinecraftExtendedListener) execContainer.next();
            assets.onMinecraftComparingAssets(fastCompare);
        }

        final List<AssetIndex.AssetObject> assets1 = compareAssets(fastCompare);
        Iterator listenerContainer = extListeners.iterator();

        while (listenerContainer.hasNext()) {
            MinecraftExtendedListener execContainer1 = (MinecraftExtendedListener) listenerContainer.next();
            execContainer1.onMinecraftDownloading();
        }

        DownloadableContainer versionContainer;
        try {
            versionContainer = vm.downloadVersion(versionSync, librariesForType, forceUpdate);
        } catch (IOException var8) {
            throw new MinecraftException(false, "Cannot download version!", "download-jar", var8);
        }

        checkAborted();

        if (assets1 != null) {
            DownloadableContainer assetsContainer = am.downloadResources(version, assets1);
            assetsContainer.setLogger(logger);
            downloader.add(assetsContainer);
        }

        versionContainer.setLogger(logger);
        downloader.add(versionContainer);

        Iterator message = assistants.iterator();

        while (message.hasNext()) {
            MinecraftLauncherAssistant e = (MinecraftLauncherAssistant) message.next();
            e.collectResources(downloader);
        }

        downloader.startDownloadAndWait();
        if (versionContainer.isAborted()) {
            throw new MinecraftLauncher.MinecraftLauncherAborted(new AbortedDownloadException());
        } else if (!versionContainer.getErrors().isEmpty()) {
            for (Throwable error : versionContainer.getErrors()) {
                if(ConnectionHelper.isCertException(error)) {
                    if(ConnectionHelper.fixCertException(error, "version-download", true) == 1) {
                        // must restart
                        return;
                    }
                    // already restarted, did not fix the problem
                    break;
                }
            }
            boolean e1 = versionContainer.getErrors().size() == 1;
            StringBuilder message1 = new StringBuilder();
            message1.append(versionContainer.getErrors().size()).append(" error").append(e1 ? "" : "s").append(" occurred while trying to download binaries.");
            if (!e1) {
                message1.append(" Cause is the first of them.");
            }

            throw new MinecraftException(false, message1.toString(), "download", versionContainer.getErrors().get(0));
        } else {
            deJureVersion.setUpdatedTime(U.getUTC().getTime());
            try {
                vm.getLocalList().saveVersion(deJureVersion);
            } catch (IOException var7) {
                log("Cannot save version!", var7);
            }
            constructProcess();
        }
    }

    private void constructProcess() throws MinecraftException {
        checkStep(MinecraftLauncher.MinecraftLauncherStep.DOWNLOADING, MinecraftLauncher.MinecraftLauncherStep.CONSTRUCTING);
        Iterator address = extListeners.iterator();

        MinecraftExtendedListener assistant;
        while (address.hasNext()) {
            assistant = (MinecraftExtendedListener) address.next();
            assistant.onMinecraftReconstructingAssets();
        }

        try {
            localAssetsDir = reconstructAssets();
        } catch (IOException var8) {
            throw new MinecraftException(false, "Cannot reconstruct assets!", "reconstruct-assets", var8);
        }

        address = extListeners.iterator();

        while (address.hasNext()) {
            assistant = (MinecraftExtendedListener) address.next();
            assistant.onMinecraftUnpackingNatives();
        }

        try {
            unpackNatives(forceUpdate);
        } catch (IOException var7) {
            throw new MinecraftException(false, "Cannot unpack natives!", "unpack-natives", var7);
        }

        checkAborted();
        address = extListeners.iterator();

        while (address.hasNext()) {
            assistant = (MinecraftExtendedListener) address.next();
            assistant.onMinecraftDeletingEntries();
        }

        try {
            deleteEntries();
        } catch (IOException var6) {
            throw new MinecraftException(false, "Cannot delete entries!", "delete-entries", var6);
        }

        try {
            deleteLibraryEntries();
        } catch (Exception var5) {
            throw new MinecraftException(false, "Cannot delete library entries!", "delete-entries", var5);
        }

        checkAborted();
        log("Constructing process...");
        address = extListeners.iterator();

        while (address.hasNext()) {
            assistant = (MinecraftExtendedListener) address.next();
            assistant.onMinecraftConstructing();
        }

        ArrayList<String> jvmArgs = new ArrayList<>(), programArgs = new ArrayList<>();
        createJvmArgs(jvmArgs);

        if (this.programArgs != null) {
            programArgs.addAll(Arrays.asList(StringUtils.split(this.programArgs, ' ')));
        }

        launcher = new JavaProcessLauncher(cmd, new String[0]);
        launcher.directory(isLauncher ? rootDir : gameDir);

        try {
            fixResourceFolder();
        } catch (Exception ioE) {
            log("Cannot check resource folder. This could have been fixed [MCL-3732].", ioE);
        }


        if (!isLauncher) {
            Set<NBTServer> exisingServerList = null, nbtServerList = new LinkedHashSet<>();
            try {
                File file = new File(gameDir, "servers.dat");
                if (file.isFile()) {
                    try {
                        FileUtil.copyFile(file, new File(file.getAbsolutePath() + ".bak"), true);
                    } catch (IOException ioE) {
                        log("Could not make backup for servers.dat", ioE);
                    }
                    try {
                        exisingServerList = NBTServer.loadSet(file);
                    } catch (Exception e) {
                        log("Could not read servers.dat", e);
                        log("We'll have to overwrite it as it can't be read by Minecraft neither");
                        exisingServerList = new LinkedHashSet<>();
                    }
                } else {
                    FileUtil.createFile(file);
                    exisingServerList = new LinkedHashSet<>();
                }
                if (server != null) {
                    nbtServerList.add(new NBTServer(server));
                }
                if (outdatedPromotedServers != null) {
                    Iterator<NBTServer> i = exisingServerList.iterator();
                    while (i.hasNext()) {
                        NBTServer existingServer = i.next();
                        for (PromotedServer outdatedServer : outdatedPromotedServers) {
                            if (existingServer.equals(outdatedServer) && existingServer.getName().equals(outdatedServer.getName())) {
                                log("Removed outdated server:", existingServer, ", compared with", outdatedServer);
                                i.remove();
                                break;
                            }
                        }
                    }
                }
                if (settings.getBoolean("minecraft.servers.promoted.ingame")) {
                    if (promotedServers != null) {
                        for (final PromotedServer promotedServer : promotedServers) {
                            if (!promotedServer.getFamily().isEmpty() && !promotedServer.getFamily().contains(family)) {
                                continue;
                            }
                            if (promotedServer.equals(server)) {
                                continue;
                            }
                            NBTServer existingServer = null;
                            for (NBTServer nbtServer : exisingServerList) {
                                if (promotedServer.equals(nbtServer)) {
                                    existingServer = nbtServer;
                                    break;
                                }
                            }
                            if (existingServer != null) {
                                nbtServerList.add(existingServer);
                                exisingServerList.remove(existingServer);
                            } else {
                                nbtServerList.add(new NBTServer(promotedServer));
                            }
                        }
                    } else {
                        promotedServerAddStatus = PromotedServerAddStatus.EMPTY;
                    }
                } else {
                    promotedServerAddStatus = PromotedServerAddStatus.DISABLED;
                }

                nbtServerList.addAll(exisingServerList);
                FileUtil.copyFile(file, new File(gameDir, "servers.dat.bak"), true);
                NBTServer.saveSet(nbtServerList, file);
                if (promotedServerAddStatus == PromotedServerAddStatus.NONE) {
                    promotedServerAddStatus = PromotedServerAddStatus.SUCCESS;
                }
            } catch (Exception e) {
                Sentry.sendError(MinecraftLauncher.class, "couldn't reconstruct server list", e, DataBuilder.create("existing", exisingServerList).add("new", nbtServerList).add("status", promotedServerAddStatus));
                log("Couldn't reconstruct server list", e);
                promotedServerAddStatus = PromotedServerAddStatus.ERROR;
            }
        }

        if (!isLauncher) {
            try {
                fixForNewerVersions();
            } catch (Exception e) {
                log("Could not make it compatible with older versions", e);
            }
        }

        /*launcher.addCommand("-Djava.library.path=" + nativeDir.getAbsolutePath());

        if (OS.WINDOWS.isCurrent() && OS.VERSION.startsWith("10.")) {
            launcher.addCommand("-Dos.name=Windows 10");
            launcher.addCommand("-Dos.version=10.0");
        }

        launcher.addCommand("-cp", constructClassPath(version));
        launcher.addCommand("-Dfml.ignoreInvalidMinecraftCertificates=true");
        launcher.addCommand("-Dfml.ignorePatchDiscrepancies=true");
        launcher.addCommand("-Djava.net.useSystemProxies=true");

        if (!OS.WINDOWS.isCurrent() || StringUtils.isAsciiPrintable(nativeDir.getAbsolutePath())) {
            launcher.addCommand("-Dfile.encoding=UTF-8");
        }

        launcher.addCommands(getJVMArguments());
        if (javaArgs != null) {
            launcher.addSplitCommands(javaArgs);
        }

        address = assistants.iterator();

        MinecraftLauncherAssistant assistant2;
        while (address.hasNext()) {
            assistant2 = (MinecraftLauncherAssistant) address.next();
            assistant2.constructJavaArguments();
        }


        if (!fullCommand) {
            log("Half command (characters are not escaped):\n" + launcher.getCommandsAsString());
        }

        launcher.addCommands(getMinecraftArguments());
        launcher.addCommand("--width", Integer.valueOf(windowSize[0]));
        launcher.addCommand("--height", Integer.valueOf(windowSize[1]));
        if (fullScreen) {
            launcher.addCommand("--fullscreen");
        }

        try {
            File serversDat = new File(gameDir, "servers.dat");

            if (serversDat.isFile())
                FileUtil.copyFile(serversDat, new File(serversDat.getAbsolutePath() + ".bak"), true);

        } catch (IOException ioE) {
            log("Could not make backup for servers.dat", ioE);
        }

        try {
            fixResourceFolder();
        } catch (Exception ioE) {
            log("Cannot check resource folder. This could have been fixed [MCL-3732].", ioE);
        }


        Set<NBTServer> exisingServerList = null, nbtServerList = new LinkedHashSet<>();
        try {
            File file = new File(gameDir, "servers.dat");
            if(file.isFile()) {
                exisingServerList = NBTServer.loadSet(file);
            } else {
                FileUtil.createFile(file);
                exisingServerList = new LinkedHashSet<>();
            }
            if(server != null) {
                nbtServerList.add(new NBTServer(server));
            }
            if (outdatedPromotedServers != null) {
                Iterator<NBTServer> i = exisingServerList.iterator();
                while (i.hasNext()) {
                    NBTServer existingServer = i.next();
                    for(PromotedServer outdatedServer : outdatedPromotedServers) {
                        if(existingServer.equals(outdatedServer) && existingServer.getName().equals(outdatedServer.getName())) {
                            log("Removed outdated server:", existingServer, ", compared with", outdatedServer);
                            i.remove();
                            break;
                        }
                    }
                }
            }
            if(settings.getBoolean("minecraft.servers.promoted.ingame")) {
                if (promotedServers != null) {
                    for (final PromotedServer promotedServer : promotedServers) {
                        if (!promotedServer.getFamily().isEmpty() && !promotedServer.getFamily().contains(family)) {
                            continue;
                        }
                        if(promotedServer.equals(server)) {
                            continue;
                        }
                        NBTServer existingServer = null;
                        for (NBTServer nbtServer : exisingServerList) {
                            if (promotedServer.equals(nbtServer)) {
                                existingServer = nbtServer;
                                break;
                            }
                        }
                        if (existingServer != null) {
                            nbtServerList.add(existingServer);
                            exisingServerList.remove(existingServer);
                        } else {
                            nbtServerList.add(new NBTServer(promotedServer));
                        }
                    }
                } else {
                    promotedServerAddStatus = PromotedServerAddStatus.EMPTY;
                }
            } else {
                promotedServerAddStatus = PromotedServerAddStatus.DISABLED;
            }

            nbtServerList.addAll(exisingServerList);
            if(!nbtServerList.isEmpty()) {
                FileUtil.copyFile(file, new File(gameDir, "servers.dat.bak"), true);
                NBTServer.saveSet(nbtServerList, file);
                if(promotedServerAddStatus == PromotedServerAddStatus.NONE) {
                    promotedServerAddStatus = PromotedServerAddStatus.SUCCESS;
                }
            }
        } catch (Exception e) {
            Sentry.sendError(MinecraftLauncher.class, "couldn't reconstruct server list", e, DataBuilder.create("existing", exisingServerList).add("new", nbtServerList).add("status", promotedServerAddStatus));
            log("Couldn't reconstruct server list", e);
            promotedServerAddStatus = PromotedServerAddStatus.ERROR;
        }

        if (server != null) {
            launcher.addCommand("--server", server.getAddress());
            launcher.addCommand("--port", server.getPort());
        }

        if (programArgs != null) {
            launcher.addSplitCommands(programArgs);
        }

        address = assistants.iterator();

        while (address.hasNext()) {
            assistant2 = (MinecraftLauncherAssistant) address.next();
            assistant2.constructProgramArguments();
        }*/

        StrSubstitutor argumentsSubstitutor = createArgumentsSubstitutor();
        jvmArgs.addAll(version.addArguments(ArgumentType.JVM, featureMatcher, argumentsSubstitutor));
        programArgs.addAll(version.addArguments(ArgumentType.GAME, featureMatcher, argumentsSubstitutor));

        if (!isLauncher && server != null) {
            programArgs.addAll(Arrays.asList("--server", server.getAddress()));
            programArgs.addAll(Arrays.asList("--port", String.valueOf(server.getPort())));
        }

        String modListFile = null;
        try {
            modListFile = generateModListFile();
        } catch (IOException ioE) {
            log("Cannot generate mod list file", ioE);
        }

        if (modListFile != null) {
            programArgs.add("--modListFile");
            programArgs.add(modListFile);
        }

        for (String arg : jvmArgs) {
            launcher.addCommand(arg);
        }

        launcher.addCommand(version.getMainClass());

        if (!fullCommand) {
            List<String> l = new ArrayList<>(launcher.getCommands());
            l.addAll(programArgs);
            log("Half command (characters are not escaped):\n" + launcher.getJvmPath() + ' ' + joinList(l, ARGS_CENSORED, BLACKLIST_MODE_CENSOR));
        }

        for (String arg : programArgs) {
            launcher.addCommand(arg);
        }

        if (fullCommand) {
            log("Full command (characters are not escaped):\n" + launcher.getCommandsAsString());
        }

        /*    CompatibilityRule.FeatureMatcher featureMatcher = createFeatureMatcher();
    StrSubstitutor argumentsSubstitutor = createArgumentsSubstitutor(getVersion(), this.selectedProfile, gameDirectory, assetsDir, this.auth);

    getVersion().addArguments(net.minecraft.launcher.updater.ArgumentType.JVM, featureMatcher, processBuilder, argumentsSubstitutor);
    processBuilder.withArguments(new String[] { getVersion().getMainClass() });

    LOGGER.info("Half command: " + org.apache.commons.lang3.StringUtils.join(processBuilder.getFullCommands(), " "));

    getVersion().addArguments(net.minecraft.launcher.updater.ArgumentType.GAME, featureMatcher, processBuilder, argumentsSubstitutor);

    Proxy proxy = getLauncher().getProxy();
    PasswordAuthentication proxyAuth = getLauncher().getProxyAuth();
    if (!proxy.equals(Proxy.NO_PROXY)) {
      InetSocketAddress address = (InetSocketAddress)proxy.address();
      processBuilder.withArguments(new String[] { "--proxyHost", address.getHostName() });
      processBuilder.withArguments(new String[] { "--proxyPort", Integer.toString(address.getPort()) });
      if (proxyAuth != null) {
        processBuilder.withArguments(new String[] { "--proxyUser", proxyAuth.getUserName() });
        processBuilder.withArguments(new String[] { "--proxyPass", new String(proxyAuth.getPassword()) });
      }
    }

    processBuilder.withArguments(this.additionalLaunchArgs);
    try
    {
      LOGGER.debug("Running " + org.apache.commons.lang3.StringUtils.join(processBuilder.getFullCommands(), " "));
      GameProcess process = this.processFactory.startGame(processBuilder);
      process.setExitRunnable(this);

      setStatus(GameInstanceStatus.PLAYING);
      if (this.visibilityRule != LauncherVisibilityRule.DO_NOTHING) {
        this.minecraftLauncher.getUserInterface().setVisible(false);
      }
    } catch (IOException e) {
      LOGGER.error("Couldn't launch game", e);
      setStatus(GameInstanceStatus.IDLE);
      return;
    }
*/

        launchMinecraft();
    }

    private void readFirstBytes(File file) throws IOException {
        FileInputStream in = null;
        byte[] buffer = new byte[256];

        try {
            IOUtils.read(in = new FileInputStream(file), buffer);
        } finally {
            U.close(in);
        }

        log("First bytes of", file);
        log(new String(buffer, "UTF-8"));
    }

    private File reconstructAssets() throws IOException, MinecraftException {
        String assetVersion = version.getAssetIndex().getId();
        File indexFile = new File(assetsIndexesDir, assetVersion + ".json");
        File virtualRoot = new File(new File(globalAssetsDir, "virtual"), assetVersion);
        if (!indexFile.isFile()) {
            log("No assets index file " + virtualRoot + "; can\'t reconstruct assets");
            return virtualRoot;
        } else {
            AssetIndex index;
            try {
                index = (AssetIndex) gson.fromJson(new FileReader(indexFile), (Class) AssetIndex.class);
            } catch (Exception var9) {
                throw new MinecraftException(false, "Cannot read index file!", "index-file", var9);
            }

            if (index.isMapToResources()) {
                virtualRoot = new File(gameDir, "resources");
            }

            if (index.isVirtual() || index.isMapToResources()) {
                log("Reconstructing virtual assets folder at " + virtualRoot);
                Iterator var6 = index.getFileMap().entrySet().iterator();

                while (true) {
                    while (var6.hasNext()) {
                        checkAborted();

                        Entry entry = (Entry) var6.next();
                        File target = new File(virtualRoot, (String) entry.getKey());
                        File original = new File(new File(assetsObjectsDir, ((AssetIndex.AssetObject) entry.getValue()).getHash().substring(0, 2)), ((AssetIndex.AssetObject) entry.getValue()).getHash());
                        if (!original.isFile()) {
                            log("Skipped reconstructing:", original);
                        } else if (forceUpdate || !target.isFile()) {
                            FileUtils.copyFile(original, target, false);
                            log(original, "->", target);
                        }
                    }

                    FileUtil.writeFile(new File(virtualRoot, ".lastused"), dateAdapter.toString(new Date()));
                    break;
                }
            }

            return virtualRoot;
        }
    }

    private File getAssetObject(String name) throws IOException {
        String assetVersion = version.getAssetIndex().getId();
        File indexFile = new File(assetsIndexesDir, assetVersion + ".json");
        AssetIndex index = gson.fromJson(FileUtil.readFile(indexFile), AssetIndex.class);
        if (index.getFileMap() == null) {
            throw new IOException("Cannot get filemap!");
        } else {
            String hash = index.getFileMap().get(name).getHash();
            return new File(assetsObjectsDir, hash.substring(0, 2) + "/" + hash);
        }
    }

    private void unpackNatives(boolean force) throws IOException {
        log("Unpacking natives...");
        Collection libraries = version.getRelevantLibraries(featureMatcher);
        OS os = OS.CURRENT;
        ZipFile zip = null;
        if (force) {
            nativeDir.delete();
        }

        Iterator var7 = libraries.iterator();

        label79:
        while (true) {
            Library library;
            Map nativesPerOs;
            do {
                do {
                    if (!var7.hasNext()) {
                        return;
                    }

                    library = (Library) var7.next();
                    nativesPerOs = library.getNatives();
                } while (nativesPerOs == null);
            } while (nativesPerOs.get(os) == null);

            File file = new File(MinecraftUtil.getWorkingDirectory(), "libraries/" + library.getArtifactPath((String) nativesPerOs.get(os)));
            if (!file.isFile()) {
                throw new IOException("Required archive doesn\'t exist: " + file.getAbsolutePath());
            }

            try {
                zip = new ZipFile(file);
            } catch (IOException var18) {
                throw new IOException("Error opening ZIP archive: " + file.getAbsolutePath(), var18);
            }

            ExtractRules extractRules = library.getExtractRules();
            Enumeration entries = zip.entries();

            while (true) {
                ZipEntry entry;
                File targetFile;
                do {
                    do {
                        do {
                            if (!entries.hasMoreElements()) {
                                zip.close();
                                continue label79;
                            }

                            entry = (ZipEntry) entries.nextElement();
                        } while (extractRules != null && !extractRules.shouldExtract(entry.getName()));

                        targetFile = new File(nativeDir, entry.getName());
                    } while (!force && targetFile.isFile());

                    if (targetFile.getParentFile() != null) {
                        targetFile.getParentFile().mkdirs();
                    }
                } while (entry.isDirectory());

                BufferedInputStream inputStream = new BufferedInputStream(zip.getInputStream(entry));
                byte[] buffer = new byte[2048];
                FileOutputStream outputStream = new FileOutputStream(targetFile);
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);

                int length;
                while ((length = inputStream.read(buffer, 0, buffer.length)) != -1) {
                    bufferedOutputStream.write(buffer, 0, length);
                }

                inputStream.close();
                bufferedOutputStream.close();
            }
        }
    }

    private void deleteEntries() throws IOException {
        List entries = version.getDeleteEntries();
        if (entries != null && entries.size() != 0) {
            log("Removing entries...");
            File file = version.getFile(rootDir);
            removeFrom(file, entries);
        }
    }

    private void deleteLibraryEntries() throws IOException {
        Iterator var2 = version.getLibraries().iterator();

        while (var2.hasNext()) {
            Library lib = (Library) var2.next();
            List entries = lib.getDeleteEntriesList();
            if (entries != null && !entries.isEmpty()) {
                log("Processing entries of", lib.getName());
                removeFrom(new File(rootDir, "libraries/" + lib.getArtifactPath()), entries);
            }
        }

    }

    private String constructClassPath(CompleteVersion version) throws MinecraftException {
        log("Constructing classpath...");
        StringBuilder result = new StringBuilder();
        Collection classPath = version.getClassPath(OS.CURRENT, featureMatcher, rootDir);
        String separator = System.getProperty("path.separator");

        File file;
        for (Iterator var6 = classPath.iterator(); var6.hasNext(); result.append(file.getAbsolutePath())) {
            file = (File) var6.next();
            if (!file.isFile()) {
                throw new MinecraftException(true, "Classpath is not found: " + file, "classpath", file);
            }

            if (result.length() > 0) {
                result.append(separator);
            }
        }

        return result.toString();
    }

    private void fixForNewerVersions() throws MinecraftException {
        boolean needSave = false;
        if (version.getMinecraftArguments() != null && version.hasModernArguments()) {
            deJureVersion.setMinecraftArguments(null);
            needSave = true;
        }
        if (needSave) {
            try {
                vm.getLocalList().saveVersion(deJureVersion);
            } catch (IOException var7) {
                log("Cannot save legacy arguments!", var7);
            }
        }
    }

    private String makeLegacyArgumentString(ArgumentType type) {
        List<String> argList = version.addArguments(type, featureMatcher, null);
        return joinList(argList, ARGS_LEGACY_REMOVED, BLACKLIST_MODE_REMOVE);
    }

    private void removeOldModlistFiles() {
        File[] fileList = gameDir.listFiles();
        if (fileList == null) {
            log("Cannot get file list in", rootDir);
            return;
        }
        for (File file : fileList) {
            if (file.getName().startsWith("tempModList-")) {
                FileUtil.deleteFile(file);
            }
        }
    }

    private String generateModListFile() throws IOException {
        removeOldModlistFiles();

        Collection<Library> collectedLibMods = version.collectMods(featureMatcher);
        if (collectedLibMods == null || collectedLibMods.isEmpty()) {
            return null;
        }

        ModList modList = new ModList(new File(rootDir, "libraries"), version.isModListAbsolutePrefix());
        for (Library collectedLibMod : collectedLibMods) {
            modList.addMod(collectedLibMod);
        }

        String modListFilename = "tempModList-" + System.currentTimeMillis();
        File modListFile = new File(gameDir, modListFilename);
        modList.save(modListFile);

        return modListFilename;
    }

    private static final String[] ARGS_LEGACY_REMOVED = new String[]{
            "--width", "${resolution_width}", "--height", "${resolution_height}"
    };

    private static final String[] ARGS_CENSORED = new String[]{
            "--accessToken"
    };

    private static final String[] CENSORED = new String[]{
            "not for you", "censored", "nothinginteresting", "boiiiiiiiiii",
            "Minecraft is a lie", "vk.cc/7iPiB9"
    };

    private static final int BLACKLIST_MODE_REMOVE = 0, BLACKLIST_MODE_CENSOR = 1;

    private String joinList(List<String> l, String[] blackList, int blacklistMode) {
        StringBuilder b = new StringBuilder();
        Iterator<String> i = l.iterator();
        while (i.hasNext()) {
            String arg = i.next();
            if (U.find(arg, blackList) == -1) {
                b.append(' ').append(arg);
            } else {
                if (blacklistMode == BLACKLIST_MODE_CENSOR) {
                    b.append(' ').append(arg).append(" [").append(U.getRandom(CENSORED)).append("]");
                    if (i.hasNext()) {
                        i.next(); // skip
                    }
                }
            }
        }
        if (b.length() > 1) {
            return b.substring(1);
        }
        return null;
    }

    /*private String[] getMinecraftArguments() throws MinecraftException {
        log("Getting Minecraft arguments...");
        if (version.getMinecraftArguments() == null) {
            throw new MinecraftException(true, "Can\'t run version, missing minecraftArguments", "noArgs");
        } else {
            HashMap map = new HashMap();
            StrSubstitutor substitutor = new StrSubstitutor(map);
            String assets = version.getAssetIndex().getId();
            String[] split = version.getMinecraftArguments().split(" ");
            map.putAll(account.getUser().getLoginCredentials().map());
            /*map.put("auth_username", accountName);
            if (!account.isFree()) {
                map.put("auth_session", String.format("token:%s:%s", account.getAccessToken(), account.getProfile().getId()));
                map.put("auth_access_token", account.getAccessToken());
                map.put("user_properties", gson.toJson(account.getProperties()));
                map.put("auth_player_name", account.getDisplayName());
                map.put("auth_uuid", account.getUUID());
                map.put("user_type", "mojang");
                map.put("profile_name", account.getProfile().getName());
            } else {
                map.put("auth_session", "null");
                map.put("auth_access_token", "null");
                map.put("user_properties", "[]");
                map.put("auth_player_name", accountName);
                map.put("auth_uuid", (new UUID(0L, 0L)).toString());
                map.put("user_type", "legacy");
                map.put("profile_name", "(Default)");
            }**

            map.put("version_name", version.getID());
            map.put("version_type", version.getReleaseType());
            map.put("game_directory", gameDir.getAbsolutePath());
            map.put("game_assets", localAssetsDir.getAbsolutePath());
            map.put("assets_root", globalAssetsDir.getAbsolutePath());
            map.put("assets_index_name", assets == null ? "legacy" : assets);

            for (int i = 0; i < split.length; ++i) {
                split[i] = substitutor.replace(split[i]);
            }

            return split;
        }
    }*/

    private void createJvmArgs(List<String> args) {
        if (javaArgs != null) {
            args.addAll(Arrays.asList(StringUtils.split(javaArgs, ' ')));
        }
        if (settings.getBoolean("minecraft.improvedargs")) {
            if (OS.JAVA_VERSION.getMajor() >= 9 || ramSize >= 3072) {
                args.add("-XX:+UnlockExperimentalVMOptions"); // to unlock G1NewSizePercent
                args.add("-XX:+UseG1GC");
                args.add("-XX:G1NewSizePercent=20"); // from Mojang launcher
                args.add("-XX:G1ReservePercent=20"); // from Mojang launcher
                args.add("-XX:MaxGCPauseMillis=50"); // from Mojang launcher
                args.add("-XX:G1HeapRegionSize=32M"); // from Mojang launcher
                args.add("-XX:ConcGCThreads=" + ((OS.Arch.AVAILABLE_PROCESSORS / 4 > 0) ? OS.Arch.AVAILABLE_PROCESSORS / 4 : 1));
                args.add("-XX:ParallelGCThreads=" + OS.Arch.AVAILABLE_PROCESSORS);
            } else {
                args.add("-XX:+UseConcMarkSweepGC");
                args.add("-XX:-UseAdaptiveSizePolicy");
                args.add("-XX:+CMSParallelRemarkEnabled");
                args.add("-XX:+ParallelRefProcEnabled");
                args.add("-XX:+CMSClassUnloadingEnabled");
                args.add("-XX:+UseCMSInitiatingOccupancyOnly");
            }
        }
        args.add("-Xmx" + ramSize + "M");
        if (librariesForType == Account.AccountType.MCLEAKS) {
            args.add("-Dru.turikhay.mcleaks.nstweaker.hostname=true");
            args.add("-Dru.turikhay.mcleaks.nstweaker.hostname.list=" + NSTweaker.toTweakHostnameList(McleaksManager.getConnector().getList()));
            args.add("-Dru.turikhay.mcleaks.nstweaker.ssl=true");
            args.add("-Dru.turikhay.mcleaks.nstweaker.mainclass=" + oldMainclass);
        }
        if (!OS.WINDOWS.isCurrent() || StringUtils.isAsciiPrintable(nativeDir.getAbsolutePath())) {
            args.add("-Dfile.encoding=UTF-8");
        }
    }

    private AssetsManager.ResourceChecker resourceChecker;

    private List<AssetIndex.AssetObject> compareAssets(boolean fastCompare) throws MinecraftException {
        if (version.getAssetIndex() != null && "none".equals(version.getAssetIndex().getId())) {
            log("Assets comparing skipped");
            return null;
        }

        log("Comparing assets...");

        AssetsManager.ResourceChecker checker = null;
        try {
            checker = am.checkResources(version, fastCompare);
        } catch (AssetsNotFoundException e) {
            sentryContext.sendWarning(null, "assetsNotFound", DataBuilder.create("fastCompare", fastCompare)
                    .add("assetIndex", version.getAssetIndex())
            );
        }

        try {
            resourceChecker = checker;
            boolean showTimerWarning = true;
            AssetIndex.AssetObject lastObject = null;
            int timer = 0;

            while (working && checker != null && checker.checkWorking()) {
                final AssetIndex.AssetObject object = checker.getCurrent();
                if (object != null) {
                    log("Instant state on:", object);
                    if (showTimerWarning && object == lastObject) {
                        if (++timer == 10) {
                            log("Tooooo slooooooow. Warning has been shown.");
                            AsyncThread.execute(new Runnable() {
                                @Override
                                public void run() {
                                    Alert.showLocWarning("launcher.warning.assets.long");
                                }
                            });
                            showTimerWarning = false;
                        }
                    } else {
                        timer = 0;
                    }
                    U.sleepFor(1000);
                }
                lastObject = object;
            }
        } catch (InterruptedException inE) {
            throw new MinecraftLauncherAborted(inE);
        }

        checkAborted();

        List<AssetIndex.AssetObject> result = checker.getAssetList();
        if (result == null) {
            log("Could not check assets", checker.getError());
            return Collections.EMPTY_LIST;
        }

        log("Compared assets in", checker.getDelta(), "ms");
        return result;
    }

    private void fixResourceFolder() throws Exception {
        if (isLauncher) {
            return;
        }
        File serverResourcePacksFolder = new File(gameDir, "server-resource-packs");
        if (serverResourcePacksFolder.isDirectory()) {
            File[] files = U.requireNotNull(serverResourcePacksFolder.listFiles(), "files of " + serverResourcePacksFolder.getAbsolutePath());
            for (File file : files) {
                if (file.length() == 0) {
                    FileUtil.deleteFile(file);
                }
            }
        }
        FileUtil.createFolder(serverResourcePacksFolder);
    }

    private void launchMinecraft() throws MinecraftException {
        checkStep(MinecraftLauncher.MinecraftLauncherStep.CONSTRUCTING, MinecraftLauncher.MinecraftLauncherStep.LAUNCHING);
        log("Launching Minecraft...");
        Iterator var2 = listeners.iterator();

        while (var2.hasNext()) {
            MinecraftListener e = (MinecraftListener) var2.next();
            e.onMinecraftLaunch();
        }

        if (version.getReleaseType() != null)
            switch (version.getReleaseType()) {
                case RELEASE:
                case SNAPSHOT:
                    log("Starting Minecraft", version.getID());
                    break;
                default:
                    log("Starting", version.getID());
            }
        log("Launching in:", gameDir.getAbsolutePath());
        startupTime = System.currentTimeMillis();
        recordValue("startupTime", startupTime);
        TLauncher.getInstance().getLogger().setLauncher(this);
        if (logger != null) {
            Calendar e1 = Calendar.getInstance();
            e1.setTimeInMillis(startupTime);
            logger.setName(version.getID() + " (" + new SimpleDateFormat("yyyy-MM-dd").format(e1.getTime()) + ")");
            logger.setLauncher(this);
        }

        try {
            ProcessBuilder b = launcher.createProcess();
            b.environment().put("_JAVA_OPTIONS", "");
            process = new JavaProcess(b.start());
            process.getMonitor().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    boolean sendError = true;

                    if (e instanceof OutOfMemoryError) {
                        int size = -1;
                        if (output != null) {
                            size = output.length();
                            output.setLength(0);
                        }
                        if (logger != null) {
                            size = logger.getOutput().length();
                            logger.clear();
                        }

                        sentryContext.sendWarning(ProcessMonitorThread.class, "oom", DataBuilder.create("size", size));
                        sendError = false;
                    }

                    ExceptionHandler.getInstance().uncaughtException(t, e);

                    if (sendError) {
                        sentryContext.sendError(ProcessMonitorThread.class, "monitorError", e, null);
                    }
                }
            });
            process.safeSetExitRunnable(this);
            minecraftWorking = true;
        } catch (Exception var3) {
            notifyClose();
            if (var3.getMessage().contains("CreateProcess error=2,")) {
                throw new MinecraftException(false, "Executable is not found: \"" + var3.getMessage() + "\"", "exec-not-found");
            }
            throw new MinecraftException(true, "Cannot start the game!", "start", var3);
        }

        postLaunch();
    }

    private void postLaunch() {
        checkStep(MinecraftLauncher.MinecraftLauncherStep.LAUNCHING, MinecraftLauncher.MinecraftLauncherStep.POSTLAUNCH);
        log("Processing post-launch actions. Assist launch:", Boolean.valueOf(assistLaunch));

        Iterator var2 = extListeners.iterator();
        while (var2.hasNext()) {
            MinecraftExtendedListener listener = (MinecraftExtendedListener) var2.next();
            listener.onMinecraftPostLaunch();
        }

        Stats.minecraftLaunched(account, version, server, serverId, promotedServerAddStatus);
        if (assistLaunch) {
            waitForClose();
        } else {
            U.sleepFor(30000L);
            if (minecraftWorking) {
                TLauncher.kill();
            }
        }

    }

    public void killProcess() {
        if (!minecraftWorking) {
            throw new IllegalStateException();
        } else {
            log("Killing Minecraft forcefully");
            killed = true;
            process.stop();
        }
    }

    public void plog(Object... o) {
        String text = U.toLog(o);
        if (logger == null) {
            if (output != null) {
                StringBuffer var3 = output;
                synchronized (output) {
                    output.append(text).append('\n');
                }
            }
        } else {
            logger.log(text);
        }

    }

    public void log(Object... o) {
        U.log("[L]", o);
        plog("[L]", o);
    }

    private void checkThread() {
        if (!Thread.currentThread().equals(parentThread)) {
            throw new IllegalStateException("Illegal thread!");
        }
    }

    private void checkStep(MinecraftLauncher.MinecraftLauncherStep prevStep, MinecraftLauncher.MinecraftLauncherStep currentStep) {
        checkAborted();
        if (prevStep != null && currentStep != null) {
            if (!step.equals(prevStep)) {
                throw new IllegalStateException("Called from illegal step: " + step);
            } else {
                checkThread();
                step = currentStep;
                SentryBreadcrumb.info(null, "step:" + currentStep).push(sentryContext);
            }
        } else {
            throw new NullPointerException("NULL: " + prevStep + " " + currentStep);
        }
    }

    private void checkAborted() {
        if (!working) {
            throw new MinecraftLauncher.MinecraftLauncherAborted("Aborted at step: " + step);
        }
    }

    private void checkWorking() {
        if (working) {
            throw new IllegalStateException("Launcher is working!");
        }
    }

    public void onJavaProcessLog(JavaProcess jp, String line) {
        if (firstLine) {
            firstLine = false;
            U.plog("===============================================================================================");
            plog("===============================================================================================");
            logStart = getOutput().length();
        }

        U.plog(">", line);
        plog(line);
    }

    public void onJavaProcessEnded(JavaProcess jp) {
        logEnd = getOutput().length() - 1;
        notifyClose();

        if (TLauncher.getInstance().getLogger().getLauncher() == this) {
            TLauncher.getInstance().getLogger().setLauncher(null);
        }

        if (logger != null) {
            logger.setLauncher(null);
        }

        int exit = jp.getExitCode();

        log("Minecraft closed with exit code: " + exit + " (0x" + Integer.toHexString(exit) + ")");
        exitCode = exit;


        if (settings.getBoolean("minecraft.crash") && !killed && (System.currentTimeMillis() - startupTime < MIN_WORK_TIME || exit != 0)) {
            crashManager = new CrashManager(this);

            for (MinecraftListener listener : listeners) {
                listener.onCrashManagerInit(crashManager);
            }

            crashManager.startAndJoin();

            if (crashManager.getCrash().getEntry() == null || !crashManager.getCrash().getEntry().isFake()) {
                return;
            }
        }

        if (!assistLaunch) {
            TLauncher.kill();
        }

        if (logger != null) {
            logger.killIn(7000L);
        }
    }

    public void onJavaProcessError(JavaProcess jp, Throwable e) {
        notifyClose();
        Iterator var4 = listeners.iterator();

        while (var4.hasNext()) {
            MinecraftListener listener = (MinecraftListener) var4.next();
            listener.onMinecraftError(e);
        }

    }

    private synchronized void waitForClose() {
        while (minecraftWorking) {
            try {
                wait();
            } catch (InterruptedException var2) {
            }
        }

    }

    private synchronized void notifyClose() {
        minecraftWorking = false;

        if (System.currentTimeMillis() - startupTime < 5000L) {
            U.sleepFor(1000L);
        }

        notifyAll();
        Iterator var2 = listeners.iterator();

        while (var2.hasNext()) {
            MinecraftListener listener = (MinecraftListener) var2.next();
            listener.onMinecraftClose();
        }

    }

    private void removeFrom(File zipFile, List<String> entries) throws IOException {
        File tempFile = new File(zipFile.getAbsolutePath() + "." + System.currentTimeMillis());
        tempFile.delete();
        tempFile.deleteOnExit();
        boolean renameOk = zipFile.renameTo(tempFile);
        if (!renameOk) {
            throw new IOException("Could not rename the file " + zipFile.getAbsolutePath() + " -> " + tempFile.getAbsolutePath());
        } else {
            log("Removing entries from", zipFile);
            byte[] buf = new byte[1024];
            ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(tempFile)));
            ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));

            for (ZipEntry entry = zin.getNextEntry(); entry != null; entry = zin.getNextEntry()) {
                String name = entry.getName();
                if (entries.contains(name)) {
                    log("Removed:", name);
                } else {
                    zout.putNextEntry(new ZipEntry(name));

                    int len;
                    while ((len = zin.read(buf)) > 0) {
                        zout.write(buf, 0, len);
                    }
                }
            }

            zin.close();
            zout.close();
            tempFile.delete();
        }
    }

    private void recordBreadcumb(String message, DataBuilder data) {
        SentryBreadcrumb.info(null, message).data(data).push(sentryContext);
    }

    private Rule.FeatureMatcher createFeatureMatcher() {
        return new CurrentLaunchFeatureMatcher();
    }

    private StrSubstitutor createArgumentsSubstitutor() throws MinecraftException {
        Map<String, String> map = new HashMap<>();


        map.putAll(account.getUser().getLoginCredentials().map());

        /*map.put("auth_access_token", user.getAuthenticatedToken());
        map.put("user_properties", new GsonBuilder().registerTypeAdapter(PropertyMap.class, new com.mojang.launcher.LegacyPropertyMapSerializer()).create().toJson(authentication.getUserProperties()));
        map.put("user_property_map", new GsonBuilder().registerTypeAdapter(PropertyMap.class, new com.mojang.authlib.properties.PropertyMap.Serializer()).create().toJson(authentication.getUserProperties()));

        if ((authentication.isLoggedIn()) && (authentication.canPlayOnline())) {
            if ((authentication instanceof com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication)) {
                map.put("auth_session", String.format("token:%s:%s", new Object[] { authentication.getAuthenticatedToken(), UUIDTypeAdapter.fromUUID(authentication.getSelectedProfile().getId()) }));
            } else {
                map.put("auth_session", authentication.getAuthenticatedToken());
            }
        }
        else {
            map.put("auth_session", "-");
        }

        if (authentication.getSelectedProfile() != null) {
            map.put("auth_player_name", authentication.getSelectedProfile().getName());
            map.put("auth_uuid", UUIDTypeAdapter.fromUUID(authentication.getSelectedProfile().getId()));
            map.put("user_type", authentication.getUserType().getName());
        } else {
            map.put("auth_player_name", "Player");
            map.put("auth_uuid", new UUID(0L, 0L).toString());
            map.put("user_type", UserType.LEGACY.getName());
        }

        map.put("profile_name", selectedProfile.getName());*/

        map.put("version_name", version.getID());

        map.put("game_directory", gameDir.getAbsolutePath());
        map.put("game_assets", localAssetsDir.getAbsolutePath());

        map.put("assets_root", globalAssetsDir.getAbsolutePath());
        map.put("assets_index_name", version.getAssetIndex().getId());

        map.put("version_type", version.getReleaseType().toString());

        if (windowSize[0] > 0 && windowSize[1] > 0) {
            map.put("resolution_width", String.valueOf(windowSize[0]));
            map.put("resolution_height", String.valueOf(windowSize[1]));
        } else {
            map.put("resolution_width", "");
            map.put("resolution_height", "");
        }

        map.put("language", "en-us");

        if (resourceChecker != null) {
            for (AssetIndex.AssetObject asset : resourceChecker.getAssetList()) {
                String hash = asset.getHash();
                String path = new File(assetsObjectsDir, hash.substring(0, 2) + "/" + hash).getAbsolutePath();
                map.put("asset=" + asset.getHash(), path);
            }
        }

        map.put("launcher_name", "java-minecraft-launcher");
        map.put("launcher_version", CAPABLE_WITH);
        map.put("natives_directory", this.nativeDir.getAbsolutePath());
        map.put("classpath", constructClassPath(version));
        map.put("classpath_separator", System.getProperty("path.separator"));
        map.put("primary_jar", new File(rootDir, "versions/" + version.getID() + "/" + version.getID() + ".jar").getAbsolutePath());

        return new StrSubstitutor(map);
    }


    private void recordValue(String key, Object value) {
        recordBreadcumb("valueSet", DataBuilder.create(key, value));
    }

    public enum LoggerVisibility {
        ALWAYS,
        ON_CRASH,
        NONE
    }

    class MinecraftLauncherAborted extends RuntimeException {
        MinecraftLauncherAborted(String message) {
            super(message);
        }

        MinecraftLauncherAborted(Throwable cause) {
            super(cause);
        }
    }

    public enum MinecraftLauncherStep {
        NONE,
        COLLECTING,
        DOWNLOADING,
        CONSTRUCTING,
        LAUNCHING,
        POSTLAUNCH
    }
}
