package com.craftmend.openaudiomc.spigot;

import com.craftmend.openaudiomc.OpenAudioMc;
import com.craftmend.openaudiomc.generic.storage.interfaces.ConfigurationImplementation;
import com.craftmend.openaudiomc.generic.platform.interfaces.TaskProvider;
import com.craftmend.openaudiomc.generic.platform.interfaces.OpenAudioInvoker;
import com.craftmend.openaudiomc.generic.logging.OpenAudioLogger;
import com.craftmend.openaudiomc.generic.networking.interfaces.NetworkingService;
import com.craftmend.openaudiomc.generic.platform.Platform;
import com.craftmend.openaudiomc.generic.state.states.WorkerState;
import com.craftmend.openaudiomc.generic.state.states.IdleState;

import com.craftmend.openaudiomc.generic.voicechat.DefaultVoiceServiceImpl;
import com.craftmend.openaudiomc.generic.voicechat.VoiceService;
import com.craftmend.openaudiomc.spigot.modules.commands.SpigotCommandModule;
import com.craftmend.openaudiomc.spigot.modules.configuration.SpigotConfigurationImplementation;
import com.craftmend.openaudiomc.spigot.modules.predictive.PredictiveMediaModule;
import com.craftmend.openaudiomc.spigot.modules.proxy.ProxyModule;
import com.craftmend.openaudiomc.spigot.modules.proxy.enums.ClientMode;
import com.craftmend.openaudiomc.spigot.modules.punishments.LitebansIntegration;
import com.craftmend.openaudiomc.spigot.modules.regions.service.RegionService;
import com.craftmend.openaudiomc.spigot.modules.shortner.AliasModule;
import com.craftmend.openaudiomc.spigot.modules.show.ShowModule;
import com.craftmend.openaudiomc.spigot.modules.traincarts.TrainCartsModule;
import com.craftmend.openaudiomc.spigot.modules.traincarts.service.TrainCartsService;
import com.craftmend.openaudiomc.spigot.modules.voicechat.SpigotVoiceChatModule;
import com.craftmend.openaudiomc.spigot.services.dependency.DependencyService;
import com.craftmend.openaudiomc.spigot.services.scheduling.SpigotTaskProvider;
import com.craftmend.openaudiomc.spigot.services.server.ServerService;
import com.craftmend.openaudiomc.spigot.modules.players.PlayerModule;
import com.craftmend.openaudiomc.spigot.modules.regions.RegionModule;
import com.craftmend.openaudiomc.spigot.modules.speakers.SpeakerModule;
import com.craftmend.openaudiomc.spigot.services.threading.ExecutorService;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;

@Getter
public final class OpenAudioMcSpigot extends JavaPlugin implements OpenAudioInvoker {

    @Setter
    private TrainCartsModule trainCartsModule;
    @Setter
    private RegionModule regionModule;

    private PredictiveMediaModule predictiveMediaService;
    private AliasModule aliasModule;
    private ExecutorService executorService;
    private ProxyModule proxyModule;
    private PlayerModule playerModule;
    private SpigotCommandModule commandModule;
    private SpeakerModule speakerModule;
    private ShowModule showModule;
    private DependencyService dependencyService;
    private ServerService serverService;
    private SpigotVoiceChatModule spigotVoicechatModule;
    private OpenAudioMc openAudioMc;
    public static StateFlag VOICE;
    /**
     * Constant: main plugin instance and plugin timing
     */
    @Getter private static OpenAudioMcSpigot instance;
    private final Instant boot = Instant.now();

    /**
     * load the plugin and start all of it's independent modules and services
     * this is in a specific order
     */
    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;

        // setup loader
        this.proxyModule = new ProxyModule();

        // setup core
        try {
            openAudioMc = new OpenAudioMc(this);
            // startup modules and services
            this.dependencyService = new DependencyService(this);
            this.aliasModule = new AliasModule(this);
            this.executorService = new ExecutorService(this);
            this.serverService = new ServerService();
            this.playerModule = new PlayerModule(this);
            this.speakerModule = new SpeakerModule(this);
            this.commandModule = new SpigotCommandModule(this);
            this.showModule = new ShowModule(this);
            this.predictiveMediaService = new PredictiveMediaModule();
            this.spigotVoicechatModule = new SpigotVoiceChatModule(this);

            this.dependencyService
                    .ifPluginEnabled("LiteBans", new LitebansIntegration())
                    .ifPluginEnabled("WorldGuard", new RegionService(this))
                    .ifPluginEnabled("Train_Carts", new TrainCartsService(this));

            // set state to idle, to allow connections and such, but only if not a node
            if (proxyModule.getMode() == ClientMode.NODE) {
                OpenAudioMc.getInstance().getStateService().setState(new WorkerState());
            } else {
                OpenAudioMc.getInstance().getStateService().setState(new IdleState("OpenAudioMc started and awaiting command"));
            }

            // timing end and calc
            Instant finish = Instant.now();
            OpenAudioLogger.toConsole("Starting and loading took " + Duration.between(boot, finish).toMillis() + "MS");
        } catch (Exception e) {
            e.printStackTrace();
            Bukkit.getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * save configuration and stop the plugin
     */
    @Override
    public void onDisable() {
        OpenAudioLogger.toConsole("Shutting down");
        predictiveMediaService.onDisable();
        openAudioMc.disable();
        HandlerList.unregisterAll(this);
        OpenAudioLogger.toConsole("Stopped OpenAudioMc. Goodbye.");
    }

    public void registerEvents(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    @Override
    public void onLoad(){
        FlagRegistry flagRegistry = WorldGuard.getInstance().getFlagRegistry();

        try {

            StateFlag flag = new StateFlag("voice", true);
            flagRegistry.register(flag);
            VOICE = flag;
        } catch (FlagConflictException e) {

            Flag<?> existing = flagRegistry.get("voice");
            if (existing instanceof StateFlag) {
                VOICE = (StateFlag) existing;
            } else {

            }
        }
    }

    @Override
    public boolean hasPlayersOnline() {
        return !Bukkit.getOnlinePlayers().isEmpty();
    }

    @Override
    public boolean isNodeServer() {
        return getProxyModule().getMode() != ClientMode.STAND_ALONE;
    }

    @Override
    public Platform getPlatform() {
        return Platform.SPIGOT;
    }

    @Override
    public Class<? extends NetworkingService> getServiceClass() {
        return proxyModule.getMode().getServiceClass();
    }

    @Override
    public TaskProvider getTaskProvider() {
        return new SpigotTaskProvider();
    }

    @Override
    public ConfigurationImplementation getConfigurationProvider() {
        return new SpigotConfigurationImplementation(OpenAudioMcSpigot.getInstance());
    }

    @Override
    public String getPluginVersion() {
        return getDescription().getVersion();
    }

    @Override
    public int getServerPort() {
        return Bukkit.getPort();
    }

    @Override
    public VoiceService getVoiceService() {
        return new DefaultVoiceServiceImpl();
    }

}
