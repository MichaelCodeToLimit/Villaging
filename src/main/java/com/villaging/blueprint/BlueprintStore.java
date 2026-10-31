package com.villaging.blueprint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Saves blueprints as small JSON files, one per name. A layer is a list of rows (one per z) of characters (one per x),
 * with {@code .} for air. Loading never throws: a missing, damaged or unsafe file is simply "not there".
 */
public final class BlueprintStore {
	private static final Gson GSON = new GsonBuilder().create();

	private BlueprintStore() {
	}

	public static void save(Path dir, Blueprint b) throws IOException {
		char[][][] grid = new char[b.sizeY()][b.sizeZ()][b.sizeX()];
		for (char[][] layer : grid) {
			for (char[] row : layer) {
				java.util.Arrays.fill(row, '.');
			}
		}
		for (Cell c : b.cells()) {
			grid[c.y()][c.z()][c.x()] = c.key();
		}
		JsonObject root = new JsonObject();
		root.addProperty("name", b.name());
		JsonArray size = new JsonArray();
		size.add(b.sizeX());
		size.add(b.sizeY());
		size.add(b.sizeZ());
		root.add("size", size);
		root.addProperty("origin", b.origin());
		JsonObject palette = new JsonObject();
		for (Map.Entry<Character, String> e : b.palette().entrySet()) {
			palette.addProperty(String.valueOf(e.getKey()), e.getValue());
		}
		root.add("palette", palette);
		JsonArray layers = new JsonArray();
		for (char[][] layer : grid) {
			JsonArray rows = new JsonArray();
			for (char[] row : layer) {
				rows.add(new String(row));
			}
			layers.add(rows);
		}
		root.add("layers", layers);
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(b.name() + ".json"), GSON.toJson(root));
	}

	public static Optional<Blueprint> load(Path dir, String name, int maxCells) {
		if (!Blueprint.isValidName(name)) {
			return Optional.empty();
		}
		Path file = dir.resolve(name + ".json");
		if (!Files.isRegularFile(file)) {
			return Optional.empty();
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			JsonArray size = root.getAsJsonArray("size");
			int sx = size.get(0).getAsInt();
			int sy = size.get(1).getAsInt();
			int sz = size.get(2).getAsInt();
			Map<Character, String> palette = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("palette").entrySet()) {
				if (e.getKey().length() != 1) {
					return Optional.empty();
				}
				palette.put(e.getKey().charAt(0), e.getValue().getAsString());
			}
			JsonArray layers = root.getAsJsonArray("layers");
			if (layers.size() != sy) {
				return Optional.empty();
			}
			List<Cell> cells = new ArrayList<>();
			for (int y = 0; y < sy; y++) {
				JsonArray rows = layers.get(y).getAsJsonArray();
				if (rows.size() != sz) {
					return Optional.empty();
				}
				for (int z = 0; z < sz; z++) {
					String row = rows.get(z).getAsString();
					if (row.length() != sx) {
						return Optional.empty();
					}
					for (int x = 0; x < sx; x++) {
						if (row.charAt(x) != '.') {
							cells.add(new Cell(x, y, z, row.charAt(x)));
						}
					}
				}
			}
			String origin = root.has("origin") ? root.get("origin").getAsString() : "imported";
			return Optional.of(Blueprint.create(name, sx, sy, sz, palette, cells, origin, maxCells));
		} catch (IOException | RuntimeException | BlueprintException e) {
			return Optional.empty();
		}
	}

	/** The saved blueprint names, sorted; empty when the folder does not exist. */
	public static List<String> names(Path dir) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.map(p -> p.getFileName().toString()).filter(f -> f.endsWith(".json"))
					.map(f -> f.substring(0, f.length() - ".json".length())).filter(Blueprint::isValidName).sorted().toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	public static boolean delete(Path dir, String name) {
		if (!Blueprint.isValidName(name)) {
			return false;
		}
		try {
			return Files.deleteIfExists(dir.resolve(name + ".json"));
		} catch (IOException e) {
			return false;
		}
	}
}
