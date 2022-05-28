package com.clantracker;

import com.clantracker.api.APIClient;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@PluginDescriptor(
		name = "Clan Tracker",
		description = "Tracks clan information and drops",
		enabledByDefault = false
)

public class ClanTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	// Injects our config
	@Inject
	private ClanTrackerConfig config;

	@Inject
	private APIClient apiClient;

	// Sequence number
	public int sequenceNumber = 0;

	// Provides our config
	@Provides
	ClanTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanTrackerConfig.class);
	}

	public void setSequenceNumber(int newSequenceNumber) {
		this.sequenceNumber = newSequenceNumber;
	}
	@Override
	protected void startUp()
	{
		// runs on plugin startup
		log.info("Plugin started");

		try {
			sequenceNumber = apiClient.getSequence();
		} catch (IOException e) {
			sequenceNumber = -1;
		}
		log.info("Sequence number: " + sequenceNumber);

	}

	@Override
	protected void shutDown()
	{
		// runs on plugin shutdown
		log.info("Plugin stopped");
	}

	public enum SystemMessageType {
		NORMAL(1),
		DROP(2),
		RAID_DROP(3),
		PET_DROP(4),
		PERSONAL_BEST(5),
		COLLECTION_LOG(6),
		QUEST(7),
		PVP(8),
		LOGIN(-1);

		public final int code;

		private SystemMessageType(int code) {
			this.code = code;
		}
	}

	private SystemMessageType getSystemMessageType(String message)
	{
		if (message.contains("received a drop:")) {
			return SystemMessageType.DROP;
		} else if (message.contains("received special loot from a raid:")) {
			return SystemMessageType.RAID_DROP;
		} else if (message.contains("has completed a quest:")) {
			return SystemMessageType.QUEST;
		} else if (message.contains("received a new collection log item:")) {
			return SystemMessageType.COLLECTION_LOG;
		} else if (message.contains("personal best:")) {
			return SystemMessageType.PERSONAL_BEST;
		} else if (message.contains("To talk in your clan's channel, start each line of chat with")) {
			return SystemMessageType.LOGIN;
		} else if (message.contains("has defeated") || message.contains("has been defeated by")) {
			return SystemMessageType.PVP;
		} else if (message.contains("has a funny feeling like")) {
			return SystemMessageType.PET_DROP;
		} else {
			return SystemMessageType.NORMAL;
		}
	}


	@Subscribe
	private void onChatMessage(ChatMessage chatMessage)
	{

		String author = chatMessage.getName().replace((char)160, ' ').replaceAll("<img=\\d>", "");
		String content = sanitizeMessage(chatMessage.getMessage());
		String clanName = "";
		switch (chatMessage.getType()) {
			case CLAN_CHAT:
				clanName = chatMessage.getSender().replace((char)160, ' ');
				try {
					setSequenceNumber(apiClient.message(clanName, config.pluginPassword(), sequenceNumber, 0, author, content, 0, 3));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				log.info(String.format("[%s] %s", author, content));
				break;
			case CLAN_MESSAGE:
				clanName = chatMessage.getSender().replace((char)160, ' ');
				SystemMessageType messageType = getSystemMessageType(content);
				log.info(String.format("[SYSTEM] %s", content));
				try {
					setSequenceNumber(apiClient.message(clanName, config.pluginPassword(), sequenceNumber, messageType.code, author, content, 0, 3));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				break;
			default:
				break;
		}
	}

	private List<String> getOnlineClanMembers() {
		List<net.runelite.api.clan.ClanChannelMember> onlineMembers = client.getClanChannel().getMembers();
		List<String> onlineClanMembers = new ArrayList<String>();

		for (int i = 0; i < onlineMembers.size(); i++) {
			if (client.getClanSettings().titleForRank(onlineMembers.get(i).getRank()).getName().equals("Guest")) {
				log.info("********* Ignoring " + onlineMembers.get(i).getName());
			} else {
				log.info(onlineMembers.get(i).getName());
				onlineClanMembers.add(onlineMembers.get(i).getName());
			}
		}
		return onlineClanMembers;
	}

	private String sanitizeMessage(String message)
	{
		String newMessage = message;
		newMessage = newMessage.replace((char)160, ' ');
		newMessage = newMessage.replace("<lt>", "<");
		newMessage = newMessage.replace("<gt>", ">");
		return additionalCustomizations(newMessage);
	}

	private String additionalCustomizations(String message)
	{
		String newMessage = message;
		newMessage = newMessage.replaceAll("\\<img=\\d\\>", "");
		return newMessage;
	}
}