package org.dexflex.dexmodel;

import net.minecraft.util.math.Vec3d;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlyFileReader {

    public static List<Vec3d> readVertices(File file) throws IOException {
        List<Vec3d> vertices = new ArrayList<>();
        int vertexCount = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("element vertex")) {
                    vertexCount = Integer.parseInt(line.split(" ")[2]);
                } else if (line.equals("end_header")) {
                    break;
                }
            }

            int verticesRead = 0;
            while ((line = reader.readLine()) != null && verticesRead < vertexCount) {
                String[] parts = line.trim().split("\\s+");

                if (parts.length >= 3) {
                    double x = Double.parseDouble(parts[0]);
                    double y = Double.parseDouble(parts[1]);
                    double z = Double.parseDouble(parts[2]);
                    vertices.add(new Vec3d(x, y, z));
                    verticesRead++;
                }
            }
        }

        return vertices;
    }

    public static List<int[]> readFaces(File file) throws IOException {
        List<int[]> faces = new ArrayList<>();
        int vertexCount = 0;
        List<Vec3d> vertices = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("element vertex")) {
                    vertexCount = Integer.parseInt(line.split(" ")[2]);
                } else if (line.equals("end_header")) {
                    break;
                }
            }

            // Read vertices again after the header
            while ((line = reader.readLine()) != null && vertices.size() < vertexCount) {
                String[] parts = line.trim().split("\\s+");

                if (parts.length >= 3) {
                    double x = Double.parseDouble(parts[0]);
                    double y = Double.parseDouble(parts[1]);
                    double z = Double.parseDouble(parts[2]);
                    vertices.add(new Vec3d(x, y, z));
                }
            }

            // Read faces after vertices
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");

                if (parts.length > 1) {
                    int[] face = new int[parts.length - 1];
                    for (int i = 1; i < parts.length; i++) {
                        face[i - 1] = Integer.parseInt(parts[i]);
                    }
                    faces.add(face);
                }
            }
        }

        return faces;
    }
}
