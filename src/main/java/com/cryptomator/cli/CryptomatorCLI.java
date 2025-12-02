package com.cryptomator.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.Console;
import java.util.concurrent.Callable;

@Command(
    name = "cryptomator-cli",
    mixinStandardHelpOptions = true,
    version = "Cryptomator CLI 1.0.0",
    description = "Command-line tool for Cryptomator vaults - 100% compatible with desktop Cryptomator",
    subcommands = {
        CryptomatorCLI.CreateCommand.class,
        CryptomatorCLI.ListCommand.class,
        CryptomatorCLI.UnlockCommand.class,
        CryptomatorCLI.UploadCommand.class,
        CryptomatorCLI.DownloadCommand.class,
        CryptomatorCLI.MkdirCommand.class,
        CryptomatorCLI.DeleteCommand.class,
        CryptomatorCLI.InfoCommand.class,
        CryptomatorCLI.ChangePasswordCommand.class
    }
)
public class CryptomatorCLI implements Callable<Integer> {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CryptomatorCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    static String readPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            char[] password = console.readPassword(prompt);
            return new String(password);
        } else {
            System.out.print(prompt);
            return new java.util.Scanner(System.in).nextLine();
        }
    }

    @Command(name = "create", description = "Create a new Cryptomator vault")
    static class CreateCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path where to create the vault")
        private String vaultPath;

        @Override
        public Integer call() {
            try {
                String password = readPassword("Enter password for new vault: ");
                String confirm = readPassword("Confirm password: ");
                
                if (!password.equals(confirm)) {
                    System.err.println("Error: Passwords do not match!");
                    return 1;
                }
                
                if (password.length() < 8) {
                    System.err.println("Error: Password must be at least 8 characters!");
                    return 1;
                }

                VaultOperations ops = new VaultOperations();
                ops.createVault(vaultPath, password);
                System.out.println("Vault created successfully at: " + vaultPath);
                System.out.println("Format: 8 (SIV_GCM) - Compatible with Cryptomator desktop");
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "list", aliases = {"ls"}, description = "List files in an unlocked vault")
    static class ListCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path to the vault")
        private String vaultPath;

        @Option(names = {"-p", "--path"}, description = "Path inside vault (default: /)", defaultValue = "/")
        private String innerPath;

        @Override
        public Integer call() {
            try {
                String password = readPassword("Enter vault password: ");
                
                VaultOperations ops = new VaultOperations();
                ops.listFiles(vaultPath, password, innerPath);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "unlock", description = "Unlock and mount vault (interactive mode)")
    static class UnlockCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path to the vault")
        private String vaultPath;

        @Override
        public Integer call() {
            try {
                String password = readPassword("Enter vault password: ");
                
                VaultOperations ops = new VaultOperations();
                ops.interactiveMode(vaultPath, password);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "upload", aliases = {"put"}, description = "Upload a file to the vault")
    static class UploadCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path to the vault")
        private String vaultPath;

        @Parameters(index = "1", description = "Local file to upload")
        private String localFile;

        @Option(names = {"-d", "--dest"}, description = "Destination path in vault (default: /)", defaultValue = "/")
        private String destPath;

        @Override
        public Integer call() {
            try {
                String password = readPassword("Enter vault password: ");
                
                VaultOperations ops = new VaultOperations();
                ops.uploadFile(vaultPath, password, localFile, destPath);
                System.out.println("File uploaded successfully!");
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "download", aliases = {"get"}, description = "Download a file from the vault")
    static class DownloadCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path to the vault")
        private String vaultPath;

        @Parameters(index = "1", description = "File path inside vault")
        private String vaultFile;

        @Option(names = {"-o", "--output"}, description = "Output file path", required = true)
        private String outputPath;

        @Override
        public Integer call() {
            try {
                String password = readPassword("Enter vault password: ");
                
                VaultOperations ops = new VaultOperations();
                ops.downloadFile(vaultPath, password, vaultFile, outputPath);
                System.out.println("File downloaded to: " + outputPath);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "mkdir", description = "Create a directory in the vault")
    static class MkdirCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path to the vault")
        private String vaultPath;

        @Parameters(index = "1", description = "Directory path to create inside vault")
        private String dirPath;

        @Override
        public Integer call() {
            try {
                String password = readPassword("Enter vault password: ");
                
                VaultOperations ops = new VaultOperations();
                ops.createDirectory(vaultPath, password, dirPath);
                System.out.println("Directory created: " + dirPath);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "delete", aliases = {"rm"}, description = "Delete a file or directory from the vault")
    static class DeleteCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path to the vault")
        private String vaultPath;

        @Parameters(index = "1", description = "Path to delete inside vault")
        private String targetPath;

        @Option(names = {"-r", "--recursive"}, description = "Delete directories recursively")
        private boolean recursive;

        @Override
        public Integer call() {
            try {
                String password = readPassword("Enter vault password: ");
                
                VaultOperations ops = new VaultOperations();
                ops.deleteFile(vaultPath, password, targetPath, recursive);
                System.out.println("Deleted: " + targetPath);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "info", description = "Show vault information")
    static class InfoCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path to the vault")
        private String vaultPath;

        @Override
        public Integer call() {
            try {
                VaultOperations ops = new VaultOperations();
                ops.showVaultInfo(vaultPath);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "change-password", description = "Change vault password")
    static class ChangePasswordCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path to the vault")
        private String vaultPath;

        @Override
        public Integer call() {
            try {
                String oldPassword = readPassword("Enter current password: ");
                String newPassword = readPassword("Enter new password: ");
                String confirm = readPassword("Confirm new password: ");
                
                if (!newPassword.equals(confirm)) {
                    System.err.println("Error: Passwords do not match!");
                    return 1;
                }
                
                if (newPassword.length() < 8) {
                    System.err.println("Error: Password must be at least 8 characters!");
                    return 1;
                }

                VaultOperations ops = new VaultOperations();
                ops.changePassword(vaultPath, oldPassword, newPassword);
                System.out.println("Password changed successfully!");
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }
}
