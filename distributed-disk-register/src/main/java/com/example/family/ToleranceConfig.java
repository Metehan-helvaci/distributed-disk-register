package com.example.family;

import java.nio.file.Files;
import java.nio.file.Paths;


public class ToleranceConfig {
    public static void main(String[] args)  {
        loadTolerance();
    }
    public static int loadTolerance() {
        try {
            String content = Files.readString(Paths.get("distributed-disk-register/src/main/tolerance.conf")).trim();
            int tolerance = Integer.parseInt(content.split("=")[1]);
            System.out.println(tolerance);

            return tolerance;

        } catch (Exception e) {
            return 1;
        }

    }
}
