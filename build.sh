#!/bin/bash

echo "Building Cryptomator CLI..."
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed."
    echo "Please install Maven 3.6+ and try again."
    exit 1
fi

# Check Java version
java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$java_version" -lt 17 ]; then
    echo "Error: Java 17 or higher is required."
    echo "Current version: $java_version"
    exit 1
fi

# Build
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "Build successful!"
    echo ""
    echo "JAR file: target/cryptomator-cli-1.0.0.jar"
    echo ""
    echo "Usage examples:"
    echo "  java -jar target/cryptomator-cli-1.0.0.jar --help"
    echo "  java -jar target/cryptomator-cli-1.0.0.jar create ./my-vault"
    echo "  java -jar target/cryptomator-cli-1.0.0.jar unlock ./my-vault"
else
    echo ""
    echo "Build failed!"
    exit 1
fi
