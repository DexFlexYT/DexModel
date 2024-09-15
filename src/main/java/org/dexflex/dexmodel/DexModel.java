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
										handleModelCommand(filename, 1.0f, 1.0f, "#FFFFFF", false, "wireframe", source);
										return 1;
									})
									.then(CommandManager.argument("scale", FloatArgumentType.floatArg(0.1f, 100.0f))
											.executes(context -> {
												String filename = StringArgumentType.getString(context, "filename");
												float scale = FloatArgumentType.getFloat(context, "scale");
												ServerCommandSource source = context.getSource();
												handleModelCommand(filename, scale, 1.0f, "#FFFFFF", false, "wireframe", source);
												return 1;
											})
											.then(CommandManager.argument("particleSize", FloatArgumentType.floatArg(0.1f, 10.0f))
													.executes(context -> {
														String filename = StringArgumentType.getString(context, "filename");
														float scale = FloatArgumentType.getFloat(context, "scale");
														float particleSize = FloatArgumentType.getFloat(context, "particleSize");
														ServerCommandSource source = context.getSource();
														handleModelCommand(filename, scale, particleSize, "#FFFFFF", false, "wireframe", source);
														return 1;
													})
													.then(CommandManager.argument("baseColor", StringArgumentType.string())
															.executes(context -> {
																String filename = StringArgumentType.getString(context, "filename");
																float scale = FloatArgumentType.getFloat(context, "scale");
																float particleSize = FloatArgumentType.getFloat(context, "particleSize");
																String baseColor = StringArgumentType.getString(context, "baseColor");
																ServerCommandSource source = context.getSource();
																handleModelCommand(filename, scale, particleSize, baseColor, false, "wireframe", source);
																return 1;
															})
															.then(CommandManager.argument("showDepth", BoolArgumentType.bool())
																	.then(CommandManager.argument("displayType", StringArgumentType.string())
																			.executes(context -> {
																				String filename = StringArgumentType.getString(context, "filename");
																				float scale = FloatArgumentType.getFloat(context, "scale");
																				float particleSize = FloatArgumentType.getFloat(context, "particleSize");
																				String baseColor = StringArgumentType.getString(context, "baseColor");
																				boolean showDepth = BoolArgumentType.getBool(context, "showDepth");
																				String displayType = StringArgumentType.getString(context, "displayType");
																				ServerCommandSource source = context.getSource();
																				handleModelCommand(filename, scale, particleSize, baseColor, showDepth, displayType, source);
																				return 1;
																			})
																	)
															)
													)
											)
									)
							)
					)
			);
		});
	}

	private void handleModelCommand(String filename, float scale, float particleSize, String baseColor, boolean showDepth, String displayType, ServerCommandSource source) {
		File configDir = new File("config/dexmodel");
		File modelFile = new File(configDir, filename + ".ply");

		if (modelFile.exists()) {
			try {
				List<Vec3d> vertices = PlyFileReader.readVertices(modelFile);
				List<int[]> faces = PlyFileReader.readFaces(modelFile);
				Vec3d commandPosition = source.getPosition();
				float[] baseRGB = hexToNormalizedRGB(baseColor);
				spawnParticles(source.getWorld(), vertices, faces, commandPosition, scale, particleSize, baseRGB, showDepth, displayType);
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

	private void spawnParticles(ServerWorld world, List<Vec3d> vertices, List<int[]> faces, Vec3d commandPosition, float scale, float particleSize, float[] baseRGB, boolean showDepth, String displayType) {
		double minY = vertices.stream().mapToDouble(vertex -> vertex.y * scale).min().orElse(0);
		double maxY = vertices.stream().mapToDouble(vertex -> vertex.y * scale).max().orElse(1);

		if ("points".equals(displayType)) {
			// Spawn vertex particles
			vertices.forEach(vertex -> {
				double[] coords = getScaledCoords(vertex, scale, commandPosition);
				float[] color = getColorForDepth(baseRGB, coords[1], minY, maxY, showDepth);
				DustParticleEffect particleEffect = new DustParticleEffect(new Vec3d(color[0], color[1], color[2]).toVector3f(), particleSize);
				world.spawnParticles(particleEffect, coords[0], coords[1], coords[2], 1, 0.0, 0.0, 0.0, 0.0);
			});
		} else if ("wireframe".equals(displayType)) {
			// Spawn edge particles
			faces.forEach(face -> {
				for (int i = 0; i < face.length; i++) {
					int vertex1 = face[i];
					int vertex2 = face[(i + 1) % face.length];
					Vec3d start = vertices.get(vertex1);
					Vec3d end = vertices.get(vertex2);
					double[] startCoords = getScaledCoords(start, scale, commandPosition);
					double[] endCoords = getScaledCoords(end, scale, commandPosition);
					float[] edgeColor = getColorForDepth(baseRGB, (startCoords[1] + endCoords[1]) / 2, minY, maxY, showDepth);
					DustParticleEffect particleEffect = new DustParticleEffect(new Vec3d(edgeColor[0], edgeColor[1], edgeColor[2]).toVector3f(), particleSize / 2);
					double distance = Math.sqrt(Math.pow(endCoords[0] - startCoords[0], 2) + Math.pow(endCoords[1] - startCoords[1], 2) + Math.pow(endCoords[2] - startCoords[2], 2));
					int steps = (int) Math.ceil(distance / (particleSize / 2));
					for (int j = 0; j <= steps; j++) {
						double ratio = (double) j / steps;
						double px = startCoords[0] + ratio * (endCoords[0] - startCoords[0]);
						double py = startCoords[1] + ratio * (endCoords[1] - startCoords[1]);
						double pz = startCoords[2] + ratio * (endCoords[2] - startCoords[2]);
						world.spawnParticles(particleEffect, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
					}
				}
			});
		} else if ("solid".equals(displayType)) {
			// Spawn face particles
			faces.forEach(face -> {
				for (int i = 0; i < face.length; i++) {
					int vertex1 = face[i];
					int vertex2 = face[(i + 1) % face.length];
					int vertex3 = face[(i + 2) % face.length];
					Vec3d v1 = vertices.get(vertex1);
					Vec3d v2 = vertices.get(vertex2);
					Vec3d v3 = vertices.get(vertex3);
					double[] v1Coords = getScaledCoords(v1, scale, commandPosition);
					double[] v2Coords = getScaledCoords(v2, scale, commandPosition);
					double[] v3Coords = getScaledCoords(v3, scale, commandPosition);
					// Simple triangulation and particle effect for faces
					float[] faceColor = getColorForDepth(baseRGB, (v1Coords[1] + v2Coords[1] + v3Coords[1]) / 3, minY, maxY, showDepth);
					DustParticleEffect particleEffect = new DustParticleEffect(new Vec3d(faceColor[0], faceColor[1], faceColor[2]).toVector3f(), particleSize);
					int steps = 20; // Example value for density
					for (int j = 0; j < steps; j++) {
						double t1 = Math.random();
						double t2 = Math.random();
						if (t1 + t2 > 1) {
							t1 = 1 - t1;
							t2 = 1 - t2;
						}
						double px = v1Coords[0] + t1 * (v2Coords[0] - v1Coords[0]) + t2 * (v3Coords[0] - v1Coords[0]);
						double py = v1Coords[1] + t1 * (v2Coords[1] - v1Coords[1]) + t2 * (v3Coords[1] - v1Coords[1]);
						double pz = v1Coords[2] + t1 * (v2Coords[2] - v1Coords[2]) + t2 * (v3Coords[2] - v1Coords[2]);
						world.spawnParticles(particleEffect, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
					}
				}
			});
		}
	}

	private double[] getScaledCoords(Vec3d vec, float scale, Vec3d offset) {
		return new double[]{
				(vec.x * scale) + offset.x,
				(vec.y * scale) + offset.y,
				(vec.z * scale) + offset.z
		};
	}

	private float[] hexToNormalizedRGB(String hex) {
		hex = hex.replace("#", "");
		int r = Integer.parseInt(hex.substring(0, 2), 16);
		int g = Integer.parseInt(hex.substring(2, 4), 16);
		int b = Integer.parseInt(hex.substring(4, 6), 16);
		return new float[]{r / 255.0f, g / 255.0f, b / 255.0f};
	}

	private float[] getColorForDepth(float[] baseRGB, double y, double minY, double maxY, boolean showDepth) {
		if (showDepth) {
			float depthFactor = (float) (y - minY) / (float) (maxY - minY);
			return new float[]{
					baseRGB[0] * depthFactor,
					baseRGB[1] * depthFactor,
					baseRGB[2] * depthFactor
			};
		} else {
			return baseRGB;
		}
	}
}
