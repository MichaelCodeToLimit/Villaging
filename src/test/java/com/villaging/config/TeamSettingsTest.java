package com.villaging.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamSettingsTest {
	@TempDir
	Path dir;

	private TeamSettings read(String json) throws Exception {
		Path file = dir.resolve("villaging.json");
		Files.writeString(file, json);
		return VillagingConfig.load(file).teams();
	}

	@Test
	void defaultIsFive() {
		assertEquals(5, TeamSettings.defaults().maxSize());
		assertEquals(5, VillagingConfig.defaults().teams().maxSize());
	}

	@Test
	void clampsLowAndHigh() throws Exception {
		assertEquals(2, read("{\"teams\":{\"maxSize\":1}}").maxSize());
		assertEquals(20, read("{\"teams\":{\"maxSize\":99}}").maxSize());
	}

	@Test
	void missingOrNonPositiveFallsBack() throws Exception {
		assertEquals(5, read("{}").maxSize());
		assertEquals(5, read("{\"teams\":{}}").maxSize());
		assertEquals(5, read("{\"teams\":{\"maxSize\":0}}").maxSize());
		assertEquals(5, read("{\"teams\":{\"maxSize\":-3}}").maxSize());
	}

	@Test
	void fileValueIsRead() throws Exception {
		assertEquals(8, read("{\"teams\":{\"maxSize\":8}}").maxSize());
	}

	@Test
	void savingAKeyKeepsTheTeamSettings() throws Exception {
		Path file = dir.resolve("villaging.json");
		Files.writeString(file, "{\"teams\":{\"maxSize\":8}}");
		VillagingConfig changed = VillagingConfig.load(file).withKey("ollama", "");
		changed.save(file);
		assertEquals(8, VillagingConfig.load(file).teams().maxSize());
	}
}
