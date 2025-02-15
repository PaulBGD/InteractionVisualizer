package com.loohp.interactionvisualizer;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.simpleyaml.configuration.file.FileConfiguration;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.loohp.interactionvisualizer.api.events.InteractionVisualizerReloadEvent;
import com.loohp.interactionvisualizer.config.Config;
import com.loohp.interactionvisualizer.database.Database;
import com.loohp.interactionvisualizer.managers.AsyncExecutorManager;
import com.loohp.interactionvisualizer.managers.LangManager;
import com.loohp.interactionvisualizer.managers.LightManager;
import com.loohp.interactionvisualizer.managers.MaterialManager;
import com.loohp.interactionvisualizer.managers.MusicManager;
import com.loohp.interactionvisualizer.managers.PacketManager;
import com.loohp.interactionvisualizer.managers.PreferenceManager;
import com.loohp.interactionvisualizer.managers.SoundManager;
import com.loohp.interactionvisualizer.managers.TaskManager;
import com.loohp.interactionvisualizer.managers.TileEntityManager;
import com.loohp.interactionvisualizer.metrics.Charts;
import com.loohp.interactionvisualizer.metrics.Metrics;
import com.loohp.interactionvisualizer.nms.NMS;
import com.loohp.interactionvisualizer.objectholders.ILightManager;
import com.loohp.interactionvisualizer.placeholderAPI.Placeholders;
import com.loohp.interactionvisualizer.protocol.WatchableCollection;
import com.loohp.interactionvisualizer.updater.Updater;
import com.loohp.interactionvisualizer.updater.Updater.UpdaterResponse;
import com.loohp.interactionvisualizer.utils.LanguageUtils;
import com.loohp.interactionvisualizer.utils.MCVersion;
import com.loohp.interactionvisualizer.utils.PotionUtils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.HoverEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class InteractionVisualizer extends JavaPlugin {
	
	public static final int BSTATS_PLUGIN_ID = 7024;
	public static final String CONFIG_ID = "config";
	
	public static InteractionVisualizer plugin = null;
	public static ProtocolManager protocolManager;
	
	public static String exactMinecraftVersion;
	public static MCVersion version;
	public static Integer metaversion = 0;
	
	public static Boolean lightapi = false;
	public static Boolean openinv = false;
	public static Boolean vanish = false;
	public static Boolean cmi = false;
	public static Boolean ess3 = false;
	
	public static Set<String> exemptBlocks = new HashSet<>();
	public static Set<String> disabledWorlds = new HashSet<>();
	
	public static World defaultworld;
	public static Location defaultlocation;
	
	public static boolean itemStandEnabled = true;
	public static boolean itemDropEnabled = true;
	public static boolean hologramsEnabled = true;
	
	public static Double playerPickupYOffset = 0.0;
	
	public static Integer tileEntityCheckingRange = 1;
	public static double ignoreWalkSquared = 0.0;
	public static double ignoreFlySquared = 0.0;
	public static double ignoreGlideSquared = 0.0;
	
	public static Boolean handMovementEnabled = true;
	
	public static Integer lightUpdatePeriod = 10;
	
	public static boolean legacyChatAPI = false;
	
	public static boolean updaterEnabled = true;
	
	public static Map<World, Integer> playerTrackingRange = new HashMap<>();
	public static boolean hideIfObstructed = false;
	public static String language = "en_us";
	
	public static boolean allPacketsSync = false;
	public static boolean defaultDisabledAll = false;
	
	public static ILightManager lightManager;
	public static PreferenceManager preferenceManager;
	public static AsyncExecutorManager asyncExecutorManager;
	
	@Override
	public void onEnable() {
		plugin = this;
		
		protocolManager = ProtocolLibrary.getProtocolManager();
		
		Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
		
		exactMinecraftVersion = Bukkit.getVersion().substring(Bukkit.getVersion().indexOf("(") + 5, Bukkit.getVersion().indexOf(")"));
		version = MCVersion.fromPackageName(getServer().getClass().getPackage().getName());
		
		ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("InteractionVisualizer Async Processing Thread #%d").build();
		ExecutorService threadPool = new ThreadPoolExecutor(8, 120, 5000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), factory);
		asyncExecutorManager = new AsyncExecutorManager(threadPool);
		
		switch (version) {
		case V1_17:
			metaversion = 4;
			break;
		case V1_16_4:
		case V1_16_2:
		case V1_16:
		case V1_15:
			metaversion = 3;
			break;
		case V1_14:
			metaversion = 2;
			break;
		case V1_13_1:
		case V1_13:
			metaversion = 1;
			break;
		case V1_12:
		case V1_11:
			metaversion = 0;
			break;
		default:
			unsupportedMessage();
			break;
		}
		
		if (getServer().getPluginManager().getPlugin("LightAPI") != null) {
			hookMessage("LightAPI");
			lightapi = true;
			lightManager = new LightManager(this);
		} else {
			if (version.isOlderOrEqualTo(MCVersion.V1_16_4)) {
				Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[InteractionVisualizer] LightAPI is recommended to be installed on servers with Minecraft version 1.16.5 or below!");
			}
			lightManager = ILightManager.DUMMY_INSTANCE;
		}
		if (getServer().getPluginManager().getPlugin("OpenInv") != null) {
			hookMessage("OpenInv");
			openinv = true;
		}
		if (getServer().getPluginManager().getPlugin("SuperVanish") != null || getServer().getPluginManager().getPlugin("PremiumVanish") != null) {
			hookMessage("SuperVanish/PremiumVanish");
			vanish = true;
		}
		if (getServer().getPluginManager().getPlugin("CMI") != null) {
			hookMessage("CMI");
			cmi = true;
		}
		if (getServer().getPluginManager().getPlugin("Essentials") != null) {
			hookMessage("Essentials");
			ess3 = true;
		}
		
		if (!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}
		Config.loadConfig(CONFIG_ID, new File(getDataFolder(), "config.yml"), getClass().getClassLoader().getResourceAsStream("config.yml"), getClass().getClassLoader().getResourceAsStream("config.yml"), true);
		loadConfig();
		
		defaultworld = getServer().getWorlds().get(0);
		defaultlocation = new Location(defaultworld, 0, 0, 0);
		if (!version.isLegacy() && !version.equals(MCVersion.V1_13) && !version.equals(MCVersion.V1_13_1)) {
			defaultworld.setChunkForceLoaded(0, 0, true);
		}
		
		if (getConfiguration().getBoolean("Options.DownloadLanguageFiles")) {
			getServer().getScheduler().runTaskAsynchronously(this, () -> LangManager.generate());
		}
		
		WatchableCollection.setup();
		SoundManager.setup();
		MusicManager.setup();
		Database.setup();
		preferenceManager = new PreferenceManager(this);
		TaskManager.setup();
		TileEntityManager._init_();
		PacketManager.run();
		PacketManager.dynamicEntity();
		PotionUtils.setupPotions();
		
		MaterialManager.setup();
		
		getCommand("interactionvisualizer").setExecutor(new Commands());
		
		TaskManager.run();
		
		Charts.registerCharts(metrics);
		
		if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
			new Placeholders().register();
		}
		
		for (World world : Bukkit.getWorlds()) {
			for (Entity entity : world.getNearbyEntities(new Location(world, 0, 0, 0), 2, 2, 2)) {
				if (entity.getScoreboardTags().contains("isInteractionVisualizer")) {
					entity.remove();
				}
			}
		}
		
		exemptBlocks.add("CRAFTING_TABLE");
		exemptBlocks.add("WORKBENCH");
		exemptBlocks.add("LOOM");
		exemptBlocks.add("SMITHING_TABLE");
		exemptBlocks.add("SPAWNER");
		exemptBlocks.add("MOB_SPAWNER");
		exemptBlocks.add("BEACON");
		
		try {
			TextComponent test = new TextComponent("Legacy Bungeecord Chat API Test");
			test.setHoverEvent(new HoverEvent(Action.SHOW_TEXT, new Text("Test Hover Text")));
			test.getHoverEvent().getContents();
			legacyChatAPI = false;
		} catch (Throwable e) {
			legacyChatAPI = true;
			getServer().getConsoleSender().sendMessage(ChatColor.YELLOW + "[InteractiveChat] Legacy Bungeecord Chat API detected, using legacy methods...");
		}
		
		getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "[InteractionVisualizer] InteractionVisualizer has been enabled!");
		
		Bukkit.getScheduler().runTask(this, () -> {
			for (Player player : Bukkit.getOnlinePlayers()) {
				PacketManager.playerStatus.put(player, Collections.newSetFromMap(new ConcurrentHashMap<>()));
			}
		});
		
		InteractionVisualizer.asyncExecutorManager.runTaskLaterAsynchronously(() -> {
			if (updaterEnabled) {
				UpdaterResponse version = Updater.checkUpdate();
				if (!version.getResult().equals("latest")) {
					Updater.sendUpdateMessage(Bukkit.getConsoleSender(), version.getResult(), version.getSpigotPluginId());
					for (Player player : Bukkit.getOnlinePlayers()) {
						if (player.hasPermission("interactionvisualizer.update")) {
							Updater.sendUpdateMessage(player, version.getResult(), version.getSpigotPluginId());
						}
					}
				}
			}
		}, 100);
		/*
		protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.MONITOR, PacketType.Play.Server.SPAWN_ENTITY_LIVING) {
		    @Override
		    public void onPacketSending(PacketEvent event) {
		        if (!event.getPacketType().equals(PacketType.Play.Server.SPAWN_ENTITY_LIVING)) {
		        	return;
		        }
		        
		        PacketContainer packet = event.getPacket();
		        if (packet.getIntegers().read(1) != 1) {
		        	return;
		        }
		        
		        Bukkit.getConsoleSender().sendMessage("Sending ArmorStand Spawn Packet to " + event.getPlayer().getName() + " " + packet.getDoubles().read(0) + " " + packet.getDoubles().read(1) + " " + packet.getDoubles().read(2));
		    }
		});

		protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.MONITOR, PacketType.Play.Server.ENTITY_METADATA) {
		    @Override
		    public void onPacketSending(PacketEvent event) {
		        if (!event.getPacketType().equals(PacketType.Play.Server.ENTITY_METADATA)) {
		        	return;
		        }
		        
		        Bukkit.getConsoleSender().sendMessage("Sending ArmorStand Update Packet to " + event.getPlayer().getName());
		    }
		});
		
		protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.MONITOR, PacketType.Play.Server.ENTITY_DESTROY) {
		    @Override
		    public void onPacketSending(PacketEvent event) {
		        if (!event.getPacketType().equals(PacketType.Play.Server.ENTITY_DESTROY)) {
		        	return;
		        }
		        
		        Bukkit.getConsoleSender().sendMessage("Sending ArmorStand Remove Packet to " + event.getPlayer().getName());
		    }
		});
		
		protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.MONITOR, PacketType.Play.Server.ANIMATION) {
		    @Override
		    public void onPacketSending(PacketEvent event) {
		        if (!event.getPacketType().equals(PacketType.Play.Server.ANIMATION)) {
		        	return;
		        }
		        
		        Bukkit.getConsoleSender().sendMessage("Sending Animation Packet to " + event.getPlayer().getName());
		    }
		});
		*/
	}
	
	@Override
	public void onDisable() {
		preferenceManager.close();
		
		if (!Bukkit.getOnlinePlayers().isEmpty()) {
			getServer().getConsoleSender().sendMessage(ChatColor.YELLOW + "[InteractionVisualizer] Plugin reload detected, attempting to despawn all visual entities. If anything went wrong, please restart! (Reloads are always not recommended)");
			int[] entityIdArray = PacketManager.active.keySet().stream().mapToInt(each -> each.getEntityId()).toArray();
			PacketContainer[] packets = NMS.getInstance().createEntityDestoryPacket(entityIdArray);
			
			try {
				for (Player player : Bukkit.getOnlinePlayers()) {
					for (PacketContainer packet : packets) {
						protocolManager.sendServerPacket(player, packet);
					}
				}
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		
		asyncExecutorManager.close();
		getServer().getConsoleSender().sendMessage(ChatColor.RED + "[InteractionVisualizer] InteractionVisualizer has been disabled!");
	}
	
	public FileConfiguration getConfiguration() {
		return Config.getConfig(CONFIG_ID).getConfiguration();
	}
	
	public void loadConfig() {
		Config config = Config.getConfig(CONFIG_ID);
		config.reload();
		
		itemStandEnabled = getConfiguration().getBoolean("Modules.ItemStand.Enabled");
		itemDropEnabled = getConfiguration().getBoolean("Modules.ItemDrop.Enabled");
		hologramsEnabled = getConfiguration().getBoolean("Modules.Hologram.Enabled");
		
		playerPickupYOffset = getConfiguration().getDouble("Settings.PickupAnimationPlayerYOffset");
		
		tileEntityCheckingRange = getConfiguration().getInt("TileEntityUpdate.CheckingRange");
		ignoreWalkSquared =  getConfiguration().getDouble("TileEntityUpdate.IgnoreMovementSpeed.Normal");
		ignoreWalkSquared *= ignoreWalkSquared;
		ignoreFlySquared =  getConfiguration().getDouble("TileEntityUpdate.IgnoreMovementSpeed.Flying");
		ignoreFlySquared *= ignoreFlySquared;
		ignoreGlideSquared =  getConfiguration().getDouble("TileEntityUpdate.IgnoreMovementSpeed.Gliding");
		ignoreGlideSquared *= ignoreGlideSquared;
		
		handMovementEnabled = getConfiguration().getBoolean("Settings.UseHandSwingAnimation");
		
		disabledWorlds = getConfiguration().getStringList("Settings.DisabledWorlds").stream().collect(Collectors.toSet());
		hideIfObstructed = getConfiguration().getBoolean("Settings.HideIfViewObstructed");
		
		lightUpdatePeriod = getConfiguration().getInt("LightUpdate.Period");
		
		updaterEnabled = getConfiguration().getBoolean("Options.Updater");
		
		language = getConfiguration().getString("Settings.Language");
		
		playerTrackingRange.clear();
		int defaultRange = getServer().spigot().getConfig().getInt("world-settings.default.entity-tracking-range.players", 64);
		for (World world : getServer().getWorlds()) {
			int range = getServer().spigot().getConfig().getInt("world-settings." + world.getName() + ".entity-tracking-range.players", defaultRange);
			playerTrackingRange.put(world, range);
		}
		
		allPacketsSync = getConfiguration().getBoolean("Settings.SendAllPacketsInSync");
		defaultDisabledAll = getConfiguration().getBoolean("Settings.DefaultDisableAll");
		
		LanguageUtils.loadTranslations(language);
		
		getServer().getPluginManager().callEvent(new InteractionVisualizerReloadEvent());
	}
	
	private static void unsupportedMessage() {
		Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "This version of minecraft is unsupported!");
	}
	
	private static void hookMessage(String pluginName) {
		Bukkit.getConsoleSender().sendMessage(ChatColor.LIGHT_PURPLE + "[InteractionVisualizer] InteractionVisualizer has hooked into " + pluginName + "!");
	}
	
}
