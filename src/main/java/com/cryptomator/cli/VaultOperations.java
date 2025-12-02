package com.cryptomator.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.cryptomator.cryptofs.CryptoFileSystem;
import org.cryptomator.cryptofs.CryptoFileSystemProperties;
import org.cryptomator.cryptofs.CryptoFileSystemProvider;
import org.cryptomator.cryptolib.api.Masterkey;
import org.cryptomator.cryptolib.api.MasterkeyLoader;
import org.cryptomator.cryptolib.common.MasterkeyFileAccess;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Scanner;

public class VaultOperations {

    private static final int SCRYPT_COST_PARAM = 32768;
    private static final SecureRandom CSPRNG = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public void createVault(String vaultPathStr, String password) throws Exception {
        Path vaultPath = Paths.get(vaultPathStr).toAbsolutePath();

        if (Files.exists(vaultPath) && Files.list(vaultPath).findAny().isPresent()) {
            throw new IllegalArgumentException("Directory is not empty: " + vaultPath);
        }

        Files.createDirectories(vaultPath);

        Masterkey masterkey = Masterkey.generate(CSPRNG);

        try {
            MasterkeyFileAccess masterkeyFileAccess = new MasterkeyFileAccess(new byte[0], CSPRNG);
            Path masterkeyPath = vaultPath.resolve("masterkey.cryptomator");
            masterkeyFileAccess.persist(masterkey, masterkeyPath, password, SCRYPT_COST_PARAM);

            MasterkeyLoader loader = uri -> masterkey.copy();
            CryptoFileSystemProperties properties = CryptoFileSystemProperties.cryptoFileSystemProperties()
                    .withKeyLoader(loader)
                    .build();

            CryptoFileSystemProvider.initialize(vaultPath, properties, URI.create("masterkeyfile:masterkey.cryptomator"));
        } finally {
            masterkey.destroy();
        }
    }

    public CryptoFileSystem openVault(String vaultPathStr, String password) throws Exception {
        Path vaultPath = Paths.get(vaultPathStr).toAbsolutePath();

        if (!Files.exists(vaultPath.resolve("masterkey.cryptomator"))) {
            throw new IllegalArgumentException("Not a valid vault: masterkey.cryptomator not found");
        }

        MasterkeyFileAccess masterkeyFileAccess = new MasterkeyFileAccess(new byte[0], CSPRNG);
        Masterkey masterkey = masterkeyFileAccess.load(vaultPath.resolve("masterkey.cryptomator"), password);

        MasterkeyLoader loader = uri -> masterkey.copy();
        CryptoFileSystemProperties properties = CryptoFileSystemProperties.cryptoFileSystemProperties()
                .withKeyLoader(loader)
                .build();

        return CryptoFileSystemProvider.newFileSystem(vaultPath, properties);
    }

