package com.loohp.interactionvisualizer.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import com.loohp.interactionvisualizer.InteractionVisualizer;
import com.loohp.interactionvisualizer.api.InteractionVisualizerAPI.Modules;
import com.loohp.interactionvisualizer.objectholders.EntryKey;
import com.loohp.interactionvisualizer.utils.ArrayUtils;

public class Database {
	
	public static final String EMPTY_BITSET_BASE64 = ArrayUtils.toBase64String(new BitSet().toByteArray());
	
	public static boolean isMYSQL = false;
	
	private static Connection connection;
	private static String host, database, username, password;
	private static String preferenceTable = "USER_PERFERENCES";
	private static String indexMappingTable = "INDEX_MAPPING";
	private static int port;
    
    private static Object syncdb = new Object();
	
	public static void setup() {
		String type = InteractionVisualizer.plugin.getConfiguration().getString("Database.Type");
		if (type.equalsIgnoreCase("MYSQL")) {
			isMYSQL = true;
		} else {
			isMYSQL = false;
		}
		synchronized (syncdb) {
			if (isMYSQL) {
				mysqlSetup(true);
				createTable();
				try {
					connection.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			} else {
				sqliteSetup(true);
				try {
					connection.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private static void open() {
		if (isMYSQL) {
			mysqlSetup(false);
		} else {
			sqliteSetup(false);
		}
	}
	
	public static void mysqlSetup(boolean echo) {
        host = InteractionVisualizer.plugin.getConfiguration().getString("Database.MYSQL.Host");
        port =  InteractionVisualizer.plugin.getConfiguration().getInt("Database.MYSQL.Port");
        database = InteractionVisualizer.plugin.getConfiguration().getString("Database.MYSQL.Database");
        username = InteractionVisualizer.plugin.getConfiguration().getString("Database.MYSQL.Username");
        password = InteractionVisualizer.plugin.getConfiguration().getString("Database.MYSQL.Password");

        try {
			if (getConnection() != null && !getConnection().isClosed()) {
				return;
			}
			
			Class.forName("com.mysql.jdbc.Driver");
			setConnection(DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, username, password));
			
			if (echo == true) {
				Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[InteractionVisualizer] MYSQL CONNECTED");
			}
		} catch (SQLException e) {
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[InteractionVisualizer] MYSQL Failed to connect! (SQLException)");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[InteractionVisualizer] MYSQL Failed to connect! (ClassNotFoundException)");
			e.printStackTrace();
		}
	}
	
	public static void sqliteSetup(boolean echo) {	   
		try {
			Class.forName("org.sqlite.JDBC");
	        connection = DriverManager.getConnection("jdbc:sqlite:plugins/InteractionVisualizer/database.db");
	        if (echo) {
	        	Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[InteractionVisualizer] Opened Sqlite database successfully");
	        }

	        Statement stmt0 = connection.createStatement();
	        String sql0 = "CREATE TABLE IF NOT EXISTS " + preferenceTable + " (UUID TEXT PRIMARY KEY, NAME TEXT NOT NULL, ITEMSTAND TEXT, ITEMDROP TEXT, HOLOGRAM TEXT);"; 
	        stmt0.executeUpdate(sql0);
	        stmt0.close();
	        
	        Statement stmt1 = connection.createStatement();
	        String sql1 = "CREATE TABLE IF NOT EXISTS " + indexMappingTable + " (ENTRY TEXT PRIMARY KEY, BITMASK_INDEX INTEGER);";
	        stmt1.executeUpdate(sql1);
	        stmt1.close(); 
	    } catch (Exception e) {
	    	Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[InteractionVisualizer] Unable to connect to sqlite database!!!");
	    	e.printStackTrace();
	    	InteractionVisualizer.plugin.getPluginLoader().disablePlugin(InteractionVisualizer.plugin);
	    }
	}

	public static Connection getConnection() {
		return connection;
	}

	public static void setConnection(Connection connection) {
		Database.connection = connection;
	}
	
    public static void createTable() {
    	synchronized (syncdb) {
	    	open();
	        try {
	        	PreparedStatement statement0 = getConnection().prepareStatement("CREATE TABLE IF NOT EXISTS " + preferenceTable + " (UUID Text, NAME Text, ITEMSTAND Text, ITEMDROP Text, HOLOGRAM Text)");
	            statement0.execute();
	            
	            PreparedStatement statement1 = getConnection().prepareStatement("CREATE TABLE IF NOT EXISTS " + indexMappingTable + " (ENTRY Text, BITMASK_INDEX INT)");
	            statement1.execute();
	        } catch (SQLException e) {
	            e.printStackTrace();
	        }
	        try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
    	}
    }
    
    public static Map<Integer, EntryKey> getBitIndex() {
    	Map<Integer, EntryKey> index = new HashMap<>();
    	synchronized (syncdb) {
			open();
			try {
				PreparedStatement statement = getConnection().prepareStatement("SELECT * FROM " + indexMappingTable);
				ResultSet results = statement.executeQuery();
				while (results.next()) {
					index.put(results.getInt("BITMASK_INDEX"), new EntryKey(results.getString("ENTRY")));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return index;
		}
    }
    
    public static void setBitIndex(Map<Integer, EntryKey> index) {
    	synchronized (syncdb) {
			open();
			try {
				for (Entry<Integer, EntryKey> entry : index.entrySet()) {
					PreparedStatement statement = getConnection().prepareStatement("SELECT * FROM " + indexMappingTable + " WHERE ENTRY=?");
					statement.setString(1, entry.getValue().toString());
					ResultSet results = statement.executeQuery();
					if (results.next()) {
						PreparedStatement statement1 = getConnection().prepareStatement("UPDATE " + indexMappingTable + " SET BITMASK_INDEX=? WHERE ENTRY=?");
						statement1.setInt(1, entry.getKey());
						statement1.setString(2, entry.getValue().toString());
						statement1.executeUpdate();
					} else {
						PreparedStatement insert = getConnection().prepareStatement("INSERT INTO " + indexMappingTable + " (BITMASK_INDEX,ENTRY) VALUES (?,?)");
						insert.setInt(1, entry.getKey());
						insert.setString(2, entry.getValue().toString());
						insert.executeUpdate();
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
    }
    
	public static boolean playerExists(UUID uuid) {
		synchronized (syncdb) {
			boolean exist = false;
			open();
			try {
				PreparedStatement statement = getConnection().prepareStatement("SELECT * FROM " + preferenceTable + " WHERE UUID=?");
				statement.setString(1, uuid.toString());
	
				ResultSet results = statement.executeQuery();
				if (results.next()) {
					exist = true;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return exist;
		}
	}

	public static void createPlayer(UUID uuid, String name) {
		synchronized (syncdb) {
			open();
			try {
				PreparedStatement insert = getConnection().prepareStatement("INSERT INTO " + preferenceTable + " (UUID,NAME,ITEMSTAND,ITEMDROP,HOLOGRAM) VALUES (?,?,?,?,?)");
				insert.setString(1, uuid.toString());
				insert.setString(2, name);
				insert.setString(3, EMPTY_BITSET_BASE64);
				insert.setString(4, EMPTY_BITSET_BASE64);
				insert.setString(5, EMPTY_BITSET_BASE64);
				insert.executeUpdate();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void setItemStand(UUID uuid, BitSet bitset) {
		synchronized (syncdb) {
			open();
			try {
				PreparedStatement statement = getConnection().prepareStatement("UPDATE " + preferenceTable + " SET ITEMSTAND=? WHERE UUID=?");
				statement.setString(1, ArrayUtils.toBase64String(bitset.toByteArray()));
				statement.setString(2, uuid.toString());
				statement.executeUpdate();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void setItemDrop(UUID uuid, BitSet bitset) {
		synchronized (syncdb) {
			open();
			try {
				PreparedStatement statement = getConnection().prepareStatement("UPDATE " + preferenceTable + " SET ITEMDROP=? WHERE UUID=?");
				statement.setString(1, ArrayUtils.toBase64String(bitset.toByteArray()));
				statement.setString(2, uuid.toString());
				statement.executeUpdate();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void setHologram(UUID uuid, BitSet bitset) {
		synchronized (syncdb) {
			open();
			try {
				PreparedStatement statement = getConnection().prepareStatement("UPDATE " + preferenceTable + " SET HOLOGRAM=? WHERE UUID=?");
				statement.setString(1, ArrayUtils.toBase64String(bitset.toByteArray()));
				statement.setString(2, uuid.toString());
				statement.executeUpdate();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static Map<Modules, BitSet> getPlayerInfo(UUID uuid) {
		Map<Modules, BitSet> map = new HashMap<>();
		synchronized (syncdb) {
			open();
			try {
				PreparedStatement statement = getConnection().prepareStatement("SELECT * FROM " + preferenceTable + " WHERE UUID=?");
				statement.setString(1, uuid.toString());
				ResultSet results = statement.executeQuery();
				results.next();
				
				map.put(Modules.ITEMSTAND, BitSet.valueOf(ArrayUtils.fromBase64String(results.getString("ITEMSTAND"))));
				map.put(Modules.ITEMDROP, BitSet.valueOf(ArrayUtils.fromBase64String(results.getString("ITEMDROP"))));
				map.put(Modules.HOLOGRAM, BitSet.valueOf(ArrayUtils.fromBase64String(results.getString("HOLOGRAM"))));				
				
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return map;
	}

}
