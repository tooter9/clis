# Cryptomator CLI

Command-line tool for working with Cryptomator vaults. **100% compatible with Cryptomator desktop application**.

## Features

- Create new Cryptomator vaults (Format 8, SIV_GCM)
- Unlock and browse encrypted vaults
- Upload/download files to/from vaults
- Create directories
- Delete files and directories
- Interactive shell mode
- Change vault password
- View vault information

## Requirements

- Java 17 or higher
- Maven 3.6+ (for building)

## Installation

### Option 1: Build from source

```bash
# Clone this repository
git clone https://github.com/YOUR_USERNAME/cryptomator-cli.git
cd cryptomator-cli

# Build the JAR
mvn clean package

# The executable JAR will be in target/cryptomator-cli-1.0.0.jar
```

### Option 2: Download release

Download the latest `cryptomator-cli-1.0.0.jar` from the Releases page.

## Usage

### Basic Commands

```bash
# Create a new vault
java -jar cryptomator-cli-1.0.0.jar create /path/to/my-vault

# Show vault information
java -jar cryptomator-cli-1.0.0.jar info /path/to/my-vault

# List files in vault root
java -jar cryptomator-cli-1.0.0.jar list /path/to/my-vault

# List files in a subdirectory
java -jar cryptomator-cli-1.0.0.jar list /path/to/my-vault -p /documents

# Upload a file
java -jar cryptomator-cli-1.0.0.jar upload /path/to/my-vault ./myfile.txt -d /documents

# Download a file
java -jar cryptomator-cli-1.0.0.jar download /path/to/my-vault /documents/myfile.txt -o ./downloaded.txt

# Create a directory
java -jar cryptomator-cli-1.0.0.jar mkdir /path/to/my-vault /new-folder

# Delete a file
java -jar cryptomator-cli-1.0.0.jar delete /path/to/my-vault /documents/myfile.txt

# Delete a directory recursively
java -jar cryptomator-cli-1.0.0.jar delete /path/to/my-vault /old-folder -r

# Change vault password
java -jar cryptomator-cli-1.0.0.jar change-password /path/to/my-vault
```

### Interactive Mode

For more convenient operation, use interactive mode:

```bash
java -jar cryptomator-cli-1.0.0.jar unlock /path/to/my-vault
```

This opens an interactive shell with commands:
- `ls` / `dir` - List files
- `cd <path>` - Change directory
- `pwd` - Print current directory
- `mkdir <name>` - Create directory
- `rm <path>` - Delete file or directory
- `cat <file>` - Show file contents
- `upload <local-file>` - Upload file to current directory
- `download <file> <output>` - Download file from vault
- `exit` / `quit` - Exit interactive mode

### Alias for convenience

Add to your `.bashrc` or `.zshrc`:

```bash
alias cryptomator='java -jar /path/to/cryptomator-cli-1.0.0.jar'
```

Then use:
```bash
cryptomator create ./my-vault
cryptomator list ./my-vault
cryptomator unlock ./my-vault
```

## Compatibility

This tool uses official Cryptomator libraries and creates vaults that are **100% compatible** with:

- Cryptomator Desktop (Windows, macOS, Linux)
- Cryptomator for iOS
- Cryptomator for Android

### Vault Format

- **Format**: 8 (latest)
- **Cipher**: SIV_GCM (AES-256-SIV + AES-256-GCM)
- **Key Derivation**: scrypt (N=32768, r=8, p=1)

## Security

- All encryption/decryption happens locally
- Passwords are never stored
- Uses official Cryptomator cryptographic libraries:
  - `org.cryptomator:cryptolib`
  - `org.cryptomator:cryptofs`
  - `org.cryptomator:siv-mode`

## License

MIT License

## Dependencies

- [Cryptomator CryptoLib](https://github.com/cryptomator/cryptolib) - Core cryptographic operations
- [Cryptomator CryptoFS](https://github.com/cryptomator/cryptofs) - Virtual encrypted filesystem
- [picocli](https://picocli.info/) - Command line interface
- [Bouncy Castle](https://www.bouncycastle.org/) - Cryptographic provider
- [Gson](https://github.com/google/gson) - JSON parsing
