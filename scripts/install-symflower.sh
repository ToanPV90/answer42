#!/bin/bash
# Install Symflower CLI for Java test generation

echo "Installing Symflower CLI for Java..."

# Create directory for Symflower
SYMFLOWER_DIR="$HOME/.local/bin"
mkdir -p "$SYMFLOWER_DIR"

# Detect system architecture
ARCH=$(uname -m)
OS=$(uname -s | tr '[:upper:]' '[:lower:]')

# Map architecture names
case "$ARCH" in
    x86_64)
        ARCH="amd64"
        ;;
    aarch64|arm64)
        ARCH="arm64"
        ;;
    *)
        echo "Unsupported architecture: $ARCH"
        exit 1
        ;;
esac

# Set download URL based on OS and architecture
if [ "$OS" = "linux" ]; then
    DOWNLOAD_URL="https://download.symflower.com/cli/latest/linux_${ARCH}/symflower"
elif [ "$OS" = "darwin" ]; then
    DOWNLOAD_URL="https://download.symflower.com/cli/latest/darwin_${ARCH}/symflower"
else
    echo "Unsupported operating system: $OS"
    exit 1
fi

echo "Downloading Symflower from: $DOWNLOAD_URL"

# Download Symflower
cd "$SYMFLOWER_DIR"
curl -L -o symflower "$DOWNLOAD_URL"

# Make it executable
chmod +x symflower

# Add to PATH if not already there
if ! grep -q "$SYMFLOWER_DIR" ~/.bashrc; then
    echo "export PATH=\"\$PATH:$SYMFLOWER_DIR\"" >> ~/.bashrc
fi

# Also add to current session
export PATH="$PATH:$SYMFLOWER_DIR"

# Verify installation
if "$SYMFLOWER_DIR/symflower" version >/dev/null 2>&1; then
    echo "Symflower installed successfully!"
    "$SYMFLOWER_DIR/symflower" version
    echo ""
    echo "Symflower has been installed to: $SYMFLOWER_DIR/symflower"
    echo "It has been added to your PATH."
    echo ""
    echo "To use Symflower in new terminals, run: source ~/.bashrc"
    echo ""
    echo "Example usage:"
    echo "  symflower test --language=java"
    echo "  ./mvnw exec:exec -Dexec.executable=\"symflower\" -Dexec.args=\"test --language=java\""
else
    echo "Failed to install Symflower"
    exit 1
fi
