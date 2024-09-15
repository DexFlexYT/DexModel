package org.dexflex.dexmodel;

import net.minecraft.util.math.Vec3d;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class PlyFileReader {

    // Reads vertices from a PLY file
    public static List<Vec3d> readVertices(File plyFile) throws IOException {
        List<Vec3d> vertices = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(plyFile))) {
            String line;
            boolean readingVertices = false;
            int vertexCount = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("element vertex")) {
                    vertexCount = Integer.parseInt(line.split(" ")[2]);
                } else if (line.equals("end_header")) {
                    readingVertices = true;
                } else if (readingVertices && vertexCount > 0) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        try {
                            double x = Double.parseDouble(parts[0]);
                            double y = Double.parseDouble(parts[1]);
                            double z = Double.parseDouble(parts[2]);
                            vertices.add(new Vec3d(x, y, z));
                            vertexCount--;
                        } catch (NumberFormatException e) {
                            System.err.println("Error parsing vertex data: " + e.getMessage());
                        }
                    }
                }
            }
        }
        return vertices;
    }

    // Reads faces from a PLY file
    public static List<int[]> readFaces(File file) throws IOException {
        List<int[]> faces = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        boolean readingFaces = false;

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("end_header")) {
                readingFaces = true;
                continue;
            }
            if (readingFaces) {
                String[] tokens = line.split("\\s+");
                try {
                    // Check if the line starts with a number (indicating the number of vertices in the face)
                    int numVertices = Integer.parseInt(tokens[0]);
                    int[] face = new int[numVertices];
                    for (int i = 0; i < numVertices; i++) {
                        // Ensure each vertex index is an integer
                        face[i] = Integer.parseInt(tokens[i + 1]);
                    }
                    faces.add(face);
                } catch (NumberFormatException e) {
                    // Handle non-integer values
                }
            }
        }

        reader.close();
        return faces;
    }
}
