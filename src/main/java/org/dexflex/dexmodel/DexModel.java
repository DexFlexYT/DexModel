package org.dexflex.dexmodel;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DexModel implements ModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("dexmodel");

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("model")
					.requires(source -> source.hasPermissionLevel(2)) // Require OP level 2
					.then(CommandManager.literal("list")
							.executes(context -> {
								ServerCommandSource source = context.getSource();
								listModelFiles(source);
								return 1;
							}))
					.then(CommandManager.literal("display")
							.then(CommandManager.argument("filename", StringArgumentType.string())
									.executes(context -> {
										String filename = StringArgumentType.getString(context, "filename");
										ServerCommandSource source = context.getSource();
										handleModelCommand(filename, 1.0f, 1.0f, "#FFFFFF", false, source);
										return 1;
									})
									.then(CommandManager.argument("scale", FloatArgumentType.floatArg(0.1f, 100.0f))
											.executes(context -> {
												String filename = StringArgumentType.getString(context, "filename");
												float scale = FloatArgumentType.getFloat(context, "scale");
												ServerCommandSource source = context.getSource();
												handleModelCommand(filename, scale, 1.0f, "#FFFFFF", false, source);
												return 1;
											})
											.then(CommandManager.argument("particleSize", FloatArgumentType.floatArg(0.1f, 10.0f))
													.executes(context -> {
														String filename = StringArgumentType.getString(context, "filename");
														float scale = FloatArgumentType.getFloat(context, "scale");
														float particleSize = FloatArgumentType.getFloat(context, "particleSize");
														ServerCommandSource source = context.getSource();
														handleModelCommand(filename, scale, particleSize, "#FFFFFF", false, source);
														return 1;
													})
													.then(CommandManager.argument("baseColor", StringArgumentType.string())
															.executes(context -> {
																String filename = StringArgumentType.getString(context, "filename");
																float scale = FloatArgumentType.getFloat(context, "scale");
																float particleSize = FloatArgumentType.getFloat(context, "particleSize");
																String baseColor = StringArgumentType.getString(context, "baseColor");
																ServerCommandSource source = context.getSource();
																handleModelCommand(filename, scale, particleSize, baseColor, false, source);
																return 1;
															})
															.then(CommandManager.argument("showDepth", BoolArgumentType.bool())
																	.executes(context -> {
																		String filename = StringArgumentType.getString(context, "filename");
																		float scale = FloatArgumentType.getFloat(context, "scale");
																		float particleSize = FloatArgumentType.getFloat(context, "particleSize");
																		String baseColor = StringArgumentType.getString(context, "baseColor");
																		boolean showDepth = BoolArgumentType.getBool(context, "showDepth");
																		ServerCommandSource source = context.getSource();
																		handleModelCommand(filename, scale, particleSize, baseColor, showDepth, source);
																		return 1;
																	})
															)
													)
											)
									)
							)
					)
			);
		});
	}

	private void handleModelCommand(String filename, float scale, float particleSize, String baseColor, boolean showDepth, ServerCommandSource source) {
		File configDir = new File("config/dexmodel");
		File modelFile = new File(configDir, filename + ".ply");

		if (modelFile.exists()) {
			try {
				// Read vertices and faces from the .ply file
				List<Vec3d> vertices = PlyFileReader.readVertices(modelFile);
				List<int[]> faces = PlyFileReader.readFaces(modelFile);

				// Get the command position
				Vec3d commandPosition = source.getPosition();

				// Convert base color from hex string to normalized RGB values
				float[] baseRGB = hexToNormalizedRGB(baseColor);

				// Spawn particles at the vertices
				spawnParticles(source.getWorld(), vertices, faces, commandPosition, scale, particleSize, baseRGB, showDepth);
			} catch (IOException e) {
				source.sendError(Text.literal("Couldn't load model due to wrong file formatting (expected ASCII)"));
				LOGGER.error("Error reading .ply file: ", e);
			} catch (Exception e) {
				source.sendError(Text.literal("An unexpected error occurred while processing the model."));
				LOGGER.error("Unexpected error: ", e);
			}
		} else {
			source.sendError(Text.literal("File not found: " + modelFile.getAbsolutePath()));
			listModelFiles(source);
		}
	}

	private void listModelFiles(ServerCommandSource source) {
		List<String> modelFiles = getModelFilenames();
		if (modelFiles.isEmpty()) {
			source.sendError(Text.literal("No models found in the config folder."));
		} else {
			StringBuilder modelList = new StringBuilder("Available models:");
			for (String model : modelFiles) {
				modelList.append("\n- ").append(model);
			}
			source.sendFeedback(() -> Text.literal(modelList.toString()), false);
		}
	}

	private List<String> getModelFilenames() {
		File configDir = new File("config/dexmodel");
		List<String> filenames = new ArrayList<>();
		if (configDir.exists() && configDir.isDirectory()) {
			File[] files = configDir.listFiles((dir, name) -> name.endsWith(".ply"));
			if (files != null) {
				for (File file : files) {
					filenames.add(file.getName().replace(".ply", ""));
				}
			}
		}
		return filenames;
	}

	private void spawnParticles(ServerWorld world, List<Vec3d> vertices, List<int[]> faces, Vec3d commandPosition, float scale, float particleSize, float[] baseRGB, boolean showDepth) {
		double minY = vertices.stream().mapToDouble(vertex -> vertex.y * scale).min().orElse(0);
		double maxY = vertices.stream().mapToDouble(vertex -> vertex.y * scale).max().orElse(1);

		// Spawn vertex particles
		vertices.forEach(vertex -> {
			double scaledX = vertex.x * scale;
			double scaledY = vertex.y * scale;
			double scaledZ = vertex.z * scale;

			double x = scaledX + commandPosition.x;
			double y = scaledY + commandPosition.y;
			double z = scaledZ + commandPosition.z;

			float[] color = getColorForDepth(baseRGB, scaledY, minY, maxY, showDepth);

			DustParticleEffect particleEffect = new DustParticleEffect(new Vec3d(color[0], color[1], color[2]).toVector3f(), particleSize);
			world.spawnParticles(particleEffect, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
		});

		// Spawn edge particles
		faces.forEach(face -> {
			for (int i = 0; i < face.length; i++) {
				int vertex1 = face[i];
				int vertex2 = face[(i + 1) % face.length];

				Vec3d start = vertices.get(vertex1);
				Vec3d end = vertices.get(vertex2);

				double scaledStartX = start.x * scale;
				double scaledStartY = start.y * scale;
				double scaledStartZ = start.z * scale;

				double scaledEndX = end.x * scale;
				double scaledEndY = end.y * scale;
				double scaledEndZ = end.z * scale;

				double x1 = scaledStartX + commandPosition.x;
				double y1 = scaledStartY + commandPosition.y;
				double z1 = scaledStartZ + commandPosition.z;

				double x2 = scaledEndX + commandPosition.x;
				double y2 = scaledEndY + commandPosition.y;
				double z2 = scaledEndZ + commandPosition.z;

				// Calculate color for edges based on depth
				float[] edgeColor = getColorForDepth(baseRGB, (scaledStartY + scaledEndY) / 2, minY, maxY, showDepth);

				DustParticleEffect particleEffect = new DustParticleEffect(new Vec3d(edgeColor[0], edgeColor[1], edgeColor[2]).toVector3f(), particleSize / 2);
				// Assuming we want to spawn particles along the edge
				double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
				int steps = (int) Math.ceil(distance / (particleSize / 2));

				for (int j = 0; j <= steps; j++) {
					double ratio = (double) j / steps;
					double px = x1 + ratio * (x2 - x1);
					double py = y1 + ratio * (y2 - y1);
					double pz = z1 + ratio * (z2 - z1);
					world.spawnParticles(particleEffect, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		});

		// Spawn face particles
		faces.forEach(face -> {
			for (int i = 0; i < face.length; i++) {
				int vertex1 = face[i];
				int vertex2 = face[(i + 1) % face.length];
				int vertex3 = face[(i + 2) % face.length];

				Vec3d v1 = vertices.get(vertex1);
				Vec3d v2 = vertices.get(vertex2);
				Vec3d v3 = vertices.get(vertex3);

				double scaledX1 = v1.x * scale;
				double scaledY1 = v1.y * scale;
				double scaledZ1 = v1.z * scale;

				double scaledX2 = v2.x * scale;
				double scaledY2 = v2.y * scale;
				double scaledZ2 = v2.z * scale;

				double scaledX3 = v3.x * scale;
				double scaledY3 = v3.y * scale;
				double scaledZ3 = v3.z * scale;

				double x1 = scaledX1 + commandPosition.x;
				double y1 = scaledY1 + commandPosition.y;
				double z1 = scaledZ1 + commandPosition.z;

				double x2 = scaledX2 + commandPosition.x;
				double y2 = scaledY2 + commandPosition.y;
				double z2 = scaledZ2 + commandPosition.z;

				double x3 = scaledX3 + commandPosition.x;
				double y3 = scaledY3 + commandPosition.y;
				double z3 = scaledZ3 + commandPosition.z;

				// Calculate color for faces based on depth
				float[] faceColor = getColorForDepth(baseRGB, (scaledY1 + scaledY2 + scaledY3) / 3, minY, maxY, showDepth);

				DustParticleEffect particleEffect = new DustParticleEffect(new Vec3d(faceColor[0], faceColor[1], faceColor[2]).toVector3f(), particleSize);
				// Simple approach to add particles to the center of the face
				double cx = (x1 + x2 + x3) / 3;
				double cy = (y1 + y2 + y3) / 3;
				double cz = (z1 + z2 + z3) / 3;
				world.spawnParticles(particleEffect, cx, cy, cz, 1, 0.0, 0.0, 0.0, 0.0);
			}
		});
	}

	private float[] getColorForDepth(float[] baseRGB, double y, double minY, double maxY, boolean showDepth) {
		if (showDepth) {
			// Normalize Y value between 0 and 1
			double normalizedHeight = (y - minY) / (maxY - minY);
			// Calculate depth color based on normalized height
			return new float[] {
					(float) (baseRGB[0] * normalizedHeight),
					(float) (baseRGB[1] * normalizedHeight),
					(float) (baseRGB[2] * normalizedHeight)
			};
		} else {
			// Return the base color when depth is not shown
			return baseRGB;
		}
	}

	private float[] hexToNormalizedRGB(String hex) {
		// Remove hash symbol if present
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		// Parse hex to integer
		int color = Integer.parseInt(hex, 16);
		// Extract RGB components and normalize to range [0, 1]
		return new float[] {
				((color >> 16) & 0xFF) / 255.0f,
				((color >> 8) & 0xFF) / 255.0f,
				(color & 0xFF) / 255.0f
		};
	}
}
