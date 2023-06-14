package com.clantracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("Clan2Discord")

public interface ClanTrackerConfig extends Config
{
	@ConfigItem(
			keyName = "pluginPassword",
			name = "Plugin Password",
			description = "Password is required for validation",
			position = 0
	)
	default String pluginPassword()
	{
		return "";
	}

	@ConfigItem(
			keyName = "apiUrl",
			name = "API Url",
			description = "URL of clan tracker API",
			position = 1
	)
	default String apiUrl()
	{
		return "";
	}

}