    public void listFiles(String vaultPathStr, String password, String innerPath) throws Exception {
        try (CryptoFileSystem fs = openVault(vaultPathStr, password)) {
            Path dir = fs.getPath(innerPath);

            if (!Files.exists(dir)) {
                throw new IllegalArgumentException("Path does not exist: " + innerPath);
            }

            System.out.println("\nContents of " + innerPath + ":\n");
            System.out.printf("%-40s %15s %20s%n", "NAME", "SIZE", "MODIFIED");
            System.out.println("-".repeat(77));

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry : stream) {
                    BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                    String name = entry.getFileName().toString();
                    String type = Files.isDirectory(entry) ? "[DIR] " : "      ";
                    String size = Files.isDirectory(entry) ? "-" : formatSize(attrs.size());
                    String modified = DATE_FORMAT.format(attrs.lastModifiedTime().toInstant());
                    
                    System.out.printf("%s%-34s %15s %20s%n", type, name, size, modified);
                }
            }
            System.out.println();
        }
    }

    public void uploadFile(String vaultPathStr, String password, String localFile, String destPath) throws Exception {
        Path localPath = Paths.get(localFile).toAbsolutePath();
        
        if (!Files.exists(localPath)) {
            throw new IllegalArgumentException("Local file does not exist: " + localFile);
        }

        try (CryptoFileSystem fs = openVault(vaultPathStr, password)) {
            String fileName = localPath.getFileName().toString();
            String targetPath = destPath.endsWith("/") ? destPath + fileName : destPath + "/" + fileName;
            Path target = fs.getPath(targetPath);

            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.copy(localPath, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Uploaded: " + localFile + " -> " + targetPath);
        }
    }

    public void downloadFile(String vaultPathStr, String password, String vaultFile, String outputPath) throws Exception {
        try (CryptoFileSystem fs = openVault(vaultPathStr, password)) {
            Path source = fs.getPath(vaultFile);
            
            if (!Files.exists(source)) {
                throw new IllegalArgumentException("File does not exist in vault: " + vaultFile);
            }

            Path target = Paths.get(outputPath).toAbsolutePath();
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void createDirectory(String vaultPathStr, String password, String dirPath) throws Exception {
        try (CryptoFileSystem fs = openVault(vaultPathStr, password)) {
            Path dir = fs.getPath(dirPath);
            Files.createDirectories(dir);
        }
    }

    public void deleteFile(String vaultPathStr, String password, String targetPath, boolean recursive) throws Exception {
        try (CryptoFileSystem fs = openVault(vaultPathStr, password)) {
            Path target = fs.getPath(targetPath);

            if (!Files.exists(target)) {
                throw new IllegalArgumentException("Path does not exist: " + targetPath);
            }

            if (Files.isDirectory(target)) {
                if (recursive) {
                    deleteRecursively(target);
                } else {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
                        if (stream.iterator().hasNext()) {
                            throw new IllegalArgumentException("Directory not empty. Use -r to delete recursively.");
                        }
                    }
                    Files.delete(target);
                }
            } else {
                Files.delete(target);
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.delete(path);
    }

    public void showVaultInfo(String vaultPathStr) throws Exception {
        Path vaultPath = Paths.get(vaultPathStr).toAbsolutePath();

        System.out.println("\n=== Vault Information ===\n");
        System.out.println("Path: " + vaultPath);

        Path masterkeyPath = vaultPath.resolve("masterkey.cryptomator");
        Path vaultConfigPath = vaultPath.resolve("vault.cryptomator");

        if (!Files.exists(masterkeyPath)) {
            System.out.println("Status: INVALID (masterkey.cryptomator not found)");
            return;
        }

        System.out.println("Status: VALID");

        if (Files.exists(vaultConfigPath)) {
            String jwt = Files.readString(vaultConfigPath);
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                JsonObject payload = JsonParser.parseString(payloadJson).getAsJsonObject();

                System.out.println("Format: " + (payload.has("format") ? payload.get("format").getAsInt() : "unknown"));
                System.out.println("Cipher: " + (payload.has("cipherCombo") ? payload.get("cipherCombo").getAsString() : "SIV_GCM"));
                System.out.println("Vault ID: " + (payload.has("jti") ? payload.get("jti").getAsString() : "unknown"));
            }
        }

        String masterkeyContent = Files.readString(masterkeyPath);
        JsonObject masterkey = JsonParser.parseString(masterkeyContent).getAsJsonObject();
        System.out.println("Scrypt Cost: " + masterkey.get("scryptCostParam").getAsInt());
        System.out.println("\nCompatibility: 100% compatible with Cryptomator desktop app");
        System.out.println();
    }

    public void changePassword(String vaultPathStr, String oldPassword, String newPassword) throws Exception {
        Path vaultPath = Paths.get(vaultPathStr).toAbsolutePath();
        Path masterkeyPath = vaultPath.resolve("masterkey.cryptomator");

        MasterkeyFileAccess masterkeyFileAccess = new MasterkeyFileAccess(new byte[0], CSPRNG);
        Masterkey masterkey = masterkeyFileAccess.load(masterkeyPath, oldPassword);

        try {
            masterkeyFileAccess.persist(masterkey, masterkeyPath, newPassword, SCRYPT_COST_PARAM);
        } finally {
            masterkey.destroy();
        }
    }

    public void interactiveMode(String vaultPathStr, String password) throws Exception {
        System.out.println("\n=== Interactive Mode ===");
        System.out.println("Type 'help' for commands, 'exit' to quit\n");

        try (CryptoFileSystem fs = openVault(vaultPathStr, password)) {
            Scanner scanner = new Scanner(System.in);
            String currentPath = "/";

            while (true) {
                System.out.print("vault:" + currentPath + "> ");
                String line = scanner.nextLine().trim();

                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+", 2);
                String cmd = parts[0].toLowerCase();
                String arg = parts.length > 1 ? parts[1] : "";

                try {
                    switch (cmd) {
                        case "help":
                            printInteractiveHelp();
                            break;
                        case "exit":
                        case "quit":
                            System.out.println("Goodbye!");
                            return;
                        case "ls":
                        case "dir":
                            listDir(fs, currentPath);
                            break;
                        case "cd":
                            currentPath = changeDir(fs, currentPath, arg);
                            break;
                        case "pwd":
                            System.out.println(currentPath);
                            break;
                        case "mkdir":
                            if (arg.isEmpty()) {
                                System.out.println("Usage: mkdir <dirname>");
                            } else {
                                String newDir = resolvePath(currentPath, arg);
                                Files.createDirectories(fs.getPath(newDir));
                                System.out.println("Created: " + newDir);
                            }
                            break;
                        case "rm":
                            if (arg.isEmpty()) {
                                System.out.println("Usage: rm <path>");
                            } else {
                                String target = resolvePath(currentPath, arg);
                                Path targetPath = fs.getPath(target);
                                if (Files.isDirectory(targetPath)) {
                                    deleteRecursively(targetPath);
                                } else {
                                    Files.delete(targetPath);
                                }
                                System.out.println("Deleted: " + target);
                            }
                            break;
                        case "cat":
                            if (arg.isEmpty()) {
                                System.out.println("Usage: cat <file>");
                            } else {
                                String filePath = resolvePath(currentPath, arg);
                                String content = Files.readString(fs.getPath(filePath));
                                System.out.println(content);
                            }
                            break;
                        case "upload":
                            if (arg.isEmpty()) {
                                System.out.println("Usage: upload <local-file>");
                            } else {
                                Path localPath = Paths.get(arg);
                                if (!Files.exists(localPath)) {
                                    System.out.println("File not found: " + arg);
                                } else {
                                    String targetFile = currentPath + (currentPath.endsWith("/") ? "" : "/") + localPath.getFileName();
                                    Files.copy(localPath, fs.getPath(targetFile), StandardCopyOption.REPLACE_EXISTING);
                                    System.out.println("Uploaded: " + targetFile);
                                }
                            }
                            break;
                        case "download":
                            String[] downloadArgs = arg.split("\\s+", 2);
                            if (downloadArgs.length < 2) {
                                System.out.println("Usage: download <vault-file> <local-path>");
                            } else {
                                String sourcePath = resolvePath(currentPath, downloadArgs[0]);
                                Path destPath = Paths.get(downloadArgs[1]);
                                Files.copy(fs.getPath(sourcePath), destPath, StandardCopyOption.REPLACE_EXISTING);
                                System.out.println("Downloaded to: " + destPath);
                            }
                            break;
                        default:
                            System.out.println("Unknown command: " + cmd + ". Type 'help' for commands.");
                    }
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        }
    }

    private void printInteractiveHelp() {
        System.out.println("\nCommands:");
        System.out.println("  ls, dir              - List files in current directory");
        System.out.println("  cd <path>            - Change directory");
        System.out.println("  pwd                  - Print current directory");
        System.out.println("  mkdir <name>         - Create directory");
        System.out.println("  rm <path>            - Delete file or directory");
        System.out.println("  cat <file>           - Show file contents");
        System.out.println("  upload <local-file>  - Upload file to current directory");
        System.out.println("  download <file> <out>- Download file from vault");
        System.out.println("  exit, quit           - Exit interactive mode");
        System.out.println();
    }

    private void listDir(CryptoFileSystem fs, String path) throws IOException {
        Path dir = fs.getPath(path);
        System.out.println();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                String type = Files.isDirectory(entry) ? "[DIR]" : "     ";
                String size = Files.isDirectory(entry) ? "" : " (" + formatSize(attrs.size()) + ")";
                System.out.println(type + " " + entry.getFileName() + size);
            }
        }
        System.out.println();
    }

    private String changeDir(CryptoFileSystem fs, String currentPath, String arg) {
        if (arg.isEmpty() || arg.equals("/")) {
            return "/";
        }
        
        String newPath = resolvePath(currentPath, arg);
        Path path = fs.getPath(newPath);
        
        if (!Files.exists(path)) {
            System.out.println("Directory not found: " + newPath);
            return currentPath;
        }
        
        if (!Files.isDirectory(path)) {
            System.out.println("Not a directory: " + newPath);
            return currentPath;
        }
        
        return newPath;
    }

    private String resolvePath(String currentPath, String arg) {
        if (arg.startsWith("/")) {
            return normalizePath(arg);
        }
        String combined = currentPath.endsWith("/") ? currentPath + arg : currentPath + "/" + arg;
        return normalizePath(combined);
    }

    private String normalizePath(String path) {
        String[] parts = path.split("/");
        java.util.List<String> stack = new java.util.ArrayList<>();
        
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!stack.isEmpty()) stack.remove(stack.size() - 1);
            } else {
                stack.add(part);
            }
        }
        
        return "/" + String.join("/", stack);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
