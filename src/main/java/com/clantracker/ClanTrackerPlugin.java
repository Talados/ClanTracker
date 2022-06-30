package com.clantracker;

import com.clantracker.api.APIClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;


import java.io.IOException;
import java.time.temporal.ChronoUnit;
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

	// ClientThread for async methods
	@Inject
	private ClientThread clientThread;

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

	private Boolean loggedIn()
	{
		return client.getGameState().equals(GameState.LOGGED_IN);
	}

	public void setSequenceNumber(int newSequenceNumber) {
		this.sequenceNumber = newSequenceNumber;
	}

	public int getSequenceNumber(){
		return this.sequenceNumber;
	}

	@Override
	protected void startUp()
	{
		// runs on plugin startup

		try {
			Callback callback = new Callback() {
				public void onResponse(Call call, Response response)
						throws IOException {
					if (response.body() == null)
					{
						log.debug("API Call - Response was null.");
						response.close();
					}
					else
					{
						String responseString = response.body().string();

						JsonObject jsonResponse = new JsonParser().parse(responseString).getAsJsonObject();
						response.close();

						setSequenceNumber(jsonResponse.get("sequence_number").getAsInt());
					}
				}

				public void onFailure(Call call, IOException e) {
					setSequenceNumber(-1);
				}
			};
			apiClient.getSequence(callback);

		} catch (IOException e) {
			setSequenceNumber(-1);
		}

	}

	@Override
	protected void shutDown()
	{
		// runs on plugin shutdown
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

	private Callback getMessageCallback(){
		Callback callback = new Callback() {
			@Override
			public void onFailure(Call call, IOException e)
			{
				setSequenceNumber(-1);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				if(response.body()==null) {
					log.debug("API Call - Response was null.");
					response.close();
					setSequenceNumber(-1);
				} else {
					String responseString = response.body().string();

					JsonObject jsonResponse = new JsonParser().parse(responseString).getAsJsonObject();
					int responseSequenceNumber = jsonResponse.get("sequence_number").getAsInt();
					response.close();

					if (response.code() == 403) {
						setSequenceNumber(responseSequenceNumber);
					}
				}
			}
		};
		return callback;
	}

	@Subscribe
	private void onChatMessage(ChatMessage chatMessage)
	{
		String author;
		String content;
		String clanName = "";

		switch (chatMessage.getType()) {
			case CLAN_CHAT:
				author = chatMessage.getName().replace((char)160, ' ').replaceAll("<img=\\d+>", "");
				content = sanitizeMessage(chatMessage.getMessage());
				clanName = client.getClanChannel().getName().replace((char)160, ' ');

				try {
					apiClient.message(clanName, config.pluginPassword(), sequenceNumber, 0, author, content, 0, 3, getMessageCallback());
					setSequenceNumber(sequenceNumber + 1);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				break;
			case CLAN_MESSAGE:
				author = chatMessage.getName().replace((char)160, ' ').replaceAll("<img=\\d+>", "");
				content = sanitizeMessage(chatMessage.getMessage());
				clanName = client.getClanChannel().getName().replace((char)160, ' ');

				SystemMessageType messageType = getSystemMessageType(content);
				try {
					apiClient.message(clanName, config.pluginPassword(), sequenceNumber, messageType.code, author, content, 0, 3, getMessageCallback());
					setSequenceNumber(sequenceNumber + 1);
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
			} else {
				onlineClanMembers.add(onlineMembers.get(i).getName());
			}
		}
		return onlineClanMembers;
	}
	@Schedule(
			period = 5,
			unit = ChronoUnit.MINUTES,
			asynchronous = true
	)
	public void onlineCountScheduleWrapper()
	{
		clientThread.invoke(this::sendClanOnlineCount);
	}

	public Callback getOnlineCountCallback()
	{
		Callback callback = new Callback() {
			public void onResponse(Call call, Response response)
					throws IOException {
			}

			public void onFailure(Call call, IOException e) {
				log.debug("Error " + e);
			}
		};
		return callback;
	}
	public void sendClanOnlineCount()
	{
		if (!loggedIn()) return;


		ClanChannel clan = client.getClanChannel();
		if (clan == null) return;

		String clanName = client.getClanChannel().getName();
		if (clanName == null) return;

		List<String> onlineMembers = getOnlineClanMembers();
		try {
			apiClient.sendOnlineCount(onlineMembers, clanName.replace((char)160, ' '), config.pluginPassword(), getOnlineCountCallback());
		} catch (IOException e) {
			log.debug("Exception!\n" + e);
		}
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
		newMessage = newMessage.replaceAll("\\<img=\\d+\\>", "");
		return newMessage;
	}
